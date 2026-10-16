package com.cooptest.client.anim.mixin;

import com.cooptest.client.anim.CoopAnimLib;
import com.cooptest.client.anim.FirstPersonConfiguration;
import com.cooptest.client.anim.FirstPersonMode;
import com.cooptest.client.anim.PlayerAnimationController;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Port of PAL's PlayerModelMixin (priority 2001 so it runs after the mod's own pose mixin). */
@Mixin(value = PlayerEntityModel.class, priority = 2001)
public abstract class AnimPlayerModelMixin<T extends LivingEntity> extends BipedEntityModel<T> {
    @Shadow @Final public ModelPart leftSleeve;
    @Shadow @Final public ModelPart rightSleeve;
    @Shadow @Final public ModelPart leftPants;
    @Shadow @Final public ModelPart rightPants;
    @Shadow @Final public ModelPart jacket;

    public AnimPlayerModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void coopanim$reset(T entity, float f, float g, float h, float i, float j, CallbackInfo ci) {
        this.head.resetTransform();
        this.body.resetTransform();
        this.rightArm.resetTransform();
        this.leftArm.resetTransform();
        this.rightLeg.resetTransform();
        this.leftLeg.resetTransform();
        this.hat.resetTransform();
        this.jacket.resetTransform();
        this.rightSleeve.resetTransform();
        this.leftSleeve.resetTransform();
        this.rightPants.resetTransform();
        this.leftPants.resetTransform();
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void coopanim$apply(T entity, float f, float g, float h, float i, float j, CallbackInfo ci) {
        PlayerAnimationController c = CoopAnimLib.active(entity);
        if (c != null) {
            CoopAnimLib.updatePart(c, "head", this.head, this.hat);
            CoopAnimLib.updatePart(c, "right_arm", this.rightArm, this.rightSleeve);
            CoopAnimLib.updatePart(c, "left_arm", this.leftArm, this.leftSleeve);
            CoopAnimLib.updatePart(c, "right_leg", this.rightLeg, this.rightPants);
            CoopAnimLib.updatePart(c, "left_leg", this.leftLeg, this.leftPants);
            CoopAnimLib.updatePart(c, "torso", this.body, this.jacket);
        }
        if (FirstPersonMode.isFirstPersonPass() && CoopAnimLib.isCameraPlayer(entity)) {
            FirstPersonConfiguration config = c != null ? c.getFirstPersonConfiguration() : FirstPersonConfiguration.DEFAULT;
            this.setVisible(false);
            this.jacket.visible = false;
            this.leftPants.visible = false;
            this.rightPants.visible = false;
            this.rightArm.visible = config.isShowRightArm();
            this.leftArm.visible = config.isShowLeftArm();
            this.rightSleeve.visible = config.isShowRightArm();
            this.leftSleeve.visible = config.isShowLeftArm();
        }
    }
}
