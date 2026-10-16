package com.cooptest.fabricshim.forge;

import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.fabric.api.networking.v1.PacketSender;
import com.cooptest.fabricshim.mc.CustomPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/** Client-only half of CoopNetwork; only ever loaded on the physical client. */
final class ClientNetDispatch {
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void dispatch(CustomPayload payload) {
        ClientPlayNetworking.PlayPayloadHandler handler = CoopNetwork.CLIENT_HANDLERS.get(payload.getId().id());
        if (handler == null) return;
        handler.receive(payload, new ClientContext(MinecraftClient.getInstance()));
    }

    record ClientContext(MinecraftClient client) implements ClientPlayNetworking.Context {
        @Override
        public ClientPlayerEntity player() {
            return client.player;
        }

        @Override
        public PacketSender responseSender() {
            return CoopNetwork::sendToServer;
        }
    }
}
