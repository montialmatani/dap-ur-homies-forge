package com.cooptest.mixin;

import com.cooptest.ArmPoseTracker;
import com.cooptest.GrabInputHandler;
import com.cooptest.GrabMechanic;
import com.cooptest.HighFiveHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.cooptest.client.CatchClientHandler;
import com.cooptest.client.ChargedDapClientHandler;
import com.cooptest.client.PushClientHandler;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({PlayerEntityModel.class})
public class PlayerModelMixin<T extends LivingEntity> {
    @Unique
    private static final float ANIMATION_SPEED = 0.2F;
    @Unique
    private static final float FAST_ANIMATION_SPEED = 0.35F;
    @Unique
    private static final float GRAB_READY_ARM_PITCH = -70.0F;
    @Unique
    private static final float GRAB_READY_ARM_YAW = 5.0F;
    @Unique
    private static final float GRAB_READY_ARM_ROLL = 0.0F;
    @Unique
    private static final float HOLD_RIGHT_ARM_PITCH = -110.0F;
    @Unique
    private static final float HOLD_RIGHT_ARM_YAW = -10.0F;
    @Unique
    private static final float HOLD_RIGHT_ARM_ROLL = 0.0F;
    @Unique
    private static final float HOLD_LEFT_ARM_PITCH = 10.0F;
    @Unique
    private static final float HOLD_LEFT_ARM_YAW = 10.0F;
    @Unique
    private static final float HOLD_LEFT_ARM_ROLL = 0.0F;
    @Unique
    private static final float CHARGE_RIGHT_ARM_PITCH = -150.0F;
    @Unique
    private static final float CHARGE_RIGHT_ARM_YAW = 10.0F;
    @Unique
    private static final float CHARGE_RIGHT_ARM_ROLL = 0.0F;
    @Unique
    private static final float CHARGE_LEFT_ARM_PITCH = -70.0F;
    @Unique
    private static final float CHARGE_LEFT_ARM_YAW = 30.0F;
    @Unique
    private static final float CHARGE_BODY_LEAN = -20.0F;
    @Unique
    private static final float THROW_RIGHT_ARM_PITCH = -130.0F;
    @Unique
    private static final float THROW_RIGHT_ARM_YAW = -5.0F;
    @Unique
    private static final float THROW_LEFT_ARM_PITCH = -20.0F;
    @Unique
    private static final float THROW_LEFT_ARM_YAW = 5.0F;
    @Unique
    private static final float THROW_BODY_LEAN = 25.0F;
    @Unique
    private static final float THROW_DURATION_MS = 350.0F;
    @Unique
    private static final float PUSH_IDLE_PITCH = -45.0F;
    @Unique
    private static final float PUSH_IDLE_YAW = -20.0F;
    @Unique
    private static final float PUSH_ACTION_PITCH = -90.0F;
    @Unique
    private static final float PUSH_ACTION_YAW = 0.0F;
    @Unique
    private static final float PUSH_ANIMATION_SPEED = 0.08F;
    @Unique
    private static final float SUPERMAN_HEAD_PITCH = -30.0F;
    @Unique
    private static final float SUPERMAN_RIGHT_ARM_PITCH = -180.0F;
    @Unique
    private static final float SUPERMAN_RIGHT_ARM_ROLL = -10.0F;
    @Unique
    private static final float SUPERMAN_LEFT_ARM_PITCH = 10.0F;
    @Unique
    private static final float SUPERMAN_LEFT_ARM_ROLL = 10.0F;
    @Unique
    private static final float SUPERMAN_LEG_ROLL = 5.0F;

    @Inject(
        method = {"method_17087"},
        at = {@At("TAIL")}
    )
    private void injectPose(T entity, float f, float g, float h, float i, float j, CallbackInfo ci) {
        if (entity instanceof PlayerEntity player) {
            UUID playerId = player.getUuid();
            PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
            PoseState lastPose = ArmPoseTracker.lastPose.getOrDefault(playerId, PoseState.NONE);
            PlayerEntityModel<?> model = (PlayerEntityModel<?>)this;
            ModelPart rightArm = model.rightArm;
            ModelPart leftArm = model.leftArm;
            ModelPart body = model.body;
            ModelPart head = model.head;
            ModelPart rightLeg = model.rightLeg;
            ModelPart leftLeg = model.leftLeg;
            float baseRightPitch = rightArm.pitch;
            float baseLeftPitch = leftArm.pitch;
            float baseRightYaw = rightArm.yaw;
            float baseLeftYaw = leftArm.yaw;
            boolean isSwinging = player.handSwinging;
            boolean isUsingItem = player.isUsingItem();
            if (pose == PoseState.GRABBED) {
                if (player.hasVehicle() && !(player.getVehicle() instanceof PlayerEntity)) {
                    ArmPoseTracker.lastPose.put(playerId, pose);
                } else {
                    body.pitch = 0.0F;
                    body.yaw = 0.0F;
                    body.roll = 0.0F;
                    head.pitch = (float)Math.toRadians(-30.0);
                    head.yaw = 0.0F;
                    head.roll = 0.0F;
                    rightArm.pitch = (float)Math.toRadians(-180.0);
                    rightArm.yaw = 0.0F;
                    rightArm.roll = (float)Math.toRadians(-10.0);
                    leftArm.pitch = (float)Math.toRadians(10.0);
                    leftArm.yaw = 0.0F;
                    leftArm.roll = (float)Math.toRadians(10.0);
                    rightLeg.pitch = 0.0F;
                    rightLeg.yaw = 0.0F;
                    rightLeg.roll = (float)Math.toRadians(5.0);
                    leftLeg.pitch = 0.0F;
                    leftLeg.yaw = 0.0F;
                    leftLeg.roll = (float)Math.toRadians(-5.0);
                    model.rightSleeve.copyTransform(rightArm);
                    model.leftSleeve.copyTransform(leftArm);
                    model.rightPants.copyTransform(rightLeg);
                    model.leftPants.copyTransform(leftLeg);
                    model.jacket.copyTransform(body);
                    model.hat.copyTransform(head);
                    ArmPoseTracker.lastPose.put(playerId, pose);
                }
            } else if (pose == PoseState.NONE && !player.isOnGround() && !player.hasVehicle() && GrabMechanic.isPlayerThrown(playerId)) {
                body.pitch = 0.0F;
                body.yaw = 0.0F;
                body.roll = 0.0F;
                head.pitch = (float)Math.toRadians(-30.0);
                head.yaw = 0.0F;
                head.roll = 0.0F;
                rightArm.pitch = (float)Math.toRadians(-180.0);
                rightArm.yaw = 0.0F;
                rightArm.roll = (float)Math.toRadians(-10.0);
                leftArm.pitch = (float)Math.toRadians(10.0);
                leftArm.yaw = 0.0F;
                leftArm.roll = (float)Math.toRadians(10.0);
                rightLeg.pitch = 0.0F;
                rightLeg.yaw = 0.0F;
                rightLeg.roll = (float)Math.toRadians(5.0);
                leftLeg.pitch = 0.0F;
                leftLeg.yaw = 0.0F;
                leftLeg.roll = (float)Math.toRadians(-5.0);
                model.rightSleeve.copyTransform(rightArm);
                model.leftSleeve.copyTransform(leftArm);
                model.rightPants.copyTransform(rightLeg);
                model.leftPants.copyTransform(leftLeg);
                model.jacket.copyTransform(body);
                model.hat.copyTransform(head);
                ArmPoseTracker.lastPose.put(playerId, pose);
            } else {
                if (pose == PoseState.GRAB_HOLDING) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    boolean isLocalPlayer = mc.player != null && mc.player.getUuid().equals(playerId);
                    boolean isFirstPerson = mc.options.getPerspective().isFirstPerson();
                    if (!isLocalPlayer || !isFirstPerson) {
                        ArmPoseTracker.lastPose.put(playerId, pose);
                        return;
                    }
                }

                if (pose == PoseState.GRAB_READY) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    boolean isLocalPlayer = mc.player != null && mc.player.getUuid().equals(playerId);
                    boolean isFirstPerson = mc.options.getPerspective().isFirstPerson();
                    if (!isLocalPlayer || !isFirstPerson) {
                        ArmPoseTracker.lastPose.put(playerId, pose);
                        return;
                    }
                }

                if (pose == PoseState.PUSH_IDLE || pose == PoseState.PUSH_ACTION || pose == PoseState.PUSH_RETURN) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    boolean isLocalPlayer = mc.player != null && mc.player.getUuid().equals(playerId);
                    boolean isFirstPerson = mc.options.getPerspective().isFirstPerson();
                    if (!isLocalPlayer || !isFirstPerson) {
                        ArmPoseTracker.lastPose.put(playerId, pose);
                        return;
                    }
                }

                boolean hasHandRaised = HighFiveHandler.hasHandRaised(playerId);
                if (hasHandRaised) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    boolean isLocalPlayer = mc.player != null && mc.player.getUuid().equals(playerId);
                    boolean isFirstPerson = mc.options.getPerspective().isFirstPerson();
                    if (isLocalPlayer && isFirstPerson) {
                        rightArm.pitch = (float)Math.toRadians(-100.0);
                        rightArm.yaw = (float)Math.toRadians(30.0);
                        rightArm.roll = 0.0F;
                        model.rightSleeve.copyTransform(rightArm);
                        ArmPoseTracker.lastPose.put(playerId, pose);
                        return;
                    }
                }

                if (!isSwinging && !isUsingItem
                    || pose == PoseState.GRAB_READY
                    || pose == PoseState.GRAB_HOLDING
                    || pose == PoseState.PUSH_IDLE
                    || pose == PoseState.PUSH_ACTION) {
                    float targetRightPitch = 0.0F;
                    float targetLeftPitch = 0.0F;
                    float targetRightYaw = 0.0F;
                    float targetLeftYaw = 0.0F;
                    float targetRightRoll = 0.0F;
                    float targetLeftRoll = 0.0F;
                    float targetBodyLean = 0.0F;
                    float animSpeed = 0.2F;
                    boolean useAbsoluteAngles = false;
                    float directJitterPitch = 0.0F;
                    float directJitterYaw = 0.0F;
                    float directJitterRoll = 0.0F;
                    Long throwStart = ArmPoseTracker.throwAnimationStart.get(playerId);
                    boolean inThrowAnimation = false;
                    float throwProgress = 0.0F;
                    if (throwStart != null) {
                        long elapsed = System.currentTimeMillis() - throwStart;
                        if ((float)elapsed < 350.0F) {
                            inThrowAnimation = true;
                            throwProgress = (float)elapsed / 350.0F;
                        } else {
                            ArmPoseTracker.throwAnimationStart.remove(playerId);
                        }
                    }

                    float chargeProgress = GrabInputHandler.getChargeProgressFor(playerId);
                    boolean isCharging = chargeProgress >= 0.0F;
                    if (inThrowAnimation) {
                        animSpeed = 0.35F;
                        useAbsoluteAngles = true;
                        if (throwProgress < 0.4F) {
                            float throwPhase = throwProgress / 0.4F;
                            targetRightPitch = lerp(-150.0F, -130.0F, throwPhase);
                            targetRightYaw = lerp(10.0F, -5.0F, throwPhase);
                            targetLeftPitch = lerp(-70.0F, -20.0F, throwPhase);
                            targetLeftYaw = lerp(30.0F, 5.0F, throwPhase);
                            targetBodyLean = lerp(-20.0F, 25.0F, throwPhase);
                        } else {
                            float returnPhase = (throwProgress - 0.4F) / 0.6F;
                            targetRightPitch = lerp(-130.0F, 0.0F, returnPhase);
                            targetRightYaw = lerp(-5.0F, 0.0F, returnPhase);
                            targetLeftPitch = lerp(-20.0F, 0.0F, returnPhase);
                            targetLeftYaw = lerp(5.0F, 0.0F, returnPhase);
                            targetBodyLean = lerp(25.0F, 0.0F, returnPhase);
                        }

                        targetRightRoll = 0.0F;
                        targetLeftRoll = 0.0F;
                    } else if (pose == PoseState.GRAB_HOLDING && isCharging) {
                        useAbsoluteAngles = true;
                        animSpeed = 0.2F;
                        targetRightPitch = lerp(-110.0F, -150.0F, chargeProgress);
                        targetRightYaw = lerp(-10.0F, 10.0F, chargeProgress);
                        targetRightRoll = lerp(0.0F, 0.0F, chargeProgress);
                        targetLeftPitch = lerp(10.0F, -70.0F, chargeProgress);
                        targetLeftYaw = lerp(10.0F, 30.0F, chargeProgress);
                        targetLeftRoll = 0.0F;
                        targetBodyLean = lerp(0.0F, -20.0F, chargeProgress);
                    } else if (pose == PoseState.GRAB_HOLDING) {
                        useAbsoluteAngles = true;
                        targetRightPitch = -110.0F;
                        targetRightYaw = -10.0F;
                        targetRightRoll = 0.0F;
                        targetLeftPitch = 10.0F;
                        targetLeftYaw = 10.0F;
                        targetLeftRoll = 0.0F;
                    } else if (pose == PoseState.GRAB_READY) {
                        useAbsoluteAngles = true;
                        targetRightPitch = -70.0F;
                        targetLeftPitch = -70.0F;
                        targetRightYaw = -5.0F;
                        targetLeftYaw = 5.0F;
                        targetRightRoll = 0.0F;
                        targetLeftRoll = -0.0F;
                    } else if (pose == PoseState.PUSH_ACTION) {
                        useAbsoluteAngles = true;
                        animSpeed = 1.0F;
                        float pushProgress = PushClientHandler.getPushAnimProgress(playerId);
                        if (pushProgress >= 0.0F && pushProgress < 0.4F) {
                            float phase = pushProgress / 0.4F;
                            float eased = 1.0F - (1.0F - phase) * (1.0F - phase);
                            targetRightPitch = lerp(-45.0F, -110.0F, eased);
                            targetLeftPitch = lerp(-45.0F, -110.0F, eased);
                            targetRightYaw = lerp(-20.0F, -5.0F, eased);
                            targetLeftYaw = lerp(20.0F, 5.0F, eased);
                        } else if (pushProgress >= 0.4F) {
                            float phase = (pushProgress - 0.4F) / 0.6F;
                            float eased = phase * phase;
                            targetRightPitch = lerp(-110.0F, -45.0F, eased);
                            targetLeftPitch = lerp(-110.0F, -45.0F, eased);
                            targetRightYaw = lerp(-5.0F, -20.0F, eased);
                            targetLeftYaw = lerp(5.0F, 20.0F, eased);
                        } else {
                            targetRightPitch = -45.0F;
                            targetLeftPitch = -45.0F;
                            targetRightYaw = -20.0F;
                            targetLeftYaw = 20.0F;
                        }
                    } else if (pose == PoseState.PUSH_IDLE || pose == PoseState.PUSH_RETURN) {
                        useAbsoluteAngles = true;
                        animSpeed = 0.08F;
                        targetRightPitch = -45.0F;
                        targetLeftPitch = -45.0F;
                        targetRightYaw = -20.0F;
                        targetLeftYaw = 20.0F;
                    } else if (!ChargedDapClientHandler.isPlayerCharging(playerId)) {
                        if (CatchClientHandler.getCatcherAnimProgress(playerId) >= 0.0F) {
                            useAbsoluteAngles = true;
                            animSpeed = 1.0F;
                            float progress = CatchClientHandler.getCatcherAnimProgress(playerId);
                            if (progress < 0.3F) {
                                float phase = progress / 0.3F;
                                float eased = phase * phase;
                                targetRightPitch = lerp(-70.0F, -30.0F, eased);
                                targetLeftPitch = lerp(-70.0F, -30.0F, eased);
                                targetRightYaw = lerp(5.0F, 40.0F, eased);
                                targetLeftYaw = lerp(-5.0F, -40.0F, eased);
                            } else {
                                float phase = (progress - 0.3F) / 0.7F;
                                float eased = 1.0F - (1.0F - phase) * (1.0F - phase);
                                targetRightPitch = lerp(-30.0F, 0.0F, eased);
                                targetLeftPitch = lerp(-30.0F, 0.0F, eased);
                                targetRightYaw = lerp(40.0F, 0.0F, eased);
                                targetLeftYaw = lerp(-40.0F, 0.0F, eased);
                            }
                        } else if (CatchClientHandler.getCaughtAnimProgress(playerId) >= 0.0F) {
                            useAbsoluteAngles = true;
                            animSpeed = 1.0F;
                            float progress = CatchClientHandler.getCaughtAnimProgress(playerId);
                            if (progress < 0.2F) {
                                float phase = progress / 0.2F;
                                targetRightPitch = lerp(0.0F, -20.0F, phase);
                                targetLeftPitch = lerp(0.0F, -20.0F, phase);
                                targetRightYaw = lerp(0.0F, -30.0F, phase);
                                targetLeftYaw = lerp(0.0F, 30.0F, phase);
                            } else {
                                float phase = (progress - 0.2F) / 0.8F;
                                float eased = phase * phase;
                                targetRightPitch = lerp(-20.0F, 0.0F, eased);
                                targetLeftPitch = lerp(-20.0F, 0.0F, eased);
                                targetRightYaw = lerp(-30.0F, 0.0F, eased);
                                targetLeftYaw = lerp(30.0F, 0.0F, eased);
                            }
                        } else {
                            useAbsoluteAngles = false;
                        }
                    } else {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        boolean isLocalPlayer = mc.player != null && mc.player.getUuid().equals(playerId);
                        boolean isFirstPerson = mc.options.getPerspective().isFirstPerson();
                        if (!isLocalPlayer || !isFirstPerson) {
                            ArmPoseTracker.lastPose.put(playerId, pose);
                            return;
                        }

                        useAbsoluteAngles = true;
                        animSpeed = 0.35F;
                        float chargePercent = ChargedDapClientHandler.getPlayerChargePercent(playerId);
                        float fireLevel = ChargedDapClientHandler.getPlayerFireLevel(playerId);
                        float basePitch = -70.0F;
                        float baseYaw = -15.0F;
                        float baseRoll = 5.0F;
                        if (chargePercent > 0.5F) {
                            float pullback = (chargePercent - 0.5F) * 2.0F;
                            basePitch -= pullback * 10.0F;
                            baseYaw -= pullback * 10.0F;
                        }

                        if (fireLevel > 0.05F) {
                            basePitch -= fireLevel * 30.0F;
                            baseYaw -= fireLevel * 35.0F;
                            baseRoll += fireLevel * 20.0F;
                        }

                        targetRightPitch = basePitch;
                        targetRightYaw = baseYaw;
                        targetRightRoll = baseRoll;
                        targetLeftPitch = 0.0F;
                        targetLeftYaw = 0.0F;
                        targetLeftRoll = 0.0F;
                    }

                    float currRightPitch = ArmPoseTracker.rightArmPitch.getOrDefault(playerId, 0.0F);
                    float currLeftPitch = ArmPoseTracker.leftArmPitch.getOrDefault(playerId, 0.0F);
                    float currRightYaw = ArmPoseTracker.rightArmYaw.getOrDefault(playerId, 0.0F);
                    float currLeftYaw = ArmPoseTracker.leftArmYaw.getOrDefault(playerId, 0.0F);
                    float currRightRoll = ArmPoseTracker.rightArmRoll.getOrDefault(playerId, 0.0F);
                    float currLeftRoll = ArmPoseTracker.leftArmRoll.getOrDefault(playerId, 0.0F);
                    float currBodyLean = ArmPoseTracker.bodyLean.getOrDefault(playerId, 0.0F);
                    float targetRightPitchRad = (float)Math.toRadians(targetRightPitch);
                    float targetLeftPitchRad = (float)Math.toRadians(targetLeftPitch);
                    float targetRightYawRad = (float)Math.toRadians(targetRightYaw);
                    float targetLeftYawRad = (float)Math.toRadians(targetLeftYaw);
                    float targetRightRollRad = (float)Math.toRadians(targetRightRoll);
                    float targetLeftRollRad = (float)Math.toRadians(targetLeftRoll);
                    float targetBodyLeanRad = (float)Math.toRadians(targetBodyLean);
                    currRightPitch += (targetRightPitchRad - currRightPitch) * animSpeed;
                    currLeftPitch += (targetLeftPitchRad - currLeftPitch) * animSpeed;
                    currRightYaw += (targetRightYawRad - currRightYaw) * animSpeed;
                    currLeftYaw += (targetLeftYawRad - currLeftYaw) * animSpeed;
                    currRightRoll += (targetRightRollRad - currRightRoll) * animSpeed;
                    currLeftRoll += (targetLeftRollRad - currLeftRoll) * animSpeed;
                    currBodyLean += (targetBodyLeanRad - currBodyLean) * animSpeed;
                    ArmPoseTracker.rightArmPitch.put(playerId, currRightPitch);
                    ArmPoseTracker.leftArmPitch.put(playerId, currLeftPitch);
                    ArmPoseTracker.rightArmYaw.put(playerId, currRightYaw);
                    ArmPoseTracker.leftArmYaw.put(playerId, currLeftYaw);
                    ArmPoseTracker.rightArmRoll.put(playerId, currRightRoll);
                    ArmPoseTracker.leftArmRoll.put(playerId, currLeftRoll);
                    ArmPoseTracker.bodyLean.put(playerId, currBodyLean);
                    if (useAbsoluteAngles) {
                        rightArm.pitch = currRightPitch + (float)Math.toRadians(directJitterPitch);
                        leftArm.pitch = currLeftPitch;
                        rightArm.yaw = currRightYaw + (float)Math.toRadians(directJitterYaw);
                        leftArm.yaw = currLeftYaw;
                        rightArm.roll = currRightRoll + (float)Math.toRadians(directJitterRoll);
                        leftArm.roll = currLeftRoll;
                    } else {
                        rightArm.pitch = baseRightPitch + currRightPitch;
                        leftArm.pitch = baseLeftPitch + currLeftPitch;
                        rightArm.yaw = baseRightYaw + currRightYaw;
                        leftArm.yaw = baseLeftYaw + currLeftYaw;
                        rightArm.roll += currRightRoll;
                        leftArm.roll += currLeftRoll;
                    }

                    boolean shouldApplyBodyLean = inThrowAnimation || pose == PoseState.GRAB_HOLDING && isCharging;
                    if (shouldApplyBodyLean && Math.abs(currBodyLean) > 0.01F) {
                        body.pitch += currBodyLean;
                        model.jacket.copyTransform(body);
                    }

                    model.rightSleeve.copyTransform(rightArm);
                    model.leftSleeve.copyTransform(leftArm);
                    ArmPoseTracker.lastPose.put(playerId, pose);
                }
            }
        }
    }

    @Unique
    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }
}
