package com.cooptest.client;

import com.cooptest.PushInteractionHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Hand;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

public class PushClientHandler {
    private static final Map<UUID, Long> pushAnimStart = new HashMap<>();
    private static final long PUSH_ANIM_DURATION = 400L;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PushInteractionHandler.PushAnimPayload.ID, (payload, context) -> context.client().execute(() -> {
            pushAnimStart.put(payload.playerId(), System.currentTimeMillis());
            MinecraftClient client = context.client();
            if (client.world != null) {
                for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
                    if (player.getUuid().equals(payload.playerId())) {
                        CoopAnimationHandler.playPushAnimation(player);
                        break;
                    }
                }
            }

            if (client.player != null && client.player.getUuid().equals(payload.playerId())) {
                client.player.swingHand(Hand.field_5808);
            }
        }));
    }

    public static float getPushAnimProgress(UUID playerId) {
        Long start = pushAnimStart.get(playerId);
        if (start == null) {
            return -1.0F;
        } else {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > 400L) {
                pushAnimStart.remove(playerId);
                return -1.0F;
            } else {
                return (float)elapsed / 400.0F;
            }
        }
    }

    public static void cleanup(UUID playerId) {
        pushAnimStart.remove(playerId);
    }
}
