package com.cooptest.mixin.client.impactframemixin;

import com.cooptest.client.CoopImpactHandler;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({ModelPart.class})
public abstract class ImpactModelPartMixin {
    private static boolean shouldFlash() {
        return CoopImpactHandler.playing && CoopImpactHandler.renderingPlayer;
    }

    @ModifyVariable(
        method = {"render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"},
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private int forceLight(int light) {
        return shouldFlash() ? 15728880 : light;
    }

    @ModifyVariable(
        method = {"render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"},
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 1
    )
    private int forceOverlay(int overlay) {
        if (!shouldFlash()) {
            return overlay;
        } else {
            return switch (CoopImpactHandler.currentFrameType) {
                case BLACK, CYAN -> 0;
                default -> OverlayTexture.DEFAULT_UV;
            };
        }
    }

    @ModifyVariable(
        method = {"render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"},
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private VertexConsumer wrapConsumer(VertexConsumer original) {
        return !shouldFlash() ? original : new VertexConsumer() {
            public VertexConsumer vertex(double x, double y, double z) {
                original.vertex(x, y, z);
                return this;
            }

            public VertexConsumer color(int r, int g, int b, int a) {
                return switch (CoopImpactHandler.currentFrameType) {
                    case BLACK, CYAN -> original.color(255, 255, 255, 255);
                    case WHITE, RED -> original.color(0, 0, 0, 255);
                    case INVERT -> original.color(255 - r, 255 - g, 255 - b, a);
                };
            }

            public VertexConsumer texture(float u, float v) {
                original.texture(u, v);
                return this;
            }

            public VertexConsumer overlay(int u, int v) {
                original.overlay(u, v);
                return this;
            }

            public VertexConsumer light(int u, int v) {
                original.light(240, 240);
                return this;
            }

            public VertexConsumer normal(float x, float y, float z) {
                original.normal(x, y, z);
                return this;
            }

            public void next() {
                original.next();
            }

            public void fixedColor(int r, int g, int b, int a) {
                original.fixedColor(r, g, b, a);
            }

            public void unfixColor() {
                original.unfixColor();
            }
        };
    }
}
