package com.cooptest.client.anim.mixin;

import com.cooptest.client.anim.FirstPersonMode;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.PlayerHeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only held items and armor render on the local player during the first-person pass. */
@Mixin(value = LivingEntityRenderer.class, priority = 2001)
public abstract class FirstPersonFeatureMixin {
    @WrapWithCondition(
            method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/feature/FeatureRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/Entity;FFFFFF)V"))
    private boolean coopanim$filterFeatures(FeatureRenderer<?, ?> feature, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, Entity entity, float a, float b, float c, float d, float e, float f) {
        if (entity instanceof ClientPlayerEntity && FirstPersonMode.isFirstPersonPass()) {
            return feature instanceof PlayerHeldItemFeatureRenderer || feature instanceof ArmorFeatureRenderer;
        }
        return true;
    }
}
