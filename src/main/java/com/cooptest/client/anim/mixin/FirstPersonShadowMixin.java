package com.cooptest.client.anim.mixin;

import com.cooptest.client.anim.CoopAnimLib;
import com.cooptest.client.anim.FirstPersonMode;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class FirstPersonShadowMixin {
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void coopanim$noShadow(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Entity entity, float opacity, float tickDelta, WorldView world, float radius, CallbackInfo ci) {
        if (FirstPersonMode.isFirstPersonPass() && CoopAnimLib.isCameraPlayer(entity)) ci.cancel();
    }
}
