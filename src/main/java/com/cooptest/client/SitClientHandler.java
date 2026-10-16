package com.cooptest.client;

import com.cooptest.SitHandler;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

public class SitClientHandler {
    private static boolean wasFHeld = false;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
            if (client.player != null && client.currentScreen == null) {
                boolean fHeld = InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), 70);
                if (fHeld != wasFHeld) {
                    wasFHeld = fHeld;
                    ClientPlayNetworking.send(new SitHandler.SitFHoldPayload(fHeld));
                }
            }
        });
    }
}
