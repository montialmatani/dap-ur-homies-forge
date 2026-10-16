package com.cooptest;

import com.cooptest.fabricshim.fabric.api.event.player.UseEntityCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.server.network.ServerPlayerEntity;

public class GrabInteractionHandler {
    public static void register() {
        UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
            if (world.isClient) {
                return ActionResult.PASS;
            } else if (player instanceof ServerPlayerEntity clicker) {
                if (entity instanceof ServerPlayerEntity target) {
                    if (clicker.isSneaking()) {
                        return ActionResult.PASS;
                    } else {
                        PoseState targetPose = PoseNetworking.poseStates.getOrDefault(target.getUuid(), PoseState.NONE);
                        if (targetPose != PoseState.GRAB_READY) {
                            return ActionResult.PASS;
                        } else {
                            PoseState clickerPose = PoseNetworking.poseStates.getOrDefault(clicker.getUuid(), PoseState.NONE);
                            if (clickerPose != PoseState.GRAB_HOLDING && clickerPose != PoseState.GRABBED) {
                                if (target.distanceTo(clicker) > 3.0F) {
                                    return ActionResult.PASS;
                                } else {
                                    boolean success = GrabMechanic.tryGrab(target, clicker);
                                    return success ? ActionResult.SUCCESS : ActionResult.PASS;
                                }
                            } else {
                                return ActionResult.PASS;
                            }
                        }
                    }
                } else {
                    return ActionResult.PASS;
                }
            } else {
                return ActionResult.PASS;
            }
        });
    }
}
