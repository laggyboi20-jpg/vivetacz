/*
 * ViveTaCZ — VR support for TaCZ Refabricated via Vivecraft.
 * Copyright (C) 2026 Laggy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * This is an unofficial add-on and is not affiliated with or endorsed by
 * the TaCZ (Timeless & Classics Zero) or Vivecraft projects. It bundles no
 * TaCZ or Vivecraft code or assets; it references them at runtime only.
 */

package com.vivetacz.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vivetacz.ViveTaCZClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Live-tunable VR placement, persisted to {@code config/vivetacz.json} and
 * editable in-game via Mod Menu ({@link ViveTaczModMenu}).
 *
 * Placement resolves per item:
 *  - {@link #defaultGun}: used for any gun without an override.
 *  - {@link #perGun}: id → override (e.g. {@code tacz:minigun} needs its own).
 *  - {@link #ammo}: used for all TaCZ ammo items.
 *
 * The render Mixins read this every frame, so edits apply the instant the
 * config screen is saved.
 */
public final class ViveTaczConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ViveTaczConfig INSTANCE;

    public boolean enabled = true;

    /** Freeze the gun body's animation: no idle/run sway, and during reload the gun stays
     *  put while only its magazine/bolt/child parts animate. */
    public boolean staticGunBody = false;

    public Placement defaultGun = Placement.gunDefault();
    public Placement ammo = Placement.ammoDefault();
    public Map<String, Placement> perGun = defaultPerGun();

    private static Map<String, Placement> defaultPerGun() {
        Map<String, Placement> m = new HashMap<>();
        m.put("tacz:minigun", new Placement(-1.27f, -0.5f, -0.652f, 180.0f, -44.0f, 0.0f, 1.4f));
        return m;
    }

    /** Floating ammo/fire-mode HUD near the gun. */
    public boolean hudEnabled = true;
    public Placement hud = Placement.hudDefault();

    /** Physical off-hand reload gesture (simple: off hand → gun). */
    public boolean reloadGestureEnabled = true;
    /** How close (blocks) the off hand must come to the gun hand to start a reload. */
    public float reloadGestureDistance = 0.1f;

    /** Physical magazine reload: grab the mag sitting by the gun, seat it at the gun. */
    public boolean magReloadEnabled = true;
    /** Magazine anchor next to the gun, offset in the gun hand's frame + mag model rotation/scale. */
    public Placement pouch = Placement.pouchDefault();
    /** Held magazine (the gun's reload mag) placement in the off hand's frame. */
    public Placement magazine = Placement.magazineDefault();
    /** How close (blocks) the off hand must get to the mag to grab it. */
    public float magGrabDistance = 0.1f;
    /** Two-stage reload: press the "Drop Magazine" key first, then insert a new mag to reload. */
    public boolean twoStageReload = false;
    /** Default "insert the mag" zone relative to the gun hand (posX/Y/Z used). */
    public Placement reloadZone = Placement.reloadZoneDefault();
    /** Per-gun reload-zone overrides (mag wells differ per gun). */
    public Map<String, Placement> perGunReloadZone = new HashMap<>();
    /** Draw a marker showing the insert zone while holding a gun. */
    public boolean showReloadZone = true;

    /** Minimum seconds between reloads (grab a new mag) to stop accidental spamming. */
    public float magCooldownSeconds = 5.0f;

    /** Aim guns from the main-hand controller (bullets leave the barrel) instead of the head. */
    public boolean controllerAim = true;
    /** Degrees the barrel sits below the controller's pointing axis. Legacy single-axis knob;
     *  migrated into {@link #aimOffset}.pitch on load. Kept for old-config compatibility. */
    public float aimPitchOffset = 40.0f;
    /** Default aim offset (controller-forward → barrel): yaw/pitch/roll, pitch positive = down. */
    public Placement aimOffset = Placement.aimOffsetDefault();
    /** Per-gun aim-offset overrides (each gun's barrel sits at its own angle in the hand). */
    public Map<String, Placement> perGunAimOffset = new HashMap<>();
    /** Draw a client-side tracer from the barrel to the impact point on each shot. */
    public boolean tracerFromBarrel = true;

    /** Recoil climb strength. 0 = no recoil, 1 = default, higher = more kick. */
    public float recoilMultiplier = 1.0f;
    /** Buzz the gun-hand controller on each shot. */
    public boolean hapticFeedback = true;

    /** Verbose diagnostics to logs/vivetacz-debug.log (poses, barrel dir, bullet spawn, etc.). */
    public boolean debugLogging = false;

    // ------------------------------------------------------------------
    public static ViveTaczConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    /** Placement for a gun id, falling back to the default gun placement. */
    public Placement gunPlacement(String gunId) {
        if (gunId != null) {
            Placement p = perGun.get(gunId);
            if (p != null) return p;
        }
        return defaultGun;
    }

    /** Get (creating from the default if needed) the override for a gun id. */
    public Placement getOrCreateGunOverride(String gunId) {
        Placement p = perGun.get(gunId);
        if (p == null) {
            p = defaultGun.copy();
            perGun.put(gunId, p);
        }
        return p;
    }

    public Placement reloadZoneFor(String gunId) {
        Placement p = (gunId == null) ? null : perGunReloadZone.get(gunId);
        return p != null ? p : reloadZone;
    }

    public Placement getOrCreateReloadZone(String gunId) {
        Placement p = perGunReloadZone.get(gunId);
        if (p == null) { p = reloadZone.copy(); perGunReloadZone.put(gunId, p); }
        return p;
    }

    public Placement aimOffsetFor(String gunId) {
        Placement p = (gunId == null) ? null : perGunAimOffset.get(gunId);
        return p != null ? p : aimOffset;
    }

    public Placement getOrCreateAimOffset(String gunId) {
        Placement p = perGunAimOffset.get(gunId);
        if (p == null) { p = aimOffset.copy(); perGunAimOffset.put(gunId, p); }
        return p;
    }

    // ------------------------------------------------------------------
    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("vivetacz.json");
    }

    private static ViveTaczConfig load() {
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                ViveTaczConfig cfg = GSON.fromJson(r, ViveTaczConfig.class);
                if (cfg != null) {
                    if (cfg.defaultGun == null) cfg.defaultGun = Placement.gunDefault();
                    if (cfg.ammo == null) cfg.ammo = Placement.ammoDefault();
                    if (cfg.hud == null) cfg.hud = Placement.hudDefault();
                    if (cfg.pouch == null) cfg.pouch = Placement.pouchDefault();
                    if (cfg.magazine == null) cfg.magazine = Placement.magazineDefault();
                    if (cfg.reloadZone == null) cfg.reloadZone = Placement.reloadZoneDefault();
                    if (cfg.perGun == null) cfg.perGun = new HashMap<>();
                    if (cfg.perGunReloadZone == null) cfg.perGunReloadZone = new HashMap<>();
                    if (cfg.perGunAimOffset == null) cfg.perGunAimOffset = new HashMap<>();
                    if (cfg.aimOffset == null) {
                        // Migrate the old single pitch knob into the new full aim offset.
                        cfg.aimOffset = Placement.aimOffsetDefault();
                        cfg.aimOffset.pitch = cfg.aimPitchOffset;
                    }
                    return cfg;
                }
            } catch (Exception e) {
                ViveTaCZClient.LOGGER.warn("[ViveTaCZ] Failed to read config, using defaults", e);
            }
        }
        ViveTaczConfig cfg = new ViveTaczConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try (Writer w = Files.newBufferedWriter(path())) {
            GSON.toJson(this, w);
        } catch (IOException e) {
            ViveTaCZClient.LOGGER.warn("[ViveTaCZ] Failed to save config", e);
        }
    }
}
