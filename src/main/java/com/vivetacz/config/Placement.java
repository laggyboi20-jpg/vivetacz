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

/**
 * A single VR placement: position offset (blocks), rotation (degrees) and scale,
 * applied to a held item at the controller. Mutable so the config screen can
 * edit instances in place.
 */
public class Placement {

    public float posX;
    public float posY;
    public float posZ;
    public float yaw;
    public float pitch;
    public float roll;
    public float scale = 1.0f;

    public Placement() {}

    public Placement(float posX, float posY, float posZ, float yaw, float pitch, float roll, float scale) {
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.scale = scale;
    }

    public Placement copy() {
        return new Placement(posX, posY, posZ, yaw, pitch, roll, scale);
    }

    /** The tuned-in default for guns (from in-VR testing). */
    public static Placement gunDefault() {
        return new Placement(-1.27f, -0.6f, -1.22f, 180.0f, 44.0f, 0.0f, 1.4f);
    }

    /** Held ammo item placement. */
    public static Placement ammoDefault() {
        return new Placement(-1.27f, -0.6f, -1.22f, 0.0f, 44.0f, 0.0f, 1.4f);
    }

    /** Floating ammo/heat HUD near the gun (relative to the controller). */
    public static Placement hudDefault() {
        return new Placement(0.28f, 0.4f, 0.5f, 0.0f, -44.0f, 0.0f, 0.5f);
    }

    /** Ammo box sitting next to the gun, offset in the gun hand's own frame. */
    public static Placement pouchDefault() {
        return new Placement(-0.09f, 0.01f, 0.14f, 91.0f, 0.0f, 0.0f, 0.2f);
    }

    /** Held magazine (the gun's reload mag) — offset/rotation in the off hand's frame. */
    public static Placement magazineDefault() {
        return new Placement(0.0f, 0.0f, 0.0f, 180.0f, 180.0f, 0.0f, 0.3f);
    }

    /** Reload/insert detection zone, offset from the gun hand in its own frame (posX/Y/Z used). */
    public static Placement reloadZoneDefault() {
        return new Placement(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
    }

    /** Aim offset (controller-forward → barrel): only yaw/pitch/roll used, pitch positive = down. */
    public static Placement aimOffsetDefault() {
        return new Placement(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
    }
}
