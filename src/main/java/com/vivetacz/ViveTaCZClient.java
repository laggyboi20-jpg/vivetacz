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

package com.vivetacz;

import com.vivetacz.config.ViveTaczConfig;
import com.vivetacz.gameplay.BulletTracers;
import com.vivetacz.gameplay.MagazineReload;
import com.vivetacz.gameplay.ReloadGestureHandler;
import com.vivetacz.vr.VRStatus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViveTaCZ — VR support for TaCZ Refabricated via Vivecraft.
 *
 * Client-only. The heavy lifting happens in Mixins (see the client.mixin
 * package); this entrypoint just announces itself and warms up the VR bridge.
 */
public final class ViveTaCZClient implements ClientModInitializer {

    public static final String MOD_ID = "vivetacz";
    public static final Logger LOGGER = LoggerFactory.getLogger("ViveTaCZ");

    /** "Drop Magazine" key — bind it to a controller button in Vivecraft's controls. */
    public static KeyBinding DROP_MAG;

    /** "Debug Dump" key — logs a full state snapshot to vivetacz-debug.log (debug logging must be on). */
    public static KeyBinding DEBUG_DUMP;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ViveTaCZ] Initialising VR support for TaCZ Refabricated.");

        DROP_MAG = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vivetacz.drop_mag", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.vivetacz"));
        DEBUG_DUMP = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vivetacz.debug_dump", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "key.categories.vivetacz"));
        // Touch the bridge so its first (harmless) reflection probe happens early.
        // It reports whether Vivecraft is present; VR itself may activate later.
        VRStatus.bridge();

        // Reload input each tick: physical magazine (grab from pouch → seat at gun) when
        // enabled, otherwise the simple off-hand-to-gun proximity gesture.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (ViveTaczConfig.get().magReloadEnabled) {
                MagazineReload.onClientTick(mc);
            } else {
                ReloadGestureHandler.onClientTick(mc);
            }
        });

        // Muzzle tracers: detect shots on tick, draw beams in the world.
        ClientTickEvents.END_CLIENT_TICK.register(BulletTracers::onClientTick);

        // Diagnostics: throttled pose/aim stream + on-demand full-state dump (debug logging must be on).
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            com.vivetacz.debug.DebugState.onClientTick(mc);
            if (DEBUG_DUMP != null) {
                while (DEBUG_DUMP.wasPressed()) {
                    com.vivetacz.debug.DebugState.dump(mc);
                }
            }
        });

        // Draw the physical magazine + muzzle tracers in the world.
        WorldRenderEvents.AFTER_ENTITIES.register(MagazineReload::render);
        WorldRenderEvents.AFTER_ENTITIES.register(BulletTracers::render);
        // Keep the gun visible while a config/menu screen is open (for aligning mags/zones).
        WorldRenderEvents.AFTER_ENTITIES.register(com.vivetacz.client.MenuGunRenderer::render);
    }
}
