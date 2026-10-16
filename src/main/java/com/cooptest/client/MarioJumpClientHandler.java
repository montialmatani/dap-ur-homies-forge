package com.cooptest.client;

import com.cooptest.MarioJumpHandler;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public class MarioJumpClientHandler {
    private static boolean wasJumpPressed = false;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
            if (client.player != null && client.world != null) {
                boolean isJumpPressed = client.options.jumpKey.isPressed();
                if (isJumpPressed && !wasJumpPressed && isOnPlayerHead(client)) {
                    ClientPlayNetworking.send(new MarioJumpHandler.MarioJumpRequestPayload());
                }

                wasJumpPressed = isJumpPressed;
            }
        });
    }

    private static boolean isOnPlayerHead(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return false;
        } else {
            Vec3d playerPos = player.getPos();
            double playerFeetY = playerPos.y;
            Box searchBox = new Box(
                playerPos.x - 0.8,
                playerPos.y - 2.5,
                playerPos.z - 0.8,
                playerPos.x + 0.8,
                playerPos.y + 0.5,
                playerPos.z + 0.8
            );

            for (PlayerEntity target : client.world.getEntitiesByClass(PlayerEntity.class, searchBox, p -> p != player && p.isAlive())) {
                Vec3d targetPos = target.getPos();
                double targetHeadY = targetPos.y + target.getStandingEyeHeight() + 0.15;
                double heightDiff = playerFeetY - targetHeadY;
                if (heightDiff >= -0.35 && heightDiff <= 0.5) {
                    double horizDist = Math.sqrt(
                        Math.pow(playerPos.x - targetPos.x, 2.0) + Math.pow(playerPos.z - targetPos.z, 2.0)
                    );
                    if (horizDist <= 0.7) {
                        return true;
                    }
                }
            }

            return false;
        }
    }
}
