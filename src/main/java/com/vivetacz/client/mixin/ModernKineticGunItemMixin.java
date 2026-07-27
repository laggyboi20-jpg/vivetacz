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

package com.vivetacz.client.mixin;

import com.vivetacz.ViveTaCZClient;
import com.vivetacz.compat.TaczBridge;
import com.vivetacz.config.ViveTaczConfig;
import com.vivetacz.gameplay.RecoilState;
import com.vivetacz.vr.VRStatus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Physical bullet origin + direction (singleplayer / integrated server).
 *
 * TaCZ spawns the bullet at the shooter's eye and aims it by the shooter's
 * yaw/pitch — both wrong in VR (eye origin → parallax; HMD rotation → aims where
 * you look). After TaCZ has set the projectile up, we simply move it to the gun
 * muzzle and re-aim its velocity straight down the controller's barrel. The
 * bullet then travels the exact line the barrel points along — matching the
 * tracer — with no eye/parallax involved.
 *
 * Only affects the local VR player's own shots.
 */
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunItem")
public abstract class ModernKineticGunItemMixin {

    private static final double MUZZLE_FORWARD = 0.35;
    private static boolean vivetacz$logged = false;

    @Inject(
            method = "doBulletSpread(Lcom/tacz/guns/entity/shooter/ShooterDataHolder;Lnet/minecraft/class_1799;Lnet/minecraft/class_1309;Lnet/minecraft/class_1676;IFFFF)V",
            at = @At("TAIL"))
    private void vivetacz$muzzleBullet(@Coerce Object dataHolder, ItemStack gunItem, LivingEntity shooter,
                                       ProjectileEntity projectile, int bulletCnt, float speed,
                                       float inaccuracy, float pitch, float yaw, CallbackInfo ci) {
        ViveTaczConfig cfg = ViveTaczConfig.get();
        if (!cfg.enabled || !cfg.controllerAim || !VRStatus.isVRActive()) return;
        if (!(shooter instanceof PlayerEntity player)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !mc.player.getUuid().equals(player.getUuid())) return;

        Vec3d ctrl = VRStatus.bridge().getMainHandPos();
        // Bullets follow the visible barrel via the per-gun aim offset (yaw/pitch/roll).
        String gunId = TaczBridge.getGunId(gunItem.getItem(), gunItem);
        Vec3d dirV = com.vivetacz.client.AimUtil.barrelDir(VRStatus.bridge(), cfg.aimOffsetFor(gunId));
        if (dirV == null) dirV = VRStatus.bridge().getMainHandBarrelDir(cfg.aimOffsetFor(gunId).pitch);
        if (ctrl == null || dirV == null || dirV.lengthSquared() < 1.0e-9) return;
        Vec3d dir = dirV.normalize();
        Vec3d muzzle = ctrl.add(dir.multiply(MUZZLE_FORWARD));

        // Move the bullet to the muzzle (and fix its damage-range start point).
        projectile.setPosition(muzzle.x, muzzle.y, muzzle.z);
        TaczBridge.setField(projectile, "startPos", muzzle);

        // Barrel direction → yaw/pitch, plus recoil climb.
        double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float barrelYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float barrelPitch = (float) -Math.toDegrees(Math.atan2(dir.y, horiz));
        float[] recoil = RecoilState.fireAndGet(Math.max(0f, cfg.recoilMultiplier));
        float finalPitch = barrelPitch - recoil[0]; // negative pitch = up
        float finalYaw = barrelYaw + recoil[1];

        // Re-fire down that direction, reusing TaCZ's inaccuracy for natural spread.
        float spd = (float) projectile.getVelocity().length();
        if (spd < 1.0e-6f) spd = speed;
        projectile.setVelocity(shooter, finalPitch, finalYaw, 0.0f, spd, inaccuracy);

        if (com.vivetacz.debug.DebugLog.enabled()) {
            com.vivetacz.debug.DebugLog.logf("SHOT",
                    "gun=%s aim=%s ctrl=%s ctrlRot=%s dir=%s barrelYaw/Pitch=%.2f/%.2f "
                            + "recoil=%.2f/%.2f final=%.2f/%.2f muzzle=%s speed=%.2f inacc=%.4f",
                    gunId, aimStr(cfg.aimOffsetFor(gunId)),
                    com.vivetacz.debug.DebugLog.v(ctrl),
                    com.vivetacz.debug.DebugLog.q(VRStatus.bridge().getMainHandRotation()),
                    com.vivetacz.debug.DebugLog.v(dir), barrelYaw, barrelPitch,
                    recoil[0], recoil[1], finalPitch, finalYaw, com.vivetacz.debug.DebugLog.v(muzzle),
                    spd, inaccuracy);
        } else if (!vivetacz$logged) {
            vivetacz$logged = true;
            ViveTaCZClient.LOGGER.info("[ViveTaCZ] bullet at muzzle {} barrel yaw/pitch {}/{} recoil {}/{}",
                    muzzle, barrelYaw, barrelPitch, recoil[0], recoil[1]);
        }
    }

    private static String aimStr(com.vivetacz.config.Placement a) {
        return String.format("(y%.1f p%.1f r%.1f)", a.yaw, a.pitch, a.roll);
    }
}
