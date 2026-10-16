package com.cooptest.fabricshim.fabric.api.client.networking.v1;
import com.cooptest.fabricshim.fabric.api.event.Event;
import com.cooptest.fabricshim.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
public final class ClientPlayConnectionEvents {
    public static final Event<Join> JOIN = new Event<>();
    public static final Event<Disconnect> DISCONNECT = new Event<>();
    @FunctionalInterface public interface Join { void onPlayReady(ClientPlayNetworkHandler handler, PacketSender sender, MinecraftClient client); }
    @FunctionalInterface public interface Disconnect { void onPlayDisconnect(ClientPlayNetworkHandler handler, MinecraftClient client); }
}
