package com.cooptest.meme;

import com.cooptest.client.CoopImpactHandler;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Type;

public class SpinYeetClientHandler {
    private static KeyBinding spinYeetKey;
    private static boolean wasHeld = false;
    private static boolean weAreGrabber = false;
    private static boolean weAreGrabbed = false;
    private static UUID localGrabberUUID = null;
    private static UUID localGrabbedUUID = null;
    private static volatile float cameraRollDegrees = 0.0F;

    public static void register() {
        spinYeetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.coopmoves.spin_yeet", Type.KEYSYM, 77, "key.categories.coopmoves"));
        ClientPlayNetworking.registerGlobalReceiver(SpinYeetGrabberYawPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().player != null && weAreGrabber) {
                ctx.client().player.setYaw(ctx.client().player.getYaw() + payload.yawDelta());
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(
            SpinYeetImpactPayload.ID, (payload, ctx) -> ctx.client().execute(() -> CoopImpactHandler.start(CoopImpactHandler.PERFECT_DAP_SEQUENCE, 33L, true))
        );
        ClientPlayNetworking.registerGlobalReceiver(SpinYeetStartPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().player != null) {
                UUID localUUID = ctx.client().player.getUuid();
                localGrabberUUID = payload.grabberUUID();
                localGrabbedUUID = payload.grabbedUUID();
                weAreGrabber = localUUID.equals(localGrabberUUID);
                weAreGrabbed = localUUID.equals(localGrabbedUUID);
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(SpinYeetCameraPayload.ID, (payload, ctx) -> cameraRollDegrees = payload.rollDegrees());
        ClientPlayNetworking.registerGlobalReceiver(SpinYeetEndPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            cameraRollDegrees = 0.0F;
            weAreGrabber = false;
            weAreGrabbed = false;
            localGrabberUUID = null;
            localGrabbedUUID = null;
        }));
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
            if (client.player != null && client.world != null) {
                if (client.currentScreen == null) {
                    long windowHandle = client.getWindow().getHandle();
                    boolean isHeld = InputUtil.isKeyPressed(windowHandle, 77);
                    if (isHeld && !wasHeld) {
                        System.out.println("[SpinYeet-DEBUG] M pressed");
                        onMPressed(client);
                    } else if (!isHeld && wasHeld) {
                        System.out.println("[SpinYeet-DEBUG] M released, weAreGrabber=" + weAreGrabber);
                        onMReleased();
                    }

                    wasHeld = isHeld;
                }
            }
        });
    }

    private static void onMPressed(MinecraftClient client) {
        if (client.player != null && client.world != null) {
            Box searchBox = client.player.getBoundingBox().expand(2.0);
            List<LivingEntity> nearby = client.world.getEntitiesByClass(LivingEntity.class, searchBox, e -> e != client.player && e.isAlive());
            System.out.println("[SpinYeet-DEBUG] M pressed — nearby entities in 2-block box: " + nearby.size());
            if (nearby.isEmpty()) {
                System.out.println("[SpinYeet-DEBUG] No target found, aborting grab");
            } else {
                nearby.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(client.player)));
                Entity target = (Entity)nearby.get(0);
                System.out
                    .println(
                        "[SpinYeet-DEBUG] Sending grab for target="
                            + target.getName().getString()
                            + " dist="
                            + String.format("%.2f", Math.sqrt(target.squaredDistanceTo(client.player)))
                    );
                ClientPlayNetworking.send(new SpinYeetGrabPayload(target.getUuid()));
            }
        }
    }

    private static void onMReleased() {
        if (weAreGrabber) {
            ClientPlayNetworking.send(new SpinYeetReleasePayload());
        }
    }

    public static float getCameraRoll() {
        return cameraRollDegrees;
    }
}
