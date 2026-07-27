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

package com.vivetacz.debug;

import com.vivetacz.client.AimUtil;
import com.vivetacz.compat.TaczBridge;
import com.vivetacz.config.Placement;
import com.vivetacz.config.ViveTaczConfig;
import com.vivetacz.gameplay.MagazineReload;
import com.vivetacz.vr.VRStatus;
import com.vivetacz.vr.VivecraftBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

/**
 * Higher-level diagnostics: a throttled per-frame pose/aim stream while a gun is held,
 * and an on-demand full-state snapshot (bound to the "Debug Dump" key). Everything is
 * gated on {@link ViveTaczConfig#debugLogging} and cheap when off.
 */
public final class DebugState {

    private DebugState() {}

    /** Streamed a few times a second so aim can be watched without firing. */
    public static void onClientTick(MinecraftClient mc) {
        if (!DebugLog.enabled()) return;
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        ItemStack gun = player.getMainHandStack();
        if (gun == null || !TaczBridge.isGun(gun.getItem())) return;
        if (!VRStatus.isVRActive()) return;

        VivecraftBridge b = VRStatus.bridge();
        String gunId = TaczBridge.getGunId(gun.getItem(), gun);
        Placement aim = ViveTaczConfig.get().aimOffsetFor(gunId);
        Vec3d barrel = AimUtil.barrelDir(b, aim);

        DebugLog.throttled("pose", 400L, "POSE",
                "gun=%s main=%s mainRot=%s off=%s barrel=%s",
                gunId, DebugLog.v(b.getMainHandPos()), DebugLog.q(b.getMainHandRotation()),
                DebugLog.v(b.getOffHandPos()), DebugLog.v(barrel));
    }

    /** Full one-shot snapshot of poses, held gun, and every resolved zone/offset. */
    public static void dump(MinecraftClient mc) {
        // Force a line out even if throttled elsewhere; but still respect the master toggle.
        if (!DebugLog.enabled()) {
            com.vivetacz.ViveTaCZClient.LOGGER.info("[ViveTaCZ] Debug dump requested but debug logging is OFF "
                    + "(enable it in the config first).");
            return;
        }
        DebugLog.log("DUMP", "----- full state snapshot -----");
        DebugLog.logf("DUMP", "vrActive=%b", VRStatus.isVRActive());

        ClientPlayerEntity player = mc.player;
        if (player == null) { DebugLog.log("DUMP", "no player"); return; }

        VivecraftBridge b = VRStatus.bridge();
        DebugLog.logf("DUMP", "head=%s", DebugLog.v(b.getHeadPos()));
        DebugLog.logf("DUMP", "mainHand pos=%s rot=%s", DebugLog.v(b.getMainHandPos()), DebugLog.q(b.getMainHandRotation()));
        DebugLog.logf("DUMP", "offHand  pos=%s rot=%s", DebugLog.v(b.getOffHandPos()), DebugLog.q(b.getOffHandRotation()));

        ItemStack gun = player.getMainHandStack();
        if (gun == null || !TaczBridge.isGun(gun.getItem())) {
            DebugLog.log("DUMP", "no gun in main hand");
            DebugLog.log("DUMP", "-------------------------------");
            return;
        }

        ViveTaczConfig cfg = ViveTaczConfig.get();
        String gunId = TaczBridge.getGunId(gun.getItem(), gun);
        Placement aim = cfg.aimOffsetFor(gunId);

        DebugLog.logf("DUMP", "gun=%s ammo=%d fireMode=%s heat=%.2f", gunId,
                TaczBridge.getCurrentAmmoCount(gun.getItem(), gun),
                TaczBridge.getFireMode(gun.getItem(), gun),
                TaczBridge.getHeatPercent(gun.getItem(), gun));
        DebugLog.logf("DUMP", "placement=%s", place(cfg.gunPlacement(gunId)));
        DebugLog.logf("DUMP", "aimOffset=%s barrelDir=%s", place(aim), DebugLog.v(AimUtil.barrelDir(b, aim)));
        DebugLog.logf("DUMP", "reloadZone world=%s (%s)",
                DebugLog.v(MagazineReload.gunHandAnchor(b, cfg.reloadZoneFor(gunId))), place(cfg.reloadZoneFor(gunId)));
        DebugLog.logf("DUMP", "magPouch   world=%s", DebugLog.v(MagazineReload.gunHandAnchor(b, cfg.pouch)));
        DebugLog.logf("DUMP", "magHeld=%b magDropped=%b", MagazineReload.isHoldingMag(), MagazineReload.isMagDropped());
        DebugLog.log("DUMP", "-------------------------------");
    }

    private static String place(Placement p) {
        return p == null ? "null"
                : String.format("pos(%.2f,%.2f,%.2f) rot(y%.1f p%.1f r%.1f) s%.2f",
                p.posX, p.posY, p.posZ, p.yaw, p.pitch, p.roll, p.scale);
    }
}
