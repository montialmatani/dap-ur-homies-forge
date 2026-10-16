package com.cooptest.client;

import com.cooptest.DivineFlamCombo;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Type;

public class DivineFlamComboClient {
    private static KeyBinding divineFlameKey;
    private static long divineFlameEndTime = 0L;

    public static void register() {
        divineFlameKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cooptest.divine_flame", Type.field_1668, 74, "category.cooptest"));
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
            if (divineFlameKey.wasPressed()) {
                ClientPlayNetworking.send(new DivineFlamCombo.DivineJPressPayload());
                divineFlameEndTime = System.currentTimeMillis() + 3000L;
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(
            DivineFlamCombo.DivineStartPayload.ID,
            (payload, context) -> context.client().execute(() -> divineFlameEndTime = System.currentTimeMillis() + 1460L)
        );
    }

    public static boolean isLocalPlayerInCombo() {
        return System.currentTimeMillis() < divineFlameEndTime;
    }

    public static boolean onHighFivePress() {
        return false;
    }
}
