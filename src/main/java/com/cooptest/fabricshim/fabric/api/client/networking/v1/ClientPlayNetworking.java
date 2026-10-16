package com.cooptest.fabricshim.fabric.api.client.networking.v1;
import com.cooptest.fabricshim.fabric.api.networking.v1.PacketSender;
import com.cooptest.fabricshim.forge.CoopNetwork;
import com.cooptest.fabricshim.mc.CustomPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
public final class ClientPlayNetworking {
    public static <T extends CustomPayload> boolean registerGlobalReceiver(CustomPayload.Id<T> id, PlayPayloadHandler<T> handler) {
        return CoopNetwork.registerClientHandler(id, handler);
    }
    public static void send(CustomPayload payload) { CoopNetwork.sendToServer(payload); }
    public static boolean canSend(CustomPayload.Id<?> id) { return MinecraftClient.getInstance().getNetworkHandler() != null; }
    @FunctionalInterface public interface PlayPayloadHandler<T extends CustomPayload> { void receive(T payload, Context context); }
    public interface Context {
        MinecraftClient client();
        ClientPlayerEntity player();
        PacketSender responseSender();
    }
}
