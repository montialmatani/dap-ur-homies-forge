package com.cooptest.mixin.client;

import com.cooptest.client.CoopCameraShakeHandler;
import com.cooptest.fabricshim.api.EnvType;
import com.cooptest.fabricshim.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({GameRenderer.class})
public class CoopCameraRollMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(
        method = {"renderWorld"},
        at = {@At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V"
        )}
    )
    private void coopApplyRoll(float tickDelta, long limitTime, MatrixStack matrixStack, CallbackInfo ci) {
        if (CoopCameraShakeHandler.isActive()) {
            if (this.client.player != null) {
                matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(CoopCameraShakeHandler.rollOffset));
            }
        }
    }
}
