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

import com.vivetacz.client.VrRenderUtil;
import com.vivetacz.compat.TaczBridge;
import com.vivetacz.config.ViveTaczConfig;
import com.vivetacz.vr.VRStatus;
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
 * VR placement for TaCZ ammo items (magazines, ammo boxes), mirroring the gun
 * renderer's approach: when Vivecraft draws a held ammo item in a first-person
 * hand context, re-dispatch it as the matching third-person hand context (so it
 * uses the proper in-hand placement) and apply the configurable ammo placement.
 *
 * Ammo models are static (no animation state machine, no LOD swap), so this is
 * simpler than the gun mixin.
 */
@Mixin(targets = "com.tacz.guns.client.renderer.item.AmmoItemRenderer")
public abstract class AmmoItemRendererMixin {

    private static boolean vivetacz$ammoReentering = false;

    @Inject(method = "method_3166", at = @At("HEAD"), cancellable = true)
    private void vivetacz$renderAmmoInVRHand(ItemStack stack,
                                             ModelTransformationMode transformType,
                                             MatrixStack poseStack,
                                             VertexConsumerProvider buffer,
                                             int light,
                                             int overlay,
                                             CallbackInfo ci) {
        if (vivetacz$ammoReentering) return;
        if (!VRStatus.isVRActive()) return;
        if (!vivetacz$isFirstPersonHand(transformType)) return;
        if (!TaczBridge.isAmmo(stack.getItem())) return;

        ViveTaczConfig cfg = ViveTaczConfig.get();
        if (!cfg.enabled) return;

        ModelTransformationMode handMode = (transformType == ModelTransformationMode.FIRST_PERSON_LEFT_HAND)
                ? ModelTransformationMode.THIRD_PERSON_LEFT_HAND
                : ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;

        vivetacz$ammoReentering = true;
        poseStack.push();
        try {
            VrRenderUtil.applyPlacement(poseStack, cfg.ammo);
            ((BuiltinModelItemRenderer) (Object) this).render(stack, handMode, poseStack, buffer, light, overlay);
        } finally {
            poseStack.pop();
            vivetacz$ammoReentering = false;
        }
        ci.cancel();
    }

    private static boolean vivetacz$isFirstPersonHand(ModelTransformationMode mode) {
        return mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND
                || mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
    }
}
