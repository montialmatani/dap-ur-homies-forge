package com.cooptest.client;

import com.cooptest.BlackHoodNetworking;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.cooptest.fabricshim.api.EnvType;
import com.cooptest.fabricshim.api.Environment;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public class BlackHoodClientHandler {
    private static final Set<UUID> hoodedPlayers = new HashSet<>();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BlackHoodNetworking.HoodStatePayload.ID, (payload, context) -> context.client().execute(() -> {
            if (payload.hasHood()) {
                hoodedPlayers.add(payload.targetId());
            } else {
                hoodedPlayers.remove(payload.targetId());
            }
        }));
        HudRenderCallback.EVENT.register((HudRenderCallback)(drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                if (hoodedPlayers.contains(client.player.getUuid())) {
                    int w = client.getWindow().getScaledWidth();
                    int h = client.getWindow().getScaledHeight();
                    drawContext.fill(0, 0, w, h, -16777216);
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> hoodedPlayers.clear());
    }

    public static boolean isLocalPlayerHooded() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? false : hoodedPlayers.contains(client.player.getUuid());
    }
}
