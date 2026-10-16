package com.cooptest;

import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;

public class CarryingSlowdown {
    public static final float CARRY_SPEED_MULTIPLIER = 0.6F;
    private static final double SLOWDOWN_AMOUNT = -0.039999997615814215;
    private static final Identifier MODIFIER_ID = new Identifier("cooptest", "carrying_slowdown");

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                updateSlowdown(player);
            }
        });
    }

    private static void updateSlowdown(ServerPlayerEntity player) {
        PoseState pose = PoseNetworking.poseStates.getOrDefault(player.getUuid(), PoseState.NONE);
        boolean isCarrying = pose == PoseState.GRAB_HOLDING;
        EntityAttributeInstance speedAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            EntityAttributeModifier existingModifier = speedAttr.getModifier(MODIFIER_ID);
            if (isCarrying) {
                if (existingModifier == null) {
                    speedAttr.addTemporaryModifier(new EntityAttributeModifier(MODIFIER_ID, -0.039999997615814215, Operation.MULTIPLY_TOTAL));
                }
            } else if (existingModifier != null) {
                speedAttr.removeModifier(MODIFIER_ID);
            }
        }
    }
}
