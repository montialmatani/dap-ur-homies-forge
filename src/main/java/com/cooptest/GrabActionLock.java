package com.cooptest;

import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.event.player.AttackBlockCallback;
import com.cooptest.fabricshim.fabric.api.event.player.AttackEntityCallback;
import com.cooptest.fabricshim.fabric.api.event.player.PlayerBlockBreakEvents;
import com.cooptest.fabricshim.fabric.api.event.player.UseBlockCallback;
import com.cooptest.fabricshim.fabric.api.event.player.UseEntityCallback;
import com.cooptest.fabricshim.fabric.api.event.player.UseItemCallback;
import com.cooptest.fabricshim.fabric.api.event.player.PlayerBlockBreakEvents.Before;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public class GrabActionLock {
    public static boolean isInGrab(UUID playerId) {
        if (playerId == null) {
            return false;
        } else if (GrabMechanic.holding.containsKey(playerId)) {
            return true;
        } else if (GrabMechanic.heldBy.containsKey(playerId)) {
            return true;
        } else {
            PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
            return pose == PoseState.GRAB_HOLDING || pose == PoseState.GRABBED;
        }
    }

    public static boolean isInGrab(PlayerEntity player) {
        return player != null && isInGrab(player.getUuid());
    }

    public static boolean isGrabbed(UUID playerId) {
        return GrabMechanic.heldBy.containsKey(playerId) || PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE) == PoseState.GRABBED;
    }

    public static boolean isHolding(UUID playerId) {
        return GrabMechanic.holding.containsKey(playerId) || PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE) == PoseState.GRAB_HOLDING;
    }

    public static void register() {
        UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hitResult) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            } else {
                return isInGrab(player) ? ActionResult.FAIL : ActionResult.PASS;
            }
        });
        UseItemCallback.EVENT.register((UseItemCallback)(player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (world.isClient()) {
                return TypedActionResult.pass(stack);
            } else {
                return isInGrab(player) ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
            }
        });
        AttackBlockCallback.EVENT.register((AttackBlockCallback)(player, world, hand, pos, direction) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            } else {
                return isInGrab(player) ? ActionResult.FAIL : ActionResult.PASS;
            }
        });
        AttackEntityCallback.EVENT.register((AttackEntityCallback)(player, world, hand, entity, hitResult) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            } else {
                return isInGrab(player) ? ActionResult.FAIL : ActionResult.PASS;
            }
        });
        UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            } else {
                return isInGrab(player) ? ActionResult.FAIL : ActionResult.PASS;
            }
        });
        PlayerBlockBreakEvents.BEFORE.register((Before)(world, player, pos, state, blockEntity) -> world.isClient() ? true : !isInGrab(player));
    }
}
