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

package com.vivetacz.compat;

import com.vivetacz.ViveTaCZClient;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Reflection bridge into TaCZ Refabricated.
 *
 * We can't compile against TaCZ's distributed jar (it bundles ~54 nested mods
 * and won't remap under Loom), so ViveTaCZ references it only by string:
 *  - Mixins target {@code com.tacz.guns.*} classes via {@code @Mixin(targets=...)}.
 *  - Calls into TaCZ (here) go through cached reflection.
 *
 * TaCZ does not obfuscate its own members, so method/class names are stable.
 */
public final class TaczBridge {

    private static final String IGUN         = "com.tacz.guns.api.item.IGun";
    private static final String TIMELESS_API = "com.tacz.guns.api.TimelessAPI";

    private TaczBridge() {}

    // ---- IGun test --------------------------------------------------------
    private static Class<?> iGunClass;
    private static boolean  iGunResolved;

    public static boolean isGun(Object item) {
        if (item == null) return false;
        if (!iGunResolved) {
            iGunResolved = true;
            try {
                iGunClass = Class.forName(IGUN);
            } catch (Throwable t) {
                iGunClass = null;
                ViveTaCZClient.LOGGER.warn("[ViveTaCZ] TaCZ IGun class not found — VR gun rendering disabled.");
            }
        }
        return iGunClass != null && iGunClass.isInstance(item);
    }

    /** Write a (possibly private) field by name via reflection. */
    public static void setField(Object obj, String name, Object value) {
        if (obj == null) return;
        try {
            java.lang.reflect.Field f = vivetacz$findField(obj.getClass(), name);
            if (f != null) {
                f.setAccessible(true);
                f.set(obj, value);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Read a (possibly private) field by name from an object via reflection, or null. */
    public static Object getField(Object obj, String name) {
        if (obj == null) return null;
        try {
            java.lang.reflect.Field f = vivetacz$findField(obj.getClass(), name);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- Item identity ----------------------------------------------------
    private static Class<?> iAmmoClass;
    private static boolean  iAmmoResolved;

    /** True if the item is a TaCZ ammo item. */
    public static boolean isAmmo(Object item) {
        if (item == null) return false;
        if (!iAmmoResolved) {
            iAmmoResolved = true;
            try {
                iAmmoClass = Class.forName("com.tacz.guns.api.item.IAmmo");
            } catch (Throwable t) {
                iAmmoClass = null;
            }
        }
        return iAmmoClass != null && iAmmoClass.isInstance(item);
    }

    /** True if the given ammo stack is valid ammo for the given gun stack. */
    public static boolean isAmmoOfGun(Object ammoItem, Object ammoStack, Object gunStack) {
        if (ammoItem == null) return false;
        try {
            Method m = find(ammoItem.getClass(), "isAmmoOfGun", 2);
            return m != null && Boolean.TRUE.equals(m.invoke(ammoItem, ammoStack, gunStack));
        } catch (Throwable t) {
            return false;
        }
    }

    /** The gun id (e.g. "tacz:minigun") for a gun item stack, or null. */
    public static String getGunId(Object item, Object stack) {
        return idViaMethod(item, stack, "getGunId");
    }

    /** The ammo id for an ammo item stack, or null. */
    public static String getAmmoId(Object item, Object stack) {
        return idViaMethod(item, stack, "getAmmoId");
    }

    private static String idViaMethod(Object item, Object stack, String methodName) {
        if (item == null) return null;
        try {
            Method m = find(item.getClass(), methodName, 1);
            if (m == null) return null;
            Object id = m.invoke(item, stack);
            return id == null ? null : id.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- Animation driving ------------------------------------------------
    private static Class<?> timelessApiClass;
    private static Method   mGetGunDisplay;   // TimelessAPI.getGunDisplay(ItemStack) -> Optional
    private static boolean  displayApiResolved;
    private static boolean  displayApiOk;

    private static boolean resolveDisplayApi() {
        if (!displayApiResolved) {
            displayApiResolved = true;
            try {
                timelessApiClass = Class.forName(TIMELESS_API);
                for (Method m : timelessApiClass.getMethods()) {
                    if (m.getName().equals("getGunDisplay") && m.getParameterCount() == 1) {
                        mGetGunDisplay = m;
                        break;
                    }
                }
                displayApiOk = mGetGunDisplay != null;
                if (!displayApiOk) {
                    ViveTaCZClient.LOGGER.error("[ViveTaCZ] TimelessAPI.getGunDisplay not found — VR animation disabled.");
                }
            } catch (Throwable t) {
                displayApiOk = false;
            }
        }
        return displayApiOk;
    }

    /** TimelessAPI.getGunDisplay(stack).orElse(null) — the GunDisplayInstance, or null. */
    private static Object getDisplay(Object stack) {
        if (!resolveDisplayApi()) return null;
        try {
            Object opt = mGetGunDisplay.invoke(null, stack);
            if (opt == null) return null;
            Method orElse = opt.getClass().getMethod("orElse", Object.class);
            orElse.setAccessible(true);
            return orElse.invoke(opt, (Object) null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Advance the gun's animation state machine one step, mirroring what TaCZ
     * does inside renderFirstPerson: refresh the context, then update().
     * Progress is nanoTime-delta based, so being called once per eye is safe.
     *
     * @param renderer the GunItemRendererWrapper instance (mixin {@code this})
     * @return true if the animation was ticked (so the caller knows to clean up)
     */
    private static boolean tickDiagLogged = false;

    public static boolean tickAnimation(Object renderer, Object stack, Object player, float partialTick) {
        Object display = getDisplay(stack);
        if (display == null) {
            diagOnce("no GunDisplayInstance (getGunDisplay empty)");
            return false;
        }
        try {
            Object sm = invokeNoArg(display, "getAnimationStateMachine");
            if (sm == null) {
                diagOnce("display has no animationStateMachine");
                return false;
            }

            Method isInit = find(sm.getClass(), "isInitialized", 0);
            Object initialized = isInit != null ? isInit.invoke(sm) : "unknown";

            // sm.processContextIfExist(context -> renderer.updateContext(context, stack, player, partialTick))
            Method process   = find(sm.getClass(), "processContextIfExist", 1);
            Method updateCtx = find(renderer.getClass(), "updateContext", 4);
            if (process != null && updateCtx != null) {
                Consumer<Object> refresh = context -> {
                    try {
                        updateCtx.invoke(renderer, context, stack, player, partialTick);
                    } catch (Throwable ignored) {
                    }
                };
                process.invoke(sm, refresh);
            }

            Method update = find(sm.getClass(), "update", 0);
            if (update != null) {
                update.invoke(sm);
                diagOnce("update() invoked OK (sm.initialized=" + initialized
                        + ", process=" + (process != null) + ", updateContext=" + (updateCtx != null) + ")");
                vivetacz$reportActiveRunnersOnce(sm);
                return true;
            }
            diagOnce("no update() method on state machine " + sm.getClass().getName());
        } catch (Throwable t) {
            logTickFailureOnce(t);
        }
        return false;
    }

    private static void diagOnce(String msg) {
        if (!tickDiagLogged) {
            tickDiagLogged = true;
            ViveTaCZClient.LOGGER.info("[ViveTaCZ] animation tick diagnostic: {}", msg);
        }
    }

    // Logs once, the first time we catch the state machine with a live animation
    // runner (e.g. mid-reload). If this never fires while reloading, the keyframe
    // animation isn't reaching the state machine we're ticking.
    private static boolean activeRunnerLogged = false;
    private static void vivetacz$reportActiveRunnersOnce(Object sm) {
        if (activeRunnerLogged) return;
        try {
            Method getController = find(sm.getClass(), "getAnimationController", 0);
            if (getController == null) return;
            Object controller = getController.invoke(sm);
            if (controller == null) return;

            java.lang.reflect.Field f = vivetacz$findField(controller.getClass(), "currentRunners");
            if (f == null) return;
            f.setAccessible(true);
            Object runners = f.get(controller);
            if (!(runners instanceof java.util.List)) return;
            java.util.List<?> list = (java.util.List<?>) runners;

            int active = 0;
            for (Object runner : list) {
                if (runner == null) continue;
                Boolean running = vivetacz$boolCall(runner, "isRunning");
                Boolean trans   = vivetacz$boolCall(runner, "isTransitioning");
                if (Boolean.TRUE.equals(running) || Boolean.TRUE.equals(trans)) active++;
            }
            if (active > 0) {
                activeRunnerLogged = true;
                ViveTaCZClient.LOGGER.info("[ViveTaCZ] animation diagnostic: {} active runner(s) in the ticked state machine "
                        + "(keyframe animation IS reaching us).", active);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Boolean vivetacz$boolCall(Object target, String name) {
        try {
            Method m = find(target.getClass(), name, 0);
            return m == null ? null : (Boolean) m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static java.lang.reflect.Field vivetacz$findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    // ---- Gameplay: reload trigger + ammo/fire-mode readouts ---------------
    private static Class<?> opClass;
    private static Method   mFromLocalPlayer;   // static (ClientPlayerEntity) -> operator
    private static Method   mReload;            // operator.reload()
    private static boolean  opResolved;

    private static boolean resolveOperator() {
        if (!opResolved) {
            opResolved = true;
            try {
                opClass = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
                mFromLocalPlayer = null;
                for (Method m : opClass.getMethods()) {
                    if (m.getName().equals("fromLocalPlayer") && m.getParameterCount() == 1) {
                        mFromLocalPlayer = m;
                        break;
                    }
                }
                mReload = opClass.getMethod("reload");
            } catch (Throwable t) {
                mFromLocalPlayer = null;
                mReload = null;
            }
        }
        return mFromLocalPlayer != null && mReload != null;
    }

    /** Fire TaCZ's reload for the local player (no-op inside TaCZ if not applicable). */
    public static void triggerReload(Object player) {
        if (!resolveOperator()) return;
        try {
            Object operator = mFromLocalPlayer.invoke(null, player);
            if (operator != null) {
                mReload.invoke(operator);
            }
        } catch (Throwable t) {
            ViveTaCZClient.LOGGER.debug("[ViveTaCZ] reload trigger failed", t);
        }
    }

    /** Set the loaded ammo count on a gun stack (e.g. 0 when the mag is dropped). */
    public static void setCurrentAmmoCount(Object item, Object stack, int count) {
        try {
            Method m = find(item.getClass(), "setCurrentAmmoCount", 2);
            if (m != null) m.invoke(item, stack, count);
        } catch (Throwable ignored) {
        }
    }

    /** Current loaded ammo count for a gun stack, or -1 if unavailable. */
    public static int getCurrentAmmoCount(Object item, Object stack) {
        try {
            Method m = find(item.getClass(), "getCurrentAmmoCount", 1);
            if (m == null) return -1;
            Object v = m.invoke(item, stack);
            return v instanceof Integer ? (Integer) v : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Fire-mode label for a gun stack (e.g. "AUTO"), or empty string. */
    public static String getFireMode(Object item, Object stack) {
        try {
            Method m = find(item.getClass(), "getFireMode", 1);
            if (m == null) return "";
            Object v = m.invoke(item, stack);
            return v == null ? "" : v.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** True if this gun has a heat/overheat mechanic (e.g. the minigun). */
    public static boolean hasHeat(Object item, Object stack) {
        try {
            Method m = find(item.getClass(), "hasHeatData", 1);
            return m != null && Boolean.TRUE.equals(m.invoke(item, stack));
        } catch (Throwable t) {
            return false;
        }
    }

    /** True while the gun is locked from overheating. */
    public static boolean isOverheatLocked(Object item, Object stack) {
        try {
            Method m = find(item.getClass(), "isOverheatLocked", 1);
            return m != null && Boolean.TRUE.equals(m.invoke(item, stack));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Heat as a 0..1 fraction of the gun's max heat, or -1 if the gun has no heat data. */
    public static float getHeatPercent(Object item, Object stack) {
        try {
            if (!hasHeat(item, stack)) return -1f;
            Method getHeat = find(item.getClass(), "getHeatAmount", 1);
            if (getHeat == null) return -1f;
            float heat = (Float) getHeat.invoke(item, stack);
            float max = getHeatMax(item, stack);
            if (max <= 0f) return -1f;
            float pct = heat / max;
            return pct < 0f ? 0f : (pct > 1f ? 1f : pct);
        } catch (Throwable t) {
            return -1f;
        }
    }

    private static Method mGetClientGunIndex;
    private static boolean gunIndexResolved;

    private static float getHeatMax(Object item, Object stack) {
        try {
            Method getGunId = find(item.getClass(), "getGunId", 1);
            if (getGunId == null) return -1f;
            Object id = getGunId.invoke(item, stack);

            if (!gunIndexResolved) {
                gunIndexResolved = true;
                Class<?> timeless = Class.forName(TIMELESS_API);
                for (Method m : timeless.getMethods()) {
                    if (m.getName().equals("getClientGunIndex") && m.getParameterCount() == 1) {
                        mGetClientGunIndex = m;
                        break;
                    }
                }
            }
            if (mGetClientGunIndex == null) return -1f;

            Object opt = mGetClientGunIndex.invoke(null, id);
            Object index = (opt == null) ? null : opt.getClass().getMethod("orElse", Object.class).invoke(opt, (Object) null);
            if (index == null) return -1f;

            Object gunData = invokeNoArg(index, "getGunData");
            if (gunData == null) return -1f;
            Object heatData = invokeNoArg(gunData, "getHeatData");
            if (heatData == null) return -1f;
            Object max = invokeNoArg(heatData, "getHeatMax");
            return (max instanceof Float) ? (Float) max : -1f;
        } catch (Throwable t) {
            return -1f;
        }
    }

    // ---- Force high-poly (animation) -------------------------------------
    // method_3166 renders a STATIC LOD model instead of the animated high-poly
    // one unless RenderDistance.inRenderHighPolyModelDistance() is true. That
    // method short-circuits to true right after RenderDistance.markGuiRenderTimestamp()
    // (a 100ms window), so we call it each frame to force the animated model.
    private static Method mMarkGuiRender;
    private static boolean markResolved;

    public static void forceHighPolyModel() {
        if (!markResolved) {
            markResolved = true;
            try {
                Class<?> rd = Class.forName("com.tacz.guns.util.RenderDistance");
                mMarkGuiRender = rd.getMethod("markGuiRenderTimestamp");
            } catch (Throwable t) {
                mMarkGuiRender = null;
            }
        }
        if (mMarkGuiRender != null) {
            try {
                mMarkGuiRender.invoke(null);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Freeze the gun body: walk every model node and zero the animation transform
     * (offset + additional rotation) on all of them EXCEPT the actual moving parts
     * (magazine, bolt, slide, lid…). So no idle/run sway and the gun stays put
     * during reload while the mag/bolt still animate. Each gun names its body bone
     * after itself (e.g. "g17"), so a name-whitelist is the reliable approach.
     * Call after ticking, before rendering.
     */
    private static final String[] KEEP_ANIMATED = {
            "mag", "bolt", "slide", "lid", "cover", "charg", "bullet", "shell",
            "ammo", "clip", "hammer", "cock", "pull", "handle", "lever", "pump"
    };

    // Cached BedrockPart fields (same class for every node).
    private static java.lang.reflect.Field pfName, pfOffX, pfOffY, pfOffZ, pfAddQ, pfChildren;
    private static boolean partFieldsResolved;

    public static void neutralizeGunBodyMovement(Object gunStack) {
        try {
            Object display = getDisplay(gunStack);
            if (display == null) return;
            Object model = invokeNoArg(display, "getGunModel");
            if (model == null) return;
            Object root = invokeNoArg(model, "getRootNode");
            if (root == null) return;

            if (!partFieldsResolved) {
                partFieldsResolved = true;
                Class<?> c = root.getClass();
                pfName = c.getField("name");
                pfOffX = c.getField("offsetX");
                pfOffY = c.getField("offsetY");
                pfOffZ = c.getField("offsetZ");
                pfAddQ = c.getField("additionalQuaternion");
                pfChildren = c.getField("children");
            }
            freezeRecursive(root);
        } catch (Throwable ignored) {
        }
    }

    private static void freezeRecursive(Object node) {
        if (node == null || pfName == null) return;
        try {
            Object nameObj = pfName.get(node);
            boolean keep = false;
            if (nameObj != null) {
                String n = nameObj.toString().toLowerCase();
                for (String k : KEEP_ANIMATED) {
                    if (n.contains(k)) { keep = true; break; }
                }
            }
            if (!keep) {
                pfOffX.setFloat(node, 0f);
                pfOffY.setFloat(node, 0f);
                pfOffZ.setFloat(node, 0f);
                Object q = pfAddQ.get(node);
                if (q instanceof org.joml.Quaternionf) {
                    ((org.joml.Quaternionf) q).identity();
                }
            }
            Object children = pfChildren.get(node);
            if (children instanceof java.util.List) {
                for (Object child : (java.util.List<?>) children) {
                    freezeRecursive(child);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ---- Magazine node (the reload-animation mag geometry) ----------------

    // ---- Magazine node (the reload-animation mag geometry) ----------------

    /** The gun's BedrockGunModel object, or null. */
    private static Object getGunModelObject(Object gunStack) {
        Object display = getDisplay(gunStack);
        if (display == null) return null;
        try {
            return invokeNoArg(display, "getGunModel");
        } catch (Throwable t) {
            return null;
        }
    }

    /** The gun's dedicated magazine BedrockPart (the {@code magazineNode} field), or null. */
    public static Object getMagazineNode(Object gunStack) {
        Object model = getGunModelObject(gunStack);
        if (model == null) return null;
        try {
            java.lang.reflect.Field f = vivetacz$findField(model.getClass(), "magazineNode");
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(model);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean looksLikeMagName(String low) {
        // Skip mag-adjacent parts that aren't the magazine body (release button, the
        // extended/additional mag variants, refit previews, the loose bullets).
        if (low.contains("release") || low.contains("refit") || low.contains("view")
                || low.contains("additional") || low.contains("extended") || low.contains("bullet")
                || low.contains("hidden")) {
            return false;
        }
        // startsWith("mag") catches mag / magazine / mag_standard without matching "image"/"damage".
        return low.startsWith("mag")
                || low.contains("magazine")
                || low.contains("clip")
                || low.contains("cartridge")
                || low.contains("danjia"); // 弹夹 (magazine), some CN-authored models
    }

    /**
     * Best-effort magazine part: the dedicated {@code magazineNode} if the model has one,
     * else the additional mag node, else the first part in the model tree whose name looks
     * like a magazine. Many guns (e.g. the SCAR) leave {@code magazineNode} null and keep the
     * mag as an ordinary named child, so the tree search is what makes drop/ghost work for them.
     */
    public static Object findMagPart(Object gunStack) {
        Object node = getMagazineNode(gunStack);
        if (node != null) return node;

        Object model = getGunModelObject(gunStack);
        if (model == null) return null;
        try {
            Object addl = invokeNoArg(model, "getAdditionalMagazineNode");
            if (addl != null) return addl;
        } catch (Throwable ignored) {
        }
        try {
            Object root = invokeNoArg(model, "getRootNode");
            if (root != null) return searchPartByName(root, 0);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object searchPartByName(Object part, int depth) {
        if (part == null || depth > 12) return null;
        try {
            java.lang.reflect.Field fname = part.getClass().getField("name");
            Object nm = fname.get(part);
            if (nm instanceof String && looksLikeMagName(((String) nm).toLowerCase(java.util.Locale.ROOT))) {
                return part;
            }
            java.lang.reflect.Field fch = part.getClass().getField("children");
            Object ch = fch.get(part);
            if (ch instanceof java.util.List) {
                for (Object c : (java.util.List<?>) ch) {
                    Object r = searchPartByName(c, depth + 1);
                    if (r != null) return r;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Show/hide the gun's magazine node (for the "mag dropped" two-stage reload look). */
    public static void setMagazineVisible(Object gunStack, boolean visible) {
        Object node = findMagPart(gunStack);
        if (node == null) return;
        try {
            java.lang.reflect.Field f = vivetacz$findField(node.getClass(), "visible");
            if (f != null) {
                f.setAccessible(true);
                f.setBoolean(node, visible);
            }
        } catch (Throwable ignored) {
        }
    }

    /** The gun model's texture (net.minecraft.class_2960 / Identifier), or null. */
    public static Object getModelTexture(Object gunStack) {
        Object display = getDisplay(gunStack);
        if (display == null) return null;
        try {
            return invokeNoArg(display, "getModelTexture");
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Render a single gun BedrockPart (the magazine) standalone at the pose
     * stack's origin. Temporarily zeroes the part's local placement (so it isn't
     * offset by where it sits in the gun) and restores it right after.
     */
    public static void renderPart(Object node, Object poseStack, Object transformType, Object buffer, int light, int overlay) {
        try {
            Class<?> c = node.getClass();
            java.lang.reflect.Field fx = c.getField("x"), fy = c.getField("y"), fz = c.getField("z");
            java.lang.reflect.Field frx = c.getField("xRot"), fry = c.getField("yRot"), frz = c.getField("zRot");
            java.lang.reflect.Field fox = c.getField("offsetX"), foy = c.getField("offsetY"), foz = c.getField("offsetZ");
            java.lang.reflect.Field fvis = c.getField("visible");
            java.lang.reflect.Field faq = c.getField("additionalQuaternion");

            float sx = fx.getFloat(node), sy = fy.getFloat(node), sz = fz.getFloat(node);
            float srx = frx.getFloat(node), sry = fry.getFloat(node), srz = frz.getFloat(node);
            float sox = fox.getFloat(node), soy = foy.getFloat(node), soz = foz.getFloat(node);
            boolean svis = fvis.getBoolean(node);
            org.joml.Quaternionf q = (org.joml.Quaternionf) faq.get(node);
            org.joml.Quaternionf qSaved = new org.joml.Quaternionf(q);

            fx.setFloat(node, 0); fy.setFloat(node, 0); fz.setFloat(node, 0);
            frx.setFloat(node, 0); fry.setFloat(node, 0); frz.setFloat(node, 0);
            fox.setFloat(node, 0); foy.setFloat(node, 0); foz.setFloat(node, 0);
            fvis.setBoolean(node, true);
            q.identity();

            java.lang.reflect.Method render = find(c, "render", 5);
            if (render != null) {
                render.invoke(node, poseStack, transformType, buffer, light, overlay);
            }

            fx.setFloat(node, sx); fy.setFloat(node, sy); fz.setFloat(node, sz);
            frx.setFloat(node, srx); fry.setFloat(node, sry); frz.setFloat(node, srz);
            fox.setFloat(node, sox); foy.setFloat(node, soy); foz.setFloat(node, soz);
            fvis.setBoolean(node, svis);
            q.set(qSaved);
        } catch (Throwable t) {
            ViveTaCZClient.LOGGER.debug("[ViveTaCZ] magazine part render failed", t);
        }
    }

    /**
     * Like {@link #renderPart} but tinted/translucent when the gun's part model supports a
     * colored render (BedrockPart.render(..., r, g, b, a)). Falls back to the opaque 5-arg
     * render if no colored overload exists. Used to draw a ghost of the gun's own magazine
     * at the reload zone. Returns true if anything was rendered.
     */
    public static boolean renderPartColored(Object node, Object poseStack, Object transformType, Object buffer,
                                            int light, int overlay, float r, float g, float b, float a) {
        try {
            Class<?> c = node.getClass();
            java.lang.reflect.Field fx = c.getField("x"), fy = c.getField("y"), fz = c.getField("z");
            java.lang.reflect.Field frx = c.getField("xRot"), fry = c.getField("yRot"), frz = c.getField("zRot");
            java.lang.reflect.Field fox = c.getField("offsetX"), foy = c.getField("offsetY"), foz = c.getField("offsetZ");
            java.lang.reflect.Field fvis = c.getField("visible");
            java.lang.reflect.Field faq = c.getField("additionalQuaternion");

            float sx = fx.getFloat(node), sy = fy.getFloat(node), sz = fz.getFloat(node);
            float srx = frx.getFloat(node), sry = fry.getFloat(node), srz = frz.getFloat(node);
            float sox = fox.getFloat(node), soy = foy.getFloat(node), soz = foz.getFloat(node);
            boolean svis = fvis.getBoolean(node);
            org.joml.Quaternionf q = (org.joml.Quaternionf) faq.get(node);
            org.joml.Quaternionf qSaved = new org.joml.Quaternionf(q);

            fx.setFloat(node, 0); fy.setFloat(node, 0); fz.setFloat(node, 0);
            frx.setFloat(node, 0); fry.setFloat(node, 0); frz.setFloat(node, 0);
            fox.setFloat(node, 0); foy.setFloat(node, 0); foz.setFloat(node, 0);
            fvis.setBoolean(node, true);
            q.identity();

            boolean rendered = false;
            Method colored = find(c, "render", 9);
            if (colored != null) {
                try {
                    colored.invoke(node, poseStack, transformType, buffer, light, overlay, r, g, b, a);
                    rendered = true;
                } catch (Throwable colorFail) {
                    ViveTaCZClient.LOGGER.debug("[ViveTaCZ] colored mag render failed, trying opaque", colorFail);
                }
            }
            if (!rendered) {
                Method render = find(c, "render", 5);
                if (render != null) {
                    render.invoke(node, poseStack, transformType, buffer, light, overlay);
                    rendered = true;
                }
            }

            fx.setFloat(node, sx); fy.setFloat(node, sy); fz.setFloat(node, sz);
            frx.setFloat(node, srx); fry.setFloat(node, sry); frz.setFloat(node, srz);
            fox.setFloat(node, sox); foy.setFloat(node, soy); foz.setFloat(node, soz);
            fvis.setBoolean(node, svis);
            q.set(qSaved);
            return rendered;
        } catch (Throwable t) {
            ViveTaCZClient.LOGGER.debug("[ViveTaCZ] magazine ghost render failed", t);
            return false;
        }
    }

    /** Reset the gun model's per-frame animation transforms (as renderFirstPerson does after rendering). */
    public static void cleanAnimation(Object stack) {
        Object display = getDisplay(stack);
        if (display == null) return;
        try {
            Object model = invokeNoArg(display, "getGunModel");
            if (model == null) return;
            Method clean = find(model.getClass(), "cleanAnimationTransform", 0);
            if (clean != null) clean.invoke(model);
        } catch (Throwable ignored) {
        }
    }

    // ---- reflection helpers ----------------------------------------------
    private static Object invokeNoArg(Object target, String name) throws Exception {
        Method m = find(target.getClass(), name, 0);
        return m == null ? null : m.invoke(target);
    }

    private static Method find(Class<?> type, String name, int arity) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == arity) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static boolean loggedTickFailure = false;
    private static void logTickFailureOnce(Throwable t) {
        if (!loggedTickFailure) {
            loggedTickFailure = true;
            ViveTaCZClient.LOGGER.error("[ViveTaCZ] Animation tick failed (logged once)", t);
        }
    }
}
