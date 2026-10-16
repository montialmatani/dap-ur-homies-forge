package com.cooptest.client;

import com.cooptest.bully.BullyDapHandler;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.Vec3d;

public class BullyDapClientHandler {
    private static final float SHAKE_MIN = 0.2F;
    private static final float SHAKE_MAX = 0.8F;
    private static final long SHAKE_DUR_MIN_MS = 150L;
    private static final long SHAKE_DUR_MAX_MS = 500L;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BullyDapHandler.BullyDapEffectsPayload.ID, (payload, context) -> context.client().execute(() -> {
            float n = payload.normalized();
            Vec3d pos = new Vec3d(payload.x(), payload.y(), payload.z());
            CoopCameraShakeHandler.shake(0.2F + n * 0.6F, 150L + (long)(n * 350.0F));
            CoopShockwaveRenderer.start(pos);
        }));
        ClientPlayNetworking.registerGlobalReceiver(BullyDapHandler.BullySimpleFlashPayload.ID, (payload, context) -> {});
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
            CoopCameraShakeHandler.tick();
            CoopRadialBlurHandler.tick();
        });
    }
}
