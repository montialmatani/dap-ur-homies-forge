package com.cooptest.client;

import com.cooptest.BonkHandler;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Type;

public class BonkClientHandler {
    private static KeyBinding keyBonk;
    private static boolean movementLocked = false;
    private static float pendingYawDelta = 0.0F;
    private static float pendingPitchDelta = 0.0F;
    private static boolean jerkPending = false;

    public static void register() {
        keyBonk = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cooptest.bonk", Type.KEYSYM, 76, "category.cooptest"));
        ClientPlayNetworking.registerGlobalReceiver(
            BonkHandler.BonkMoveLockPayload.ID, (payload, context) -> context.client().execute(() -> movementLocked = payload.locked())
        );
        ClientPlayNetworking.registerGlobalReceiver(BonkHandler.BonkCameraPayload.ID, (payload, context) -> context.client().execute(() -> {
            pendingYawDelta = payload.deltaYaw();
            pendingPitchDelta = payload.deltaPitch();
            jerkPending = true;
        }));
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
            if (client.player != null) {
                while (keyBonk.wasPressed()) {
                    ClientPlayNetworking.send(new BonkHandler.BonkLKeyPayload());
                }

                if (movementLocked) {
                    client.player.setVelocity(0.0, client.player.getVelocity().y, 0.0);
                }

                if (jerkPending && client.player != null) {
                    jerkPending = false;
                    client.player.setYaw(client.player.getYaw() + pendingYawDelta);
                    client.player.setPitch(Math.max(-90.0F, Math.min(90.0F, client.player.getPitch() + pendingPitchDelta)));
                }
            }
        });
    }

    public static boolean isMovementLocked() {
        return movementLocked;
    }

    public static void reset() {
        movementLocked = false;
        jerkPending = false;
    }
}
