package com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1;
import com.cooptest.fabricshim.fabric.api.event.Event;
import net.minecraft.client.MinecraftClient;
public final class ClientTickEvents {
    public static final Event<StartTick> START_CLIENT_TICK = new Event<>();
    public static final Event<EndTick> END_CLIENT_TICK = new Event<>();
    @FunctionalInterface public interface StartTick { void onStartTick(MinecraftClient client); }
    @FunctionalInterface public interface EndTick { void onEndTick(MinecraftClient client); }
}
