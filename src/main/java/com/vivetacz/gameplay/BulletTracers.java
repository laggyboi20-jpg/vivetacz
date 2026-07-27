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

import com.vivetacz.client.AimUtil;
import com.vivetacz.compat.TaczBridge;
import com.vivetacz.config.ViveTaczConfig;
import com.vivetacz.vr.VRStatus;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side muzzle tracers. TaCZ's bullet entity is spawned server-side at the
 * eye, so its trail comes from the head. We instead draw our own short-lived beam
 * from the barrel to the impact point on each shot, detected by the gun's loaded
 * ammo dropping.
 */
public final class BulletTracers {

    private record Tracer(Vec3d start, Vec3d end, long spawnMs) {}

    private static final List<Tracer> TRACERS = new ArrayList<>();
    private static final long LIFETIME_MS = 130L;
    private static final float HALF_WIDTH = 0.015f;    // beam half-thickness (blocks)
    private static final float MUZZLE_FORWARD = 0.35f; // push origin to the barrel tip

    private static int lastAmmo = -1;

    private BulletTracers() {}

    // ------------------------------------------------------------------ tick
    public static void onClientTick(MinecraftClient mc) {
        ViveTaczConfig cfg = ViveTaczConfig.get();
        if (!cfg.enabled || !VRStatus.isVRActive()) { lastAmmo = -1; return; }
        ClientPlayerEntity player = mc.player;
        if (player == null) { lastAmmo = -1; return; }
        ItemStack gun = player.getMainHandStack();
        if (gun == null || !TaczBridge.isGun(gun.getItem())) { lastAmmo = -1; return; }

        int ammo = TaczBridge.getCurrentAmmoCount(gun.getItem(), gun);
        if (lastAmmo >= 0 && ammo >= 0 && ammo < lastAmmo) {
            String gunId = TaczBridge.getGunId(gun.getItem(), gun);
            onShotFired(player, cfg, gunId);
        }
        lastAmmo = ammo;
    }

    private static void onShotFired(ClientPlayerEntity player, ViveTaczConfig cfg, String gunId) {
        if (cfg.hapticFeedback) {
            VRStatus.bridge().triggerHaptic(0.06f, 160.0f, 0.6f);
        }
        if (cfg.tracerFromBarrel) {
            spawnTracer(player, cfg, gunId);
        }
    }

    private static void spawnTracer(ClientPlayerEntity player, ViveTaczConfig cfg, String gunId) {
        Vec3d ctrl = VRStatus.bridge().getMainHandPos();
        // Match the bullet path: barrel direction from the per-gun aim offset.
        Vec3d dirV = com.vivetacz.client.AimUtil.barrelDir(VRStatus.bridge(), cfg.aimOffsetFor(gunId));
        if (dirV == null) dirV = VRStatus.bridge().getMainHandBarrelDir(cfg.aimOffsetFor(gunId).pitch);
        if (ctrl == null || dirV == null || dirV.lengthSquared() < 1.0e-9) return;

        // Match the bullet's recoil-adjusted direction so tracer and hit agree.
        float[] recoil = RecoilState.lastApplied();
        Vec3d dir = applyRecoil(dirV.normalize(), recoil[0], recoil[1]);

        Vec3d muzzle = ctrl.add(dir.multiply(MUZZLE_FORWARD));
        Vec3d target = AimUtil.raycastTarget(player, muzzle, dir);
        synchronized (TRACERS) {
            TRACERS.add(new Tracer(muzzle, target, System.currentTimeMillis()));
        }
        if (com.vivetacz.debug.DebugLog.enabled()) {
            com.vivetacz.debug.DebugLog.logf("TRACE", "gun=%s muzzle=%s dir=%s target=%s dist=%.2f",
                    gunId, com.vivetacz.debug.DebugLog.v(muzzle),
                    com.vivetacz.debug.DebugLog.v(dir), com.vivetacz.debug.DebugLog.v(target),
                    muzzle.distanceTo(target));
        }
    }

    /** Rotate a direction by recoil (pitch up by pitchDeg, yaw by yawDeg). */
    private static Vec3d applyRecoil(Vec3d dir, float pitchDeg, float yawDeg) {
        double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z)) + yawDeg;
        double pitch = -Math.toDegrees(Math.atan2(dir.y, horiz)) - pitchDeg;
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double cp = Math.cos(pr);
        return new Vec3d(-Math.sin(yr) * cp, -Math.sin(pr), Math.cos(yr) * cp);
    }

    // ---------------------------------------------------------------- render
    public static void render(WorldRenderContext ctx) {
        if (TRACERS.isEmpty()) return;
        VertexConsumerProvider consumers = ctx.consumers();
        if (consumers == null) return;

        long now = System.currentTimeMillis();
        Vec3d cam = ctx.camera().getPos();
        Matrix4f matrix = ctx.matrixStack().peek().getPositionMatrix();
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLightning());

        synchronized (TRACERS) {
            Iterator<Tracer> it = TRACERS.iterator();
            while (it.hasNext()) {
                Tracer t = it.next();
                long age = now - t.spawnMs;
                if (age >= LIFETIME_MS) { it.remove(); continue; }
                float alpha = 1.0f - (float) age / LIFETIME_MS;
                drawBeam(vc, matrix, t.start, t.end, cam, alpha);
            }
        }
    }

    private static void drawBeam(VertexConsumer vc, Matrix4f m, Vec3d s, Vec3d e, Vec3d cam, float alpha) {
        Vec3d dir = e.subtract(s);
        if (dir.lengthSquared() < 1.0e-9) return;
        Vec3d side = dir.crossProduct(cam.subtract(s));
        if (side.lengthSquared() < 1.0e-9) return;
        side = side.normalize().multiply(HALF_WIDTH);

        double sx = s.x - cam.x, sy = s.y - cam.y, sz = s.z - cam.z;
        double ex = e.x - cam.x, ey = e.y - cam.y, ez = e.z - cam.z;
        int argb = ((int) (alpha * 255) << 24) | 0x00FFF0A0; // warm tracer glow

        vertex(vc, m, sx - side.x, sy - side.y, sz - side.z, argb);
        vertex(vc, m, sx + side.x, sy + side.y, sz + side.z, argb);
        vertex(vc, m, ex + side.x, ey + side.y, ez + side.z, argb);
        vertex(vc, m, ex - side.x, ey - side.y, ez - side.z, argb);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, double x, double y, double z, int argb) {
        vc.vertex(m, (float) x, (float) y, (float) z).color(argb).next();
    }
}
