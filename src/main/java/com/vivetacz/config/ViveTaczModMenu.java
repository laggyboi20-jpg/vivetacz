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

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.vivetacz.compat.TaczBridge;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Mod Menu entry point → a Cloth Config screen for live VR placement tuning.
 *
 * Tabs: General, Default Gun, Ammo, and (when you open the menu holding a gun) a
 * per-gun override tab for that specific gun — so e.g. the minigun can be placed
 * independently of every other gun.
 */
public class ViveTaczModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ViveTaczConfig cfg = ViveTaczConfig.get();

            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.literal("ViveTaCZ — VR Placement"))
                    .setSavingRunnable(cfg::save);

            ConfigEntryBuilder eb = builder.entryBuilder();

            ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));
            general.addEntry(eb.startBooleanToggle(Text.literal("Enable VR gun/ammo rendering"), cfg.enabled)
                    .setDefaultValue(true)
                    .setTooltip(Text.literal("Master switch for the whole VR item render."))
                    .setSaveConsumer(v -> cfg.enabled = v)
                    .build());
            general.addEntry(eb.startBooleanToggle(Text.literal("Static gun body (only mag/bolt animate)"), cfg.staticGunBody)
                    .setDefaultValue(false)
                    .setTooltip(Text.literal("Stops idle/run sway and keeps the gun still during reload — "
                            + "only the magazine, bolt and lid animate."))
                    .setSaveConsumer(v -> cfg.staticGunBody = v)
                    .build());
            general.addEntry(eb.startBooleanToggle(Text.literal("Show floating ammo HUD"), cfg.hudEnabled)
                    .setDefaultValue(true)
                    .setTooltip(Text.literal("Ammo count + fire mode floating next to the gun."))
                    .setSaveConsumer(v -> cfg.hudEnabled = v)
                    .build());
            general.addEntry(eb.startBooleanToggle(Text.literal("Off-hand reload gesture"), cfg.reloadGestureEnabled)
                    .setDefaultValue(true)
                    .setTooltip(Text.literal("Bring your off hand up to the gun to reload."))
                    .setSaveConsumer(v -> cfg.reloadGestureEnabled = v)
                    .build());
            general.addEntry(eb.startFloatField(Text.literal("Reload gesture distance (blocks)"), cfg.reloadGestureDistance)
                    .setDefaultValue(0.3f)
                    .setMin(0.05f).setMax(1.0f)
                    .setTooltip(Text.literal("How close the off hand must get to the gun to trigger/seat a reload."))
                    .setSaveConsumer(v -> cfg.reloadGestureDistance = v)
                    .build());
            general.addEntry(eb.startBooleanToggle(Text.literal("Physical magazine reload"), cfg.magReloadEnabled)
                    .setDefaultValue(true)
                    .setTooltip(Text.literal("Grab a magazine from the body pouch and bring it to the gun to reload."))
                    .setSaveConsumer(v -> cfg.magReloadEnabled = v)
                    .build());
            general.addEntry(eb.startBooleanToggle(Text.literal("Two-stage reload (drop then insert)"), cfg.twoStageReload)
                    .setDefaultValue(false)
                    .setTooltip(Text.literal("Press the 'Drop Magazine' key first (bind it in Controls / Vivecraft), "
                            + "then bring a new mag to the gun to reload."))
                    .setSaveConsumer(v -> cfg.twoStageReload = v)
                    .build());
            general.addEntry(eb.startBooleanToggle(Text.literal("Show reload zone marker"), cfg.showReloadZone)
                    .setDefaultValue(true)
                    .setTooltip(Text.literal("A marker where you bring the mag to reload (green when ready)."))
                    .setSaveConsumer(v -> cfg.showReloadZone = v)
                    .build());
            general.addEntry(eb.startFloatField(Text.literal("Magazine grab distance (blocks)"), cfg.magGrabDistance)
                    .setDefaultValue(0.1f)
                    .setMin(0.05f).setMax(1.0f)
                    .setTooltip(Text.literal("How close the off hand must get to the pouch to grab the mag."))
                    .setSaveConsumer(v -> cfg.magGrabDistance = v)
                    .build());
            general.addEntry(eb.startFloatField(Text.literal("Reload cooldown (seconds)"), cfg.magCooldownSeconds)
                    .setDefaultValue(5.0f)
                    .setMin(0.0f).setMax(30.0f)
                    .setTooltip(Text.literal("Minimum time before you can grab another magazine."))
                    .setSaveConsumer(v -> cfg.magCooldownSeconds = v)
                    .build());
            general.addEntry(eb.startBooleanToggle(Text.literal("Aim from controller (barrel, not head)"), cfg.controllerAim)
                    .setDefaultValue(true)
                    .setTooltip(Text.literal("Bullets fire along the gun's controller aim instead of where you look."))
                    .setSaveConsumer(v -> cfg.controllerAim = v)
                    .build());
            general.addEntry(eb.startBooleanToggle(Text.literal("Muzzle tracers"), cfg.tracerFromBarrel)
                    .setDefaultValue(true)
                    .setTooltip(Text.literal("Draw a tracer from the barrel to the impact point on each shot."))
                    .setSaveConsumer(v -> cfg.tracerFromBarrel = v)
                    .build());
            general.addEntry(eb.startFloatField(Text.literal("Recoil strength"), cfg.recoilMultiplier)
                    .setDefaultValue(1.0f)
                    .setMin(0.0f).setMax(5.0f)
                    .setTooltip(Text.literal("How hard the gun kicks. 0 = none, 1 = default, higher = more."))
                    .setSaveConsumer(v -> cfg.recoilMultiplier = v)
                    .build());
            general.addEntry(eb.startBooleanToggle(Text.literal("Haptic feedback"), cfg.hapticFeedback)
                    .setDefaultValue(true)
                    .setTooltip(Text.literal("Vibrate the gun-hand controller on each shot."))
                    .setSaveConsumer(v -> cfg.hapticFeedback = v)
                    .build());
            general.addEntry(eb.startBooleanToggle(Text.literal("Debug logging"), cfg.debugLogging)
                    .setDefaultValue(false)
                    .setTooltip(Text.literal("Write detailed diagnostics (poses, barrel direction, bullet "
                            + "spawn, reload/grip events) to logs/vivetacz-debug.log. Press the 'Debug: Dump "
                            + "State' key for a full snapshot. Turn off when done — it's verbose."))
                    .setSaveConsumer(v -> cfg.debugLogging = v)
                    .build());

            addPlacementEntries(builder.getOrCreateCategory(Text.literal("Default Gun")),
                    eb, cfg.defaultGun, Placement.gunDefault());

            addPlacementEntries(builder.getOrCreateCategory(Text.literal("Ammo HUD")),
                    eb, cfg.hud, Placement.hudDefault());

            addPlacementEntries(builder.getOrCreateCategory(Text.literal("Ammo Item (held)")),
                    eb, cfg.ammo, Placement.ammoDefault());

            // Ammo box sitting next to the gun (offset in the gun hand's frame).
            addPlacementEntries(builder.getOrCreateCategory(Text.literal("Ammo Box (next to gun)")),
                    eb, cfg.pouch, Placement.pouchDefault());

            // The gun's reload magazine while held in the off hand (offset in the off-hand frame).
            addPlacementEntries(builder.getOrCreateCategory(Text.literal("Held Magazine")),
                    eb, cfg.magazine, Placement.magazineDefault());

            // Where the default "insert the mag" zone sits relative to the gun hand (only posX/Y/Z matter).
            addPlacementEntries(builder.getOrCreateCategory(Text.literal("Reload Zone (default)")),
                    eb, cfg.reloadZone, Placement.reloadZoneDefault());

            // Default aim offset: the angle from the controller's pointing axis to the barrel.
            ConfigCategory aimCat = builder.getOrCreateCategory(Text.literal("Aim Offset (default)"));
            aimCat.addEntry(eb.startTextDescription(
                    Text.literal("Angle from the controller to the visible barrel. Tune Yaw (left/right), "
                            + "Pitch (up/down, + = down) and Roll until bullets hit where the barrel points.")).build());
            addAimEntries(aimCat, eb, cfg.aimOffset, Placement.aimOffsetDefault());

            // Per-gun overrides for the gun currently in hand (if any).
            String heldGunId = heldGunId();
            if (heldGunId != null) {
                Placement override = cfg.getOrCreateGunOverride(heldGunId);
                ConfigCategory cat = builder.getOrCreateCategory(Text.literal("Held Gun: " + heldGunId));
                cat.addEntry(eb.startTextDescription(
                        Text.literal("These values apply only to " + heldGunId
                                + ". Adjust and Save to give this gun its own placement.")).build());
                addPlacementEntries(cat, eb, override, cfg.defaultGun);

                // Per-gun reload zone (mag wells differ per gun).
                Placement rzOverride = cfg.getOrCreateReloadZone(heldGunId);
                ConfigCategory rzCat = builder.getOrCreateCategory(Text.literal("Reload Zone: " + heldGunId));
                rzCat.addEntry(eb.startTextDescription(
                        Text.literal("Where you insert the mag for " + heldGunId
                                + " (only Position X/Y/Z matter).")).build());
                addPlacementEntries(rzCat, eb, rzOverride, cfg.reloadZone);

                // Per-gun aim offset (each gun's barrel sits at its own angle in the hand).
                Placement aimOverride = cfg.getOrCreateAimOffset(heldGunId);
                ConfigCategory aimGunCat = builder.getOrCreateCategory(Text.literal("Aim Offset: " + heldGunId));
                aimGunCat.addEntry(eb.startTextDescription(
                        Text.literal("Barrel angle for " + heldGunId + ". Tune Yaw/Pitch/Roll until "
                                + "bullets hit where this gun's barrel points (Pitch + = down).")).build());
                addAimEntries(aimGunCat, eb, aimOverride, cfg.aimOffset);
            }

            return builder.build();
        };
    }

    private static void addPlacementEntries(ConfigCategory cat, ConfigEntryBuilder eb, Placement p, Placement def) {
        cat.addEntry(eb.startFloatField(Text.literal("Position X (right/left)"), p.posX)
                .setDefaultValue(def.posX)
                .setTooltip(Text.literal("Blocks. + moves toward the right of the controller."))
                .setSaveConsumer(v -> p.posX = v).build());
        cat.addEntry(eb.startFloatField(Text.literal("Position Y (up/down)"), p.posY)
                .setDefaultValue(def.posY)
                .setTooltip(Text.literal("Blocks. + moves up."))
                .setSaveConsumer(v -> p.posY = v).build());
        cat.addEntry(eb.startFloatField(Text.literal("Position Z (forward/back)"), p.posZ)
                .setDefaultValue(def.posZ)
                .setTooltip(Text.literal("Blocks. + moves forward."))
                .setSaveConsumer(v -> p.posZ = v).build());
        cat.addEntry(eb.startFloatField(Text.literal("Yaw (°)"), p.yaw)
                .setDefaultValue(def.yaw)
                .setTooltip(Text.literal("Left/right barrel direction. 180 flips it forward."))
                .setSaveConsumer(v -> p.yaw = v).build());
        cat.addEntry(eb.startFloatField(Text.literal("Pitch (°)"), p.pitch)
                .setDefaultValue(def.pitch)
                .setTooltip(Text.literal("Up/down barrel tilt."))
                .setSaveConsumer(v -> p.pitch = v).build());
        cat.addEntry(eb.startFloatField(Text.literal("Roll (°)"), p.roll)
                .setDefaultValue(def.roll)
                .setTooltip(Text.literal("Barrel roll about its own axis."))
                .setSaveConsumer(v -> p.roll = v).build());
        cat.addEntry(eb.startFloatField(Text.literal("Scale"), p.scale)
                .setDefaultValue(def.scale)
                .setTooltip(Text.literal("Uniform size multiplier."))
                .setSaveConsumer(v -> p.scale = v).build());
    }

    /** Aim offset is rotation-only (yaw/pitch/roll); position and scale are ignored. */
    private static void addAimEntries(ConfigCategory cat, ConfigEntryBuilder eb, Placement p, Placement def) {
        cat.addEntry(eb.startFloatField(Text.literal("Yaw (° left/right)"), p.yaw)
                .setDefaultValue(def.yaw)
                .setMin(-90.0f).setMax(90.0f)
                .setTooltip(Text.literal("Swing the barrel line left/right. + aims right."))
                .setSaveConsumer(v -> p.yaw = v).build());
        cat.addEntry(eb.startFloatField(Text.literal("Pitch (° up/down, + = down)"), p.pitch)
                .setDefaultValue(def.pitch)
                .setMin(-90.0f).setMax(90.0f)
                .setTooltip(Text.literal("Tilt the barrel line up/down. + aims down (grip angle)."))
                .setSaveConsumer(v -> p.pitch = v).build());
        cat.addEntry(eb.startFloatField(Text.literal("Roll (°)"), p.roll)
                .setDefaultValue(def.roll)
                .setMin(-90.0f).setMax(90.0f)
                .setTooltip(Text.literal("Twist about the barrel axis (rarely needed)."))
                .setSaveConsumer(v -> p.roll = v).build());
    }

    /** Gun id of the item in the player's main hand, or null if none/not a gun. */
    private static String heldGunId() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return null;
        ItemStack stack = player.getMainHandStack();
        if (stack == null || !TaczBridge.isGun(stack.getItem())) return null;
        return TaczBridge.getGunId(stack.getItem(), stack);
    }
}
