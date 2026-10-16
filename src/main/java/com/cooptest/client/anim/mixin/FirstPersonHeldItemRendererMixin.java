package com.cooptest.client.anim.mixin;

import com.cooptest.client.anim.CoopAnimLib;
import com.cooptest.client.anim.FirstPersonConfiguration;
import com.cooptest.client.anim.FirstPersonMode;
import com.cooptest.client.anim.PlayerAnimationController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PAL ItemInHandRendererMixin: hide vanilla first-person hands while the third-person model is shown. */
@Mixin(HeldItemRenderer.class)
public abstract class FirstPersonHeldItemRendererMixin {
    @Shadow private float equipProgressMainHand;
    @Shadow private float equipProgressOffHand;
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"), cancellable = true)
    private void coopanim$hideVanillaHands(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, ClientPlayerEntity player, int light, CallbackInfo ci) {
        if (CoopAnimLib.thirdPersonModelActive(player)) ci.cancel();
    }

    @Inject(method = "updateHeldItems", at = @At("TAIL"))
    private void coopanim$lowerHands(CallbackInfo ci) {
        if (CoopAnimLib.thirdPersonModelActive(this.client.player)) {
            float progress = 0.4f; // fully transitioned (PAL default transition length is 0)
            this.equipProgressMainHand *= progress;
            this.equipProgressOffHand *= progress;
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"), cancellable = true)
    private void coopanim$hideItems(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!CoopAnimLib.isCameraPlayer(entity) || !FirstPersonMode.isFirstPersonPass()) return;
        PlayerAnimationController c = CoopAnimLib.active(entity);
        FirstPersonConfiguration config = c != null ? c.getFirstPersonConfiguration() : FirstPersonConfiguration.DEFAULT;
        boolean right = mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
        if (right ? !config.isShowRightItem() : !config.isShowLeftItem()) ci.cancel();
    }
}
