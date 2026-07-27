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

package com.vivetacz.vr;

/**
 * Process-wide access point to the single {@link VivecraftBridge} instance.
 *
 * Mixins can't easily receive constructor-injected dependencies, so they reach
 * VR state through this tiny static holder. The bridge itself is cheap and
 * self-initialising, so a global singleton is the pragmatic choice.
 */
public final class VRStatus {

    private static final VivecraftBridge BRIDGE = new VivecraftBridge();

    private VRStatus() {}

    public static VivecraftBridge bridge() {
        return BRIDGE;
    }

    /** Convenience gate used throughout the mod. */
    public static boolean isVRActive() {
        return BRIDGE.isVRActive();
    }
}
