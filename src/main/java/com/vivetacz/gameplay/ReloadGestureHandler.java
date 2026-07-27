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

package com.vivetacz.gameplay;

import com.vivetacz.compat.TaczBridge;
import com.vivetacz.config.ViveTaczConfig;
import com.vivetacz.vr.VivecraftBridge;
import com.vivetacz.vr.VRStatus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

/**
 * Physical reload: bring the off hand up to the gun (as if seating a magazine)
 * to trigger TaCZ's reload. Runs every client tick.
 *
 * A hysteresis "primed" flag means one approach fires exactly one reload — the
 * hands must separate again before another can trigger.
 */
public final class ReloadGestureHandler {

    private static boolean primed = true;

    private ReloadGestureHandler() {}

    public static void onClientTick(MinecraftClient mc) {
        ViveTaczConfig cfg = ViveTaczConfig.get();
        if (!cfg.enabled || !cfg.reloadGestureEnabled) return;
        if (!VRStatus.isVRActive()) return;

        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        ItemStack held = player.getMainHandStack();
        if (held == null || !TaczBridge.isGun(held.getItem())) {
            primed = true;
            return;
        }

        VivecraftBridge bridge = VRStatus.bridge();
        Vec3d gunHand = bridge.getMainHandPos();
        Vec3d offHand = bridge.getOffHandPos();
        if (gunHand == null || offHand == null) return;

        double dist = gunHand.distanceTo(offHand);
        double trigger = cfg.reloadGestureDistance;

        if (dist <= trigger) {
            if (primed) {
                primed = false;
                // Close only the Vivecraft hotbar so the reload isn't swallowed by it.
                MagazineReload.closeVrHotbar(mc);
                TaczBridge.triggerReload(player);
            }
        } else if (dist > trigger + 0.10) {
            // separated enough — ready to trigger again
            primed = true;
        }
    }
}
