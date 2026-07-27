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

package com.vivetacz.client;

import com.vivetacz.config.Placement;
import com.vivetacz.vr.VivecraftBridge;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Shared aiming raycast used by the aim override and the muzzle tracer. */
public final class AimUtil {

    public static final double MAX_RANGE = 128.0;

    private AimUtil() {}

    /**
     * World-space barrel direction: the main-hand controller's forward (-Z) rotated
     * by a per-gun aim offset (yaw/pitch/roll) applied in the controller's own frame.
     *
     * The offset is exactly the fixed rotation from the controller's pointing axis to
     * the visible barrel — so tuning yaw/pitch/roll lines the bullets up with the gun's
     * barrel in every axis, not just up/down. {@code pitch} is positive-down to match
     * the old single "aim pitch offset" knob it replaces.
     *
     * Returns null if the controller rotation isn't available (VR off / not ready).
     */
    public static Vec3d barrelDir(VivecraftBridge bridge, Placement aim) {
        Object rotObj = bridge.getMainHandRotation();
        if (!(rotObj instanceof org.joml.Quaternionfc)) return null;

        org.joml.Quaternionf q = new org.joml.Quaternionf((org.joml.Quaternionfc) rotObj);
        // Same local-frame (post-multiplied) order the render uses: yaw, then pitch, then roll.
        q.rotateY((float) Math.toRadians(aim.yaw));
        q.rotateX((float) Math.toRadians(-aim.pitch)); // +pitch = barrel down
        q.rotateZ((float) Math.toRadians(aim.roll));

        org.joml.Vector3f fwd = new org.joml.Vector3f(0.0f, 0.0f, -1.0f);
        q.transform(fwd);
        if (fwd.lengthSquared() < 1.0e-9f) return null;
        return new Vec3d(fwd.x, fwd.y, fwd.z).normalize();
    }

    /** World point the barrel is pointing at (block or entity), or the far end if nothing is hit. */
    public static Vec3d raycastTarget(PlayerEntity player, Vec3d start, Vec3d dir) {
        World world = player.getWorld();
        Vec3d end = start.add(dir.multiply(MAX_RANGE));

        BlockHitResult block = world.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        Vec3d hit = (block.getType() != HitResult.Type.MISS) ? block.getPos() : end;

        double limitSq = start.squaredDistanceTo(hit);
        Box box = player.getBoundingBox().stretch(dir.multiply(MAX_RANGE)).expand(1.0);
        EntityHitResult entity = ProjectileUtil.raycast(player, start, hit, box,
                e -> !e.isSpectator() && e.canHit() && e != player, limitSq);
        return entity != null ? entity.getPos() : hit;
    }
}
