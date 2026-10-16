package com.cooptest.client;

import com.cooptest.HighFivePassHandler;
import java.util.UUID;
import com.cooptest.fabricshim.api.EnvType;
import com.cooptest.fabricshim.api.Environment;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class HighFivePassClientHandler {
    private static final long IMPACT_DELAY_MS = 210L;
    private static final long LOCK_END_MS = 290L;
    private static final float CAMERA_SHAKE_AMOUNT = 0.7F;
    private static final long CAMERA_SHAKE_MS = 80L;
    private static final float YAW_LOCK_RANGE = 15.0F;
    private static boolean lockActive = false;
    private static boolean shakeTriggered = false;
    private static float lockedYaw = 0.0F;
    private static long startTime = 0L;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HighFivePassHandler.HighFivePassStartPayload.ID, (payload, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (client.player != null) {
                    lockedYaw = client.player.getYaw();
                    lockActive = true;
                    shakeTriggered = false;
                    startTime = System.currentTimeMillis();
                }
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> tick());
    }

    private static void tick() {
        if (lockActive) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (!shakeTriggered && elapsed >= 210L) {
                CoopCameraShakeHandler.shake(0.7F, 80L);
                CoopSpeedLinesRenderer.start();
                shakeTriggered = true;
            }

            if (elapsed >= 290L) {
                lockActive = false;
            }
        }
    }

    public static void clampCameraYaw() {
        if (lockActive) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                float currentYaw = client.player.getYaw();
                float delta = MathHelper.wrapDegrees(currentYaw - lockedYaw);
                float clampedDelta = MathHelper.clamp(delta, -15.0F, 15.0F);
                if (clampedDelta != delta) {
                    float newYaw = lockedYaw + clampedDelta;
                    client.player.setYaw(newYaw);
                    client.player.setHeadYaw(newYaw);
                    client.player.setBodyYaw(newYaw);
                }
            }
        }
    }

    public static boolean isYawLocked() {
        return lockActive;
    }

    public static void onAnimationEnd(UUID playerId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getUuid().equals(playerId)) {
            lockActive = false;
        }
    }
}
