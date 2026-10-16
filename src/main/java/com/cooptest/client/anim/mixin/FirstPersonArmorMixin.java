package com.cooptest.client.anim.mixin;

import com.cooptest.client.anim.CoopAnimLib;
import com.cooptest.client.anim.FirstPersonConfiguration;
import com.cooptest.client.anim.FirstPersonMode;
import com.cooptest.client.anim.PlayerAnimationController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ArmorFeatureRenderer.class, priority = 2000)
public abstract class FirstPersonArmorMixin<T extends LivingEntity, M extends BipedEntityModel<T>, A extends BipedEntityModel<T>> {
    @Inject(method = "setVisible", at = @At("HEAD"), cancellable = true)
    private void coopanim$armorVisibility(A model, EquipmentSlot slot, CallbackInfo ci) {
        if (!FirstPersonMode.isFirstPersonPass()) return;
        PlayerAnimationController c = CoopAnimLib.active(MinecraftClient.getInstance().player);
        if (c == null || c.getFirstPersonMode() != FirstPersonMode.THIRD_PERSON_MODEL) return;
        FirstPersonConfiguration config = c.getFirstPersonConfiguration();
        model.setVisible(false);
        if (slot == EquipmentSlot.CHEST && config.isShowArmor()) {
            model.rightArm.visible = config.isShowRightArm();
            model.leftArm.visible = config.isShowLeftArm();
        }
        ci.cancel();
    }
}
