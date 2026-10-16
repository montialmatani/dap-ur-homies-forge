package com.cooptest.mixin;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1 has no getPassengerRidingPos(), so the grab offset is applied in updatePassengerPosition instead.
 * The math reproduces 1.21's result: vehicle top attachment (height) minus the player passenger's
 * 0.6 vehicle attachment, plus the mod's grab offsets.
 */
@Mixin({Entity.class})
public class PlayerEntityPassengerOffsetMixin {
    private static final double GRAB_OFFSET_FORWARD = 0.6;
    private static final double GRAB_OFFSET_UP = 0.8;
    private static final double GRAB_OFFSET_RIGHT = 3.0;
    private static final double PLAYER_VEHICLE_ATTACHMENT_Y = 0.6;

    @Inject(
        method = {"updatePassengerPosition(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity$PositionUpdater;)V"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void customPassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater, CallbackInfo ci) {
        Entity vehicle = (Entity)(Object)this;
        if (vehicle instanceof PlayerEntity holder && passenger instanceof PlayerEntity && vehicle.hasPassenger(passenger)) {
            PoseState holderPose = PoseNetworking.poseStates.getOrDefault(holder.getUuid(), PoseState.NONE);
            if (holderPose == PoseState.GRAB_HOLDING) {
                float yaw = holder.getYaw();
                double yawRad = Math.toRadians(-yaw);
                double rotX = GRAB_OFFSET_RIGHT * Math.cos(yawRad) + GRAB_OFFSET_FORWARD * Math.sin(yawRad);
                double rotZ = GRAB_OFFSET_RIGHT * Math.sin(yawRad) - GRAB_OFFSET_FORWARD * Math.cos(yawRad);
                positionUpdater.accept(passenger,
                        holder.getX() + rotX,
                        holder.getY() + holder.getHeight() + GRAB_OFFSET_UP - PLAYER_VEHICLE_ATTACHMENT_Y,
                        holder.getZ() + rotZ);
                ci.cancel();
            }
        }
    }
}
