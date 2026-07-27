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

/**
 * Simple recoil accumulator shared between the server bullet aim and the client
 * tracer (same JVM in singleplayer). Each shot climbs the aim up (and drifts a
 * little sideways); it recovers exponentially over time.
 *
 * Angles are in degrees: {@code pitchOffset} raises the muzzle, {@code yawOffset}
 * pushes it sideways.
 */
public final class RecoilState {

    private static final float RECOVER_PER_SEC = 6.0f; // higher = snappier recovery
    private static final float BASE_VERTICAL = 1.1f;   // degrees of climb per shot
    private static final float BASE_HORIZONTAL = 0.5f; // max sideways drift per shot
    private static final float MAX_PITCH = 18.0f;      // clamp so it can't flip over

    private static float pitch = 0f;
    private static float yaw = 0f;
    private static long lastMs = 0L;

    // What the most recent shot actually applied (so the tracer can match it).
    private static float lastAppliedPitch = 0f;
    private static float lastAppliedYaw = 0f;

    private RecoilState() {}

    /** Apply this shot's recoil (returns the offset to add), then kick for the next. */
    public static synchronized float[] fireAndGet(float multiplier) {
        decay();
        lastAppliedPitch = pitch;
        lastAppliedYaw = yaw;

        pitch = Math.min(MAX_PITCH, pitch + BASE_VERTICAL * multiplier);
        yaw += (float) ((Math.random() - 0.5) * 2.0 * BASE_HORIZONTAL * multiplier);
        lastMs = System.currentTimeMillis();

        return new float[]{lastAppliedPitch, lastAppliedYaw};
    }

    /** The offset the last shot used — for the tracer to match the bullet. */
    public static synchronized float[] lastApplied() {
        return new float[]{lastAppliedPitch, lastAppliedYaw};
    }

    private static void decay() {
        long now = System.currentTimeMillis();
        if (lastMs == 0L) { lastMs = now; return; }
        float dt = (now - lastMs) / 1000.0f;
        float f = (float) Math.exp(-dt * RECOVER_PER_SEC);
        pitch *= f;
        yaw *= f;
        lastMs = now;
    }
}
