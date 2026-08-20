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
import com.vivetacz.client.VrRenderUtil;
import com.vivetacz.compat.TaczBridge;
import com.vivetacz.config.Placement;
import com.vivetacz.config.ViveTaczConfig;
import com.vivetacz.vr.VRStatus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The heart of ViveTaCZ.
 *
 * TaCZ's built-in item renderer ({@code method_3166}) intentionally renders
 * NOTHING for the first-person hand contexts — on desktop the first-person gun
 * is drawn by a separate camera-space event (which does not fire under
 * Vivecraft). Only the third-person / ground / fixed contexts actually draw the
 * gun model, using a real "held in a hand" placement.
 *
 * Vivecraft draws the held item by calling this renderer with
 * {@code FIRST_PERSON_RIGHT_HAND} and a PoseStack already positioned at the
 * controller — so nothing renders, and (with a naive fix) the camera-space
 * first-person path throws the gun off behind the head.
 *
 * ViveTaCZ intercepts the first-person-hand call and re-dispatches it as the
 * matching THIRD_PERSON hand context, which renders the gun with proper
 * in-hand placement relative to the controller PoseStack. Around that we tick
 * (and then clean) TaCZ's animation state machine so reload / fire / inspect
 * animations play — exactly as renderFirstPerson would, minus the camera math.
 *
 * Targeted by string because TaCZ's production jar cannot be a compile
 * dependency — see {@link TaczBridge}. The intermediary name method_3166 is used
 * for the same reason (Loom can't remap a string-targeted class's members).
 */
@Mixin(targets = "com.tacz.guns.client.renderer.item.GunItemRendererWrapper")
public abstract class GunItemRendererWrapperMixin {

    private static boolean vivetacz$loggedContext = false;

    // Guards against re-entering our own logic when we re-dispatch as THIRD_PERSON.
    private static boolean vivetacz$reentering = false;

    // True while we've forced the mag node hidden (dropped), so we know to restore it.
    private static boolean vivetacz$magHidden = false;

    // Placement is now live-tunable via the Mod Menu config screen — see
    // com.vivetacz.config.ViveTaczConfig / ViveTaczModMenu.

    @Inject(method = "method_3166", at = @At("HEAD"), cancellable = true)
    private void vivetacz$renderGunInVRHand(ItemStack stack,
                                            ModelTransformationMode transformType,
                                            MatrixStack poseStack,
                                            VertexConsumerProvider buffer,
                                            int light,
                                            int overlay,
                                            CallbackInfo ci) {
        if (vivetacz$reentering) return;                       // let the re-dispatched THIRD_PERSON pass through
        if (!VRStatus.isVRActive()) return;
        if (!vivetacz$isFirstPersonHand(transformType)) return;
        if (!TaczBridge.isGun(stack.getItem())) return;

        ViveTaczConfig cfg = ViveTaczConfig.get();
        if (!cfg.enabled) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        if (!vivetacz$loggedContext) {
            vivetacz$loggedContext = true;
            ViveTaCZClient.LOGGER.info("[ViveTaCZ] VR gun render active — re-dispatching {} as third-person hand.", transformType);
        }

        float partialTick = mc.getTickDelta();
        boolean ticked = TaczBridge.tickAnimation(this, stack, player, partialTick);
        // Optionally freeze the gun body so only mag/bolt parts animate (no sway).
        if (ticked && cfg.staticGunBody) {
            TaczBridge.neutralizeGunBodyMovement(stack);
        }
        // Two-stage reload: hide the gun's magazine while it's "dropped". The magazine node
        // lives on the shared per-gun model, so a one-way hide sticks invisible forever after
        // a reload (bug). Track that we hid it and restore visibility once it's no longer
        // dropped — without touching TaCZ's own visibility the rest of the time.
        if (com.vivetacz.gameplay.MagazineReload.isMagDropped()) {
            TaczBridge.setMagazineVisible(stack, false);
            vivetacz$magHidden = true;
        } else if (vivetacz$magHidden) {
            TaczBridge.setMagazineVisible(stack, true);
            vivetacz$magHidden = false;
        }
        // Force the animated high-poly model instead of the static LOD model.
        TaczBridge.forceHighPolyModel();

        // Per-gun placement override (e.g. the minigun), else the default gun placement.
        String gunId = TaczBridge.getGunId(stack.getItem(), stack);
        Placement placement = cfg.gunPlacement(gunId);

        ModelTransformationMode handMode = (transformType == ModelTransformationMode.FIRST_PERSON_LEFT_HAND)
                ? ModelTransformationMode.THIRD_PERSON_LEFT_HAND
                : ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;

        vivetacz$reentering = true;
        poseStack.push();
        try {
            VrRenderUtil.applyPlacement(poseStack, placement);
            // Calls method_3166 again with a hand context that actually renders the gun,
            // on the controller-positioned PoseStack Vivecraft provided.
            ((BuiltinModelItemRenderer) (Object) this).render(stack, handMode, poseStack, buffer, light, overlay);
        } finally {
            poseStack.pop();
            vivetacz$reentering = false;
        }

        if (ticked) {
            TaczBridge.cleanAnimation(stack);
        }

        // Floating HUD next to the gun: heat line (overheat guns only) on top, then ammo/fire-mode.
        if (cfg.hudEnabled) {
            vivetacz$renderHud(stack, poseStack, buffer, light, cfg);
        }

        ci.cancel();
    }

    private static boolean vivetacz$isFirstPersonHand(ModelTransformationMode mode) {
        return mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND
                || mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
    }

    private static void vivetacz$renderHud(ItemStack stack, MatrixStack poseStack,
                                           VertexConsumerProvider buffer, int light, ViveTaczConfig cfg) {
        Object item = stack.getItem();

        // Ammo line: "<count>  <fireMode>". Count matches TaCZ's own HUD exactly — mag plus the
        // chambered round, but only for closed/manual-bolt guns (open-bolt guns don't chamber).
        int ammo = TaczBridge.getDisplayAmmoCount(item, stack);
        String mode = TaczBridge.getFireMode(item, stack);
        StringBuilder ammoLine = new StringBuilder();
        if (ammo >= 0) ammoLine.append(ammo);
        if (!mode.isEmpty()) ammoLine.append(ammoLine.length() > 0 ? "  " : "").append(mode);

        // Heat bar (overheat guns only, e.g. the minigun) — shown above the ammo.
        float heat = TaczBridge.getHeatPercent(item, stack);
        boolean locked = heat >= 0f && TaczBridge.isOverheatLocked(item, stack);
        int heatColor = vivetacz$heatColor(Math.max(0f, heat));
        VrRenderUtil.drawGunHud(poseStack, buffer, cfg.hud, light, heat, locked, heatColor, ammoLine.toString());
    }

    /** Green → yellow → red as heat rises. */
    private static int vivetacz$heatColor(float pct) {
        int r = (int) (255 * Math.min(1f, pct * 2f));
        int g = (int) (255 * Math.min(1f, (1f - pct) * 2f));
        return 0xFF000000 | (r << 16) | (g << 8) | 0x30;
    }
}
