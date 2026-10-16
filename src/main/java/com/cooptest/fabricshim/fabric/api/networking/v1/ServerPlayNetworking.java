package com.cooptest.fabricshim.fabric.api.networking.v1;
import com.cooptest.fabricshim.forge.CoopNetwork;
import com.cooptest.fabricshim.mc.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
public final class ServerPlayNetworking {
    public static <T extends CustomPayload> boolean registerGlobalReceiver(CustomPayload.Id<T> id, PlayPayloadHandler<T> handler) {
        return CoopNetwork.registerServerHandler(id, handler);
    }
    public static void send(ServerPlayerEntity player, CustomPayload payload) { CoopNetwork.sendToPlayer(player, payload); }
    public static boolean canSend(ServerPlayerEntity player, CustomPayload.Id<?> id) { return CoopNetwork.hasCodec(id); }
    @FunctionalInterface public interface PlayPayloadHandler<T extends CustomPayload> { void receive(T payload, Context context); }
    public interface Context {
        MinecraftServer server();
        ServerPlayerEntity player();
        PacketSender responseSender();
    }
}
