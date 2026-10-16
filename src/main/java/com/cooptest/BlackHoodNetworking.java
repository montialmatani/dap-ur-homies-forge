package com.cooptest;

import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class BlackHoodNetworking {
    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(BlackHoodNetworking.HoodStatePayload.ID, BlackHoodNetworking.HoodStatePayload.CODEC);
    }

    public static void broadcastHoodState(MinecraftServer server, UUID targetId, boolean hasHood) {
        BlackHoodNetworking.HoodStatePayload payload = new BlackHoodNetworking.HoodStatePayload(targetId, hasHood);

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public record HoodStatePayload(UUID targetId, boolean hasHood) implements CustomPayload {
        public static final Id<BlackHoodNetworking.HoodStatePayload> ID = new Id(new Identifier("testcoop", "hood_state"));
        public static final PacketCodec<PacketByteBuf, BlackHoodNetworking.HoodStatePayload> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeUuid(p.targetId());
            buf.writeBoolean(p.hasHood());
        }, buf -> new BlackHoodNetworking.HoodStatePayload(buf.readUuid(), buf.readBoolean()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
