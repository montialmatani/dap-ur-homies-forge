package com.cooptest.client.anim.mixin;

import com.cooptest.client.anim.CoopAnimLib;
import com.cooptest.client.anim.PlayerAnimationController;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Advances the animation for this frame and applies the whole-body bone (PAL PlayerRendererMixin). */
@Mixin(value = PlayerEntityRenderer.class, priority = 2000)
public abstract class AnimPlayerRendererMixin {
    @Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V", at = @At("RETURN"))
    private void coopanim$body(AbstractClientPlayerEntity player, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, CallbackInfo ci) {
        CoopAnimLib.process(player, tickDelta);
        PlayerAnimationController c = CoopAnimLib.active(player);
        if (c != null) CoopAnimLib.applyBody(c, matrices);
    }
}
