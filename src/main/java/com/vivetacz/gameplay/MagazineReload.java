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
import com.vivetacz.config.Placement;
import com.vivetacz.config.ViveTaczConfig;
import com.vivetacz.vr.VivecraftBridge;
import com.vivetacz.vr.VRStatus;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Physical magazine reload.
 *
 * A magazine (rendered as the gun's matching ammo box) sits at a body pouch.
 * Reach your off hand to the pouch to "grab" it (it then follows your hand);
 * bring it up to the gun to seat it, which triggers TaCZ's reload.
 *
 * State + the resolved ammo stack are computed on the client tick; the world
 * render callback re-reads the live hand/body poses so the mag tracks smoothly.
 */
public final class MagazineReload {

    private static boolean held = false;      // mag currently in the off hand
    private static boolean active = false;    // a gun is held and a mag is available
    private static boolean magDropped = false; // two-stage: old mag ejected, ready to insert
    private static ItemStack magStack = ItemStack.EMPTY;
    private static long lastReloadMs = 0L;    // for the grab cooldown

    // The gun (hotbar slot + id) the carried mag was grabbed for; switching guns drops it.
    private static int lastSlot = -1;
    private static String lastGunKey = "";

    private MagazineReload() {}

    /** True while the two-stage reload has the mag ejected (so the gun renders empty). */
    public static boolean isMagDropped() {
        return magDropped;
    }

    /** True while a magazine is being carried in the off hand (so two-hand grip is suppressed). */
    public static boolean isHoldingMag() {
        return held;
    }

    /** Shared helper: world point offset from the gun hand in its own frame. */
    public static Vec3d gunHandAnchor(VivecraftBridge bridge, Placement zone) {
        return magAnchor(bridge, zone);
    }

    /** Empty the gun's loaded ammo (client visual + authoritative on the integrated server). */
    private static void emptyGun(MinecraftClient mc, ClientPlayerEntity player, ItemStack gun) {
        TaczBridge.setCurrentAmmoCount(gun.getItem(), gun, 0);
        net.minecraft.server.integrated.IntegratedServer server = mc.getServer();
        if (server != null) {
            java.util.UUID uuid = player.getUuid();
            server.execute(() -> {
                net.minecraft.server.network.ServerPlayerEntity sp = server.getPlayerManager().getPlayer(uuid);
                if (sp != null) {
                    ItemStack g = sp.getMainHandStack();
                    if (g != null && TaczBridge.isGun(g.getItem())) {
                        TaczBridge.setCurrentAmmoCount(g.getItem(), g, 0);
                    }
                }
            });
        }
    }

    /** Close only Vivecraft's own hotbar/radial screen — never vanilla menus. */
    public static void closeVrHotbar(MinecraftClient mc) {
        if (mc.currentScreen == null) return;
        String cls = mc.currentScreen.getClass().getName().toLowerCase();
        if (cls.contains("vivecraft")) {
            mc.setScreen(null);
        }
    }

    // ------------------------------------------------------------------ tick
    public static void onClientTick(MinecraftClient mc) {
        ViveTaczConfig cfg = ViveTaczConfig.get();
        active = false;

        if (!cfg.enabled || !cfg.magReloadEnabled || !VRStatus.isVRActive()) {
            held = false; magDropped = false;
            return;
        }
        ClientPlayerEntity player = mc.player;
        if (player == null) { held = false; magDropped = false; return; }

        ItemStack gun = player.getMainHandStack();
        if (gun == null || !TaczBridge.isGun(gun.getItem())) { held = false; magDropped = false; return; }

        // A carried mag belongs to the gun you grabbed it for. If you switch hotbar slots or
        // to a different gun, drop it (it vanishes from the off hand) so a wrong-gun mag never
        // carries over. Checked before the magazine-fed gate so switching to e.g. the minigun
        // still clears a previously held mag.
        int slot = player.getInventory().selectedSlot;
        String gunKey = TaczBridge.getGunId(gun.getItem(), gun);
        if (gunKey == null) gunKey = "";
        if (slot != lastSlot || !gunKey.equals(lastGunKey)) {
            held = false;
            magDropped = false;
            lastSlot = slot;
            lastGunKey = gunKey;
        }

        // Only magazine-fed guns get the physical mag drop/insert. Inventory-fed guns like the
        // minigun have no removable mag, and forcing their ammo to 0 corrupts them into
        // single-shot — so we leave them entirely to TaCZ's own reload.
        if (!TaczBridge.isMagazineFed(gun.getItem(), gun)) {
            held = false; magDropped = false; active = false;
            return;
        }

        magStack = ammoBoxStack();
        if (magStack.isEmpty()) { held = false; return; }
        active = true;

        VivecraftBridge bridge = VRStatus.bridge();
        Vec3d offHand = bridge.getOffHandPos();
        Vec3d gunHand = bridge.getMainHandPos();
        if (offHand == null || gunHand == null) return;

        String gunId = TaczBridge.getGunId(gun.getItem(), gun);

        // Two-stage: the "Drop Magazine" key ejects the current mag first (and empties it).
        if (cfg.twoStageReload && com.vivetacz.ViveTaCZClient.DROP_MAG != null) {
            while (com.vivetacz.ViveTaCZClient.DROP_MAG.wasPressed()) {
                if (!magDropped) {
                    magDropped = true;
                    bridge.triggerHaptic(0.05f, 120.0f, 0.5f);
                    emptyGun(mc, player, gun);
                    com.vivetacz.debug.DebugLog.logf("RELOAD", "DROP gun=%s ammo->0", gunId);
                }
            }
        }

        if (!held) {
            // Grab: off hand reaches the mag sitting next to the gun (respect cooldown).
            long since = System.currentTimeMillis() - lastReloadMs;
            boolean offCooldown = since >= (long) (cfg.magCooldownSeconds * 1000f);
            Vec3d anchor = magAnchor(bridge, cfg.pouch);
            if (offCooldown && anchor != null && offHand.distanceTo(anchor) <= cfg.magGrabDistance) {
                held = true;
                com.vivetacz.debug.DebugLog.logf("RELOAD", "GRAB gun=%s off=%s anchor=%s", gunId,
                        com.vivetacz.debug.DebugLog.v(offHand), com.vivetacz.debug.DebugLog.v(anchor));
            }
        } else {
            // Insert: bring the held mag to the gun's (per-gun) reload zone → reload.
            Vec3d zone = magAnchor(bridge, cfg.reloadZoneFor(gunId));
            if (zone != null && offHand.distanceTo(zone) <= cfg.reloadGestureDistance) {
                // In two-stage mode, only reload once the old mag has been dropped — except
                // open-bolt guns (RPG, break-action, single-shot) don't chamber, so they skip
                // the drop step and reload on a plain insert.
                boolean openBolt = TaczBridge.isOpenBolt(gun.getItem(), gun);
                if (!cfg.twoStageReload || magDropped || openBolt) {
                    TaczBridge.triggerReload(player);
                    closeVrHotbar(mc);
                    lastReloadMs = System.currentTimeMillis();
                    magDropped = false;
                    held = false;
                    com.vivetacz.debug.DebugLog.logf("RELOAD", "INSERT gun=%s zone=%s off=%s", gunId,
                            com.vivetacz.debug.DebugLog.v(zone), com.vivetacz.debug.DebugLog.v(offHand));
                }
            }
        }
    }

    // ---------------------------------------------------------------- render
    public static void render(WorldRenderContext ctx) {
        if (!active) return;
        VertexConsumerProvider consumers = ctx.consumers();
        if (consumers == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        ViveTaczConfig cfg = ViveTaczConfig.get();
        VivecraftBridge bridge = VRStatus.bridge();
        Vec3d cam = ctx.camera().getPos();
        MatrixStack ms = ctx.matrixStack();

        // Show where the "insert the mag" zone is (green once the mag is dropped).
        if (cfg.showReloadZone) {
            ItemStack heldGun = player.getMainHandStack();
            String gunId = (heldGun != null && TaczBridge.isGun(heldGun.getItem()))
                    ? TaczBridge.getGunId(heldGun.getItem(), heldGun) : null;
            Vec3d zone = magAnchor(bridge, cfg.reloadZoneFor(gunId));
            if (zone != null) {
                boolean ready = !cfg.twoStageReload || magDropped;
                // Prefer a translucent ghost of the gun's own magazine (its real shape);
                // fall back to the plain marker for internal-mag guns / no model.
                if (!drawMagGhost(consumers, ms, heldGun, bridge, zone, cam, ready)) {
                    drawZoneMarker(consumers, ms, zone, cam, (float) cfg.reloadGestureDistance, ready);
                }
            }
        }

        if (held) {
            // Held → the gun's own reload magazine, in the off hand.
            ItemStack gun = player.getMainHandStack();
            Object node = (gun != null && TaczBridge.isGun(gun.getItem()))
                    ? TaczBridge.findMagPart(gun) : null;
            Object texObj = (node != null) ? TaczBridge.getModelTexture(gun) : null;
            Vec3d off = bridge.getOffHandPos();
            if (node != null && texObj instanceof Identifier && off != null) {
                ms.push();
                ms.translate(off.x - cam.x, off.y - cam.y, off.z - cam.z);
                applyHandRotation(ms, bridge.getOffHandRotation());
                applyPlacement(ms, cfg.magazine);
                VertexConsumer vc = consumers.getBuffer(
                        RenderLayer.getEntityCutoutNoCull((Identifier) texObj));
                TaczBridge.renderPart(node, ms, ModelTransformationMode.NONE, vc,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
                ms.pop();
                return;
            }
            // No mag node (e.g. internal-mag guns) → fall back to the box in hand.
            renderBox(mc, consumers, player, ms, off, bridge.getOffHandRotation(), cam, cfg.pouch);
            return;
        }

        // Stowed → the ammo box sitting next to the gun.
        renderBox(mc, consumers, player, ms, magAnchor(bridge, cfg.pouch),
                bridge.getMainHandRotation(), cam, cfg.pouch);
    }

    private static void renderBox(MinecraftClient mc, VertexConsumerProvider consumers,
                                  ClientPlayerEntity player, MatrixStack ms,
                                  Vec3d pos, Object handRot, Vec3d cam, Placement placement) {
        if (pos == null || magStack.isEmpty()) return;
        ms.push();
        ms.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        applyHandRotation(ms, handRot);
        applyPlacement(ms, placement);
        mc.getItemRenderer().renderItem(magStack, ModelTransformationMode.GROUND,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV,
                ms, consumers, player.getWorld(), 0);
        ms.pop();
    }

    // --- transform helpers -------------------------------------------------
    private static void applyHandRotation(MatrixStack ms, Object handRot) {
        if (handRot instanceof org.joml.Quaternionfc) {
            org.joml.Quaternionfc q = (org.joml.Quaternionfc) handRot;
            ms.peek().getPositionMatrix().rotate(q);
            ms.peek().getNormalMatrix().rotate(q);
        }
    }

    private static void applyPlacement(MatrixStack ms, Placement p) {
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.yaw));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.pitch));
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.roll));
        if (p.scale > 0f) ms.scale(p.scale, p.scale, p.scale);
    }

    /**
     * Draw a translucent ghost of the gun's own magazine at the reload zone, so the marker
     * has the exact shape of the mag you're inserting. Green tint when ready to insert.
     * Returns false (→ caller falls back to the plain marker) if the gun has no mag model.
     */
    private static boolean loggedGhost = false;

    private static boolean drawMagGhost(VertexConsumerProvider consumers, MatrixStack ms, ItemStack gun,
                                        VivecraftBridge bridge, Vec3d zone, Vec3d cam, boolean ready) {
        if (gun == null || !TaczBridge.isGun(gun.getItem())) return false;
        Object node = TaczBridge.findMagPart(gun);
        Object texObj = (node != null) ? TaczBridge.getModelTexture(gun) : null;
        if (!loggedGhost) {
            loggedGhost = true;
            com.vivetacz.ViveTaCZClient.LOGGER.info("[ViveTaCZ] mag ghost probe: magNode={} tex={}", node, texObj);
        }
        if (node == null || !(texObj instanceof Identifier)) return false;

        ViveTaczConfig cfg = ViveTaczConfig.get();
        ms.push();
        ms.translate(zone.x - cam.x, zone.y - cam.y, zone.z - cam.z);
        applyHandRotation(ms, bridge.getMainHandRotation());
        applyPlacement(ms, cfg.magazine);
        // Translucent, unlit so it reads as a ghost. Green when ready, white otherwise.
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucentCull((Identifier) texObj));
        float r = ready ? 0.45f : 0.85f;
        float g = ready ? 1.0f : 0.9f;
        float b = ready ? 0.5f : 0.95f;
        boolean ok = TaczBridge.renderPartColored(node, ms, ModelTransformationMode.NONE, vc,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, r, g, b, 0.45f);
        ms.pop();
        return ok;
    }

    /** A translucent camera-facing square marking the insert zone (green = ready). */
    private static void drawZoneMarker(VertexConsumerProvider consumers, MatrixStack ms,
                                       Vec3d pos, Vec3d cam, float radius, boolean ready) {
        float half = Math.max(0.03f, radius);
        double px = pos.x - cam.x, py = pos.y - cam.y, pz = pos.z - cam.z;
        org.joml.Matrix4f m = ms.peek().getPositionMatrix();
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getDebugQuads());
        int argb = ready ? 0x5540FF60 : 0x40FFFFFF; // faint green when ready, faint white otherwise
        // Two camera-ish facing quads (XY and XZ) so it reads as a blob from any angle.
        quad(vc, m, px, py, pz, half, argb, true);
        quad(vc, m, px, py, pz, half, argb, false);
    }

    private static void quad(VertexConsumer vc, org.joml.Matrix4f m, double x, double y, double z,
                             float h, int argb, boolean vertical) {
        if (vertical) {
            vertexC(vc, m, x - h, y - h, z, argb);
            vertexC(vc, m, x - h, y + h, z, argb);
            vertexC(vc, m, x + h, y + h, z, argb);
            vertexC(vc, m, x + h, y - h, z, argb);
        } else {
            vertexC(vc, m, x - h, y, z - h, argb);
            vertexC(vc, m, x - h, y, z + h, argb);
            vertexC(vc, m, x + h, y, z + h, argb);
            vertexC(vc, m, x + h, y, z - h, argb);
        }
    }

    private static void vertexC(VertexConsumer vc, org.joml.Matrix4f m, double x, double y, double z, int argb) {
        vc.vertex(m, (float) x, (float) y, (float) z).color(argb).next();
    }

    // --------------------------------------------------------------- helpers
    /**
     * World position of the stowed mag, next to the gun and tracked from the
     * main (gun) hand. The offset is expressed in the gun hand's own frame, so
     * the mag stays put relative to the gun as you move and aim it.
     */
    private static Vec3d magAnchor(VivecraftBridge bridge, Placement pouch) {
        Vec3d hand = bridge.getMainHandPos();
        if (hand == null) return null;
        org.joml.Vector3f off = new org.joml.Vector3f(pouch.posX, pouch.posY, pouch.posZ);
        Object rot = bridge.getMainHandRotation();
        if (rot instanceof org.joml.Quaternionfc) {
            ((org.joml.Quaternionfc) rot).transform(off);   // gun-hand-local → world
        }
        return hand.add(off.x, off.y, off.z);
    }

    /** A cached TaCZ ammo-box stack used as the physical magazine visual. */
    private static ItemStack ammoBoxStack;

    private static ItemStack ammoBoxStack() {
        if (ammoBoxStack == null) {
            Item item = Registries.ITEM.get(new Identifier("tacz", "ammo_box"));
            ammoBoxStack = (item == Items.AIR) ? ItemStack.EMPTY : new ItemStack(item);
        }
        return ammoBoxStack;
    }
}
