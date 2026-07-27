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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/** Shared helpers for the VR item render mixins. */
public final class VrRenderUtil {

    private VrRenderUtil() {}

    /** Base world-space scale for the HUD (1 px ≈ this many blocks before the config scale). */
    private static final float TEXT_BASE_SCALE = 0.02f;

    /**
     * Apply a {@link Placement} (rotation → offset → scale) to the pose stack,
     * on top of the controller-positioned matrix Vivecraft provides.
     */
    public static void applyPlacement(MatrixStack poseStack, Placement p) {
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.yaw));
        poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.pitch));
        poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.roll));
        poseStack.translate(p.posX, p.posY, p.posZ);
        if (p.scale != 1.0f && p.scale > 0.0f) {
            poseStack.scale(p.scale, p.scale, p.scale);
        }
    }

    /**
     * Draw the gun HUD near the gun: an optional heat bar (+ % / OVERHEAT) on top,
     * then the ammo/fire-mode line. Everything lives in the same "pixel" space so
     * it stacks cleanly; the config {@link Placement} aims and scales it.
     *
     * @param heatPercent 0..1, or negative to omit the heat bar entirely
     */
    public static void drawGunHud(MatrixStack poseStack, VertexConsumerProvider buffer, Placement hud, int light,
                                  float heatPercent, boolean heatLocked, int heatColor, String ammoText) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;

        poseStack.push();
        applyPlacement(poseStack, hud);
        // Text/quads are authored 1 unit = 1 px, Y-down; shrink and flip into world space.
        poseStack.scale(-TEXT_BASE_SCALE, -TEXT_BASE_SCALE, TEXT_BASE_SCALE);
        Matrix4f m = poseStack.peek().getPositionMatrix();

        float y = 0f;

        if (heatPercent >= 0f) {
            final float barW = 60f, barH = 6f, x0 = -barW / 2f;
            VertexConsumer vc = buffer.getBuffer(RenderLayer.getDebugQuads());
            fillRect(vc, m, x0 - 1f, y - 1f, barW + 2f, barH + 2f, 0xFF101010); // frame
            fillRect(vc, m, x0, y, barW, barH, 0xFF3A3A3A);                       // track
            float fill = barW * clamp01(heatPercent);
            fillRect(vc, m, x0, y, fill, barH, heatColor);                        // fill
            y += barH + 3f;

            String heatText = heatLocked ? "OVERHEAT!" : (Math.round(heatPercent * 100f) + "%");
            int textColor = heatLocked ? 0xFFFF3030 : heatColor;
            drawCentered(tr, m, buffer, heatText, y, textColor, light);
            y += tr.fontHeight + 2f;
        }

        if (ammoText != null && !ammoText.isEmpty()) {
            drawCentered(tr, m, buffer, ammoText, y, 0xFFFFFFFF, light);
        }

        poseStack.pop();
    }

    private static void drawCentered(TextRenderer tr, Matrix4f m, VertexConsumerProvider buffer,
                                     String text, float y, int color, int light) {
        float w = tr.getWidth(text);
        tr.draw(text, -w / 2f, y, color, false, m, buffer, TextRenderer.TextLayerType.NORMAL, 0, light);
    }

    private static void fillRect(VertexConsumer vc, Matrix4f m, float x, float y, float w, float h, int argb) {
        vc.vertex(m, x, y, 0f).color(argb).next();
        vc.vertex(m, x, y + h, 0f).color(argb).next();
        vc.vertex(m, x + w, y + h, 0f).color(argb).next();
        vc.vertex(m, x + w, y, 0f).color(argb).next();
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
