package com.cooptest.mixin.client.impactframemixin;

import com.cooptest.client.CoopImpactHandler;
import com.cooptest.fabricshim.api.EnvType;
import com.cooptest.fabricshim.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin({HeldItemRenderer.class})
public class HeldItemRendererImpactMixin {
    @ModifyVariable(
        method = {"renderFirstPersonItem"},
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private VertexConsumerProvider wrapProvider(VertexConsumerProvider original) {
        if (!CoopImpactHandler.playing) {
            return original;
        } else {
            boolean white = CoopImpactHandler.whiteFrame;
            return layer -> {
                final VertexConsumer base = original.getBuffer(layer);
                return new VertexConsumer() {
                    public VertexConsumer vertex(double x, double y, double z) {
                        base.vertex(x, y, z);
                        return this;
                    }

                    public VertexConsumer color(int r, int g, int b, int a) {
                        return white ? base.color(0, 0, 0, 255) : base.color(255, 255, 255, 255);
                    }

                    public VertexConsumer texture(float u, float v) {
                        base.texture(u, v);
                        return this;
                    }

                    public VertexConsumer overlay(int u, int v) {
                        base.overlay(u, v);
                        return this;
                    }

                    public VertexConsumer light(int u, int v) {
                        base.light(240, 240);
                        return this;
                    }

                    public VertexConsumer normal(float x, float y, float z) {
                        base.normal(x, y, z);
                        return this;
                    }

                    public void next() {
                        base.next();
                    }

                    public void fixedColor(int r, int g, int b, int a) {
                        base.fixedColor(r, g, b, a);
                    }

                    public void unfixColor() {
                        base.unfixColor();
                    }
                };
            };
        }
    }
}
