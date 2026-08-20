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

import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;

/**
 * Thin reflection bridge to Vivecraft 1.3.x's public client API.
 *
 * We wire Vivecraft purely by reflection so ViveTaCZ still loads (and simply
 * stays inert) when Vivecraft is absent or the player is in desktop mode.
 *
 * Confirmed method chain (Vivecraft 1.20.1-1.3.9-fabric):
 *   VRClientAPI.instance()             -> VRClientAPI singleton (static)
 *   VRClientAPI.isVRActive()           -> boolean
 *   VRClientAPI.getWorldRenderPose()   -> VRPose (nullable — null when not in VR)
 *   VRPose.getMainHand()/getOffHand()/getHead() -> VRBodyPartData
 *   VRBodyPartData.getPos()            -> Vec3d (class_243, world-space)
 *   VRBodyPartData.getRotation()       -> org.joml.Quaternionfc
 *
 * Method handles are cached after the first successful init.
 */
public final class VivecraftBridge {

    private boolean initialized        = false;
    private boolean vivecraftAvailable = false;

    private Object apiInstance = null;

    private Method mIsVRActive         = null;
    private Method mGetWorldRenderPose = null;
    private Method mGetMainHand        = null;
    private Method mGetOffHand         = null;
    private Method mGetHead            = null;
    private Method mGetPos             = null;
    private Method mGetDir             = null;
    private Method mGetRotation        = null;
    private Method mTriggerHaptic      = null;
    private Object bodyPartMainHandEnum = null; // VRBodyPart.MAIN_HAND for haptics

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("ViveTaCZ/VivecraftBridge");

    // ------------------------------------------------------------------
    // Lazy init — retried until it either succeeds or fails permanently.
    // Vivecraft may not be ready on the very first tick.
    // ------------------------------------------------------------------
    private void initIfNeeded() {
        if (initialized) return;

        try {
            Class<?> apiClass      = Class.forName("org.vivecraft.api.client.VRClientAPI");
            Class<?> poseClass     = Class.forName("org.vivecraft.api.data.VRPose");
            Class<?> bodyPartClass = Class.forName("org.vivecraft.api.data.VRBodyPartData");

            Method instanceMethod = apiClass.getMethod("instance");
            apiInstance = instanceMethod.invoke(null);
            if (apiInstance == null) {
                // Installed but not initialised yet — try again next call.
                return;
            }

            mIsVRActive         = apiClass.getMethod("isVRActive");
            mGetWorldRenderPose = apiClass.getMethod("getWorldRenderPose");
            mGetMainHand        = poseClass.getMethod("getMainHand");
            mGetOffHand         = poseClass.getMethod("getOffHand");
            mGetHead            = poseClass.getMethod("getHead");
            mGetPos             = bodyPartClass.getMethod("getPos");

            // Optional extras — tolerate their absence on odd versions.
            mGetRotation  = optional(bodyPartClass, "getRotation");
            mGetDir       = optional(bodyPartClass, "getDir");

            vivecraftAvailable = true;
            initialized        = true;
            LOGGER.info("[ViveTaCZ] Vivecraft API connected — VR gun support armed.");

        } catch (ClassNotFoundException e) {
            initialized        = true;
            vivecraftAvailable = false;
            LOGGER.info("[ViveTaCZ] Vivecraft not found — running inert (desktop TaCZ unaffected).");
        } catch (NoSuchMethodException e) {
            initialized        = true;
            vivecraftAvailable = false;
            LOGGER.error("[ViveTaCZ] Vivecraft API method missing: {} — targets Vivecraft 1.3.x.", e.getMessage());
        } catch (Exception e) {
            initialized        = true;
            vivecraftAvailable = false;
            LOGGER.error("[ViveTaCZ] Vivecraft bridge init failed", e);
        }
    }

    private static Method optional(Class<?> c, String name, Class<?>... args) {
        try {
            return c.getMethod(name, args);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Public surface
    // ------------------------------------------------------------------

    /** True only when VR hardware is active and pose data is live. */
    public boolean isVRActive() {
        initIfNeeded();
        if (!vivecraftAvailable) return false;
        try {
            return (boolean) mIsVRActive.invoke(apiInstance);
        } catch (Exception e) {
            return false;
        }
    }

    // ---- Haptics ----------------------------------------------------------
    private boolean hapticResolved = false;

    /** Buzz the main-hand controller. Durations/frequency/amplitude are best-effort. */
    public void triggerHaptic(float durationSeconds, float frequency, float amplitude) {
        initIfNeeded();
        if (!vivecraftAvailable || apiInstance == null) return;
        if (!hapticResolved) {
            hapticResolved = true;
            try {
                for (Method m : apiInstance.getClass().getMethods()) {
                    if (m.getName().equals("triggerHapticPulse") && m.getParameterCount() == 5) {
                        mTriggerHaptic = m;
                        Class<?> enumClass = m.getParameterTypes()[0];
                        Object[] consts = enumClass.getEnumConstants();
                        if (consts != null) {
                            for (Object c : consts) {
                                if (c.toString().toUpperCase().contains("MAIN")) {
                                    bodyPartMainHandEnum = c;
                                    break;
                                }
                            }
                            if (bodyPartMainHandEnum == null && consts.length > 0) {
                                bodyPartMainHandEnum = consts[0];
                            }
                        }
                        break;
                    }
                }
            } catch (Throwable t) {
                mTriggerHaptic = null;
            }
        }
        if (mTriggerHaptic == null || bodyPartMainHandEnum == null) return;
        try {
            mTriggerHaptic.invoke(apiInstance, bodyPartMainHandEnum,
                    durationSeconds, frequency, amplitude, 0.0f);
        } catch (Throwable ignored) {
        }
    }

    /** World-space position of the dominant (main) hand, or null. */
    public Vec3d getMainHandPos() { return extractPos(mGetMainHand); }

    /** World-space position of the off hand, or null. */
    public Vec3d getOffHandPos()  { return extractPos(mGetOffHand); }

    /** World-space position of the head / eyes, or null. */
    public Vec3d getHeadPos()     { return extractPos(mGetHead); }

    /**
     * Main-hand orientation as an org.joml.Quaternionfc (returned as Object to
     * avoid a hard JOML-version coupling), or null. Used later for physical aim.
     */
    public Object getMainHandRotation() { return extractRotation(mGetMainHand); }

    /** Off-hand orientation quaternion (Object), or null. */
    public Object getOffHandRotation()  { return extractRotation(mGetOffHand); }

    /**
     * World-space forward direction of the main hand (barrel aim), or null.
     * Prefers VRBodyPartData.getDir(); if that's unavailable, derives forward
     * from the (confirmed-working) rotation quaternion — controller forward is -Z.
     */
    public Vec3d getMainHandDir() {
        Vec3d d = extractDir(mGetMainHand);
        if (d != null && d.lengthSquared() > 1.0e-9) {
            logDirOnce("getDir", d);
            return d;
        }
        Object rot = getMainHandRotation();
        if (rot instanceof org.joml.Quaternionfc) {
            org.joml.Vector3f f = new org.joml.Vector3f(0.0f, 0.0f, -1.0f);
            ((org.joml.Quaternionfc) rot).transform(f);
            Vec3d fd = new Vec3d(f.x, f.y, f.z);
            logDirOnce("rotation(-Z)", fd);
            return fd;
        }
        logDirOnce("none", null);
        return null;
    }

    /**
     * Barrel aim direction: the controller's forward pitched down by
     * {@code pitchOffsetDeg} in the controller's own frame (so it accounts for
     * the grip angle between the controller axis and the visible gun barrel, and
     * still rotates correctly as you turn/roll the gun).
     */
    public Vec3d getMainHandBarrelDir(float pitchOffsetDeg) {
        Object rot = getMainHandRotation();
        if (rot instanceof org.joml.Quaternionfc) {
            double r = Math.toRadians(pitchOffsetDeg);
            org.joml.Vector3f local = new org.joml.Vector3f(
                    0.0f, -(float) Math.sin(r), -(float) Math.cos(r));
            ((org.joml.Quaternionfc) rot).transform(local);
            if (local.lengthSquared() > 1.0e-9f) {
                return new Vec3d(local.x, local.y, local.z);
            }
        }
        return getMainHandDir();
    }

    private boolean dirLogged = false;
    private void logDirOnce(String source, Vec3d dir) {
        if (!dirLogged) {
            dirLogged = true;
            LOGGER.info("[ViveTaCZ] main-hand aim direction source = {} , dir = {}", source, dir);
        }
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------
    private Vec3d extractPos(Method bodyPartGetter) {
        Object bp = bodyPart(bodyPartGetter);
        if (bp == null) return null;
        try {
            return (Vec3d) mGetPos.invoke(bp);
        } catch (Exception e) {
            return null;
        }
    }

    private Object extractRotation(Method bodyPartGetter) {
        if (mGetRotation == null) return null;
        Object bp = bodyPart(bodyPartGetter);
        if (bp == null) return null;
        try {
            return mGetRotation.invoke(bp);
        } catch (Exception e) {
            return null;
        }
    }

    private Vec3d extractDir(Method bodyPartGetter) {
        if (mGetDir == null) return null;
        Object bp = bodyPart(bodyPartGetter);
        if (bp == null) return null;
        try {
            return (Vec3d) mGetDir.invoke(bp);
        } catch (Exception e) {
            return null;
        }
    }

    private Object bodyPart(Method bodyPartGetter) {
        initIfNeeded();
        if (!vivecraftAvailable || bodyPartGetter == null) return null;
        try {
            Object pose = mGetWorldRenderPose.invoke(apiInstance);
            if (pose == null) return null;        // not in VR this frame
            return bodyPartGetter.invoke(pose);
        } catch (Exception e) {
            return null;
        }
    }
}
