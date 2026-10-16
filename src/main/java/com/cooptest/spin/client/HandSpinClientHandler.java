package com.cooptest.spin.client;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.cooptest.client.CoopCameraShakeHandler;
import com.cooptest.spin.HandSpinHandler;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.WorldRenderEvents;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.WorldRenderEvents.Start;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.lwjgl.glfw.GLFW;

public class HandSpinClientHandler {
    private static final int SPIN_HOLD_KEY = 71;
    private static boolean gWasHeld = false;
    private static boolean localSpinning = false;
    private static Vec3d lockedCenter = null;
    private static UUID partnerId = null;
    private static UUID strengthWeakId = null;
    private static int liftTicks = 0;
    private static boolean monkeFlying = false;
    private static final int WATCHDOG_TICKS = 40;
    private static final int MONKE_WATCHDOG_TICKS = 260;
    private static int ticksSinceServerSignal = 0;
    private static int monkeFlyTicks = 0;
    private static ClientWorld lastWorld = null;

    public static void noteServerSignal() {
        ticksSinceServerSignal = 0;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HandSpinHandler.HandSpinStartPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            noteServerSignal();
            lastWorld = ctx.client().world;
            lockedCenter = new Vec3d(payload.centerX(), payload.centerY(), payload.centerZ());
            strengthWeakId = null;
            liftTicks = 0;
            if (ctx.client().player != null) {
                UUID localId = ctx.client().player.getUuid();
                if (localId.equals(payload.p1())) {
                    localSpinning = true;
                    partnerId = payload.p2();
                } else if (localId.equals(payload.p2())) {
                    localSpinning = true;
                    partnerId = payload.p1();
                }
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(HandSpinHandler.HandSpinStopPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            noteServerSignal();
            if (ctx.client().player != null && ctx.client().player.getUuid().equals(payload.playerId())) {
                localSpinning = false;
                lockedCenter = null;
                partnerId = null;
                strengthWeakId = null;
                liftTicks = 0;
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(HandSpinHandler.HandSpinShakePulsePayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            noteServerSignal();
            CoopCameraShakeHandler.shake(payload.amount(), payload.durationMs());
        }));
        ClientPlayNetworking.registerGlobalReceiver(HandSpinHandler.HandSpinStrengthPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            strengthWeakId = payload.weakId();
            liftTicks = 0;
        }));
        ClientPlayNetworking.registerGlobalReceiver(HandSpinHandler.HandSpinMonkeFlyPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().player != null && ctx.client().player.getUuid().equals(payload.playerId())) {
                noteServerSignal();
                monkeFlying = payload.flying();
                monkeFlyTicks = 0;
                if (!payload.flying()) {
                    strengthWeakId = null;
                    liftTicks = 0;
                }
            }
        }));
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
            if (client.world != lastWorld) {
                lastWorld = client.world;
                if (localSpinning || monkeFlying) {
                    forceReleaseLocal();
                }
            }

            if (client.player == null) {
                if (localSpinning || monkeFlying) {
                    forceReleaseLocal();
                }
            } else {
                if (localSpinning) {
                    if (++ticksSinceServerSignal > 40) {
                        forceReleaseLocal();
                    }
                } else {
                    ticksSinceServerSignal = 0;
                }

                if (monkeFlying) {
                    if (++monkeFlyTicks > 260) {
                        monkeFlying = false;
                        monkeFlyTicks = 0;
                        strengthWeakId = null;
                        liftTicks = 0;
                    }
                } else {
                    monkeFlyTicks = 0;
                }

                UUID id = client.player.getUuid();
                PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
                if (pose != PoseState.GRAB_READY && !localSpinning) {
                    if (gWasHeld) {
                        ClientPlayNetworking.send(new HandSpinHandler.HandSpinFHoldPayload(false));
                        gWasHeld = false;
                    }
                } else {
                    long win = client.getWindow().getHandle();
                    boolean held = GLFW.glfwGetKey(win, 71) == 1;
                    if (held && !gWasHeld) {
                        ClientPlayNetworking.send(new HandSpinHandler.HandSpinFHoldPayload(true));
                        gWasHeld = true;
                    } else if (!held && gWasHeld) {
                        ClientPlayNetworking.send(new HandSpinHandler.HandSpinFHoldPayload(false));
                        gWasHeld = false;
                    }
                }
            }
        });
        WorldRenderEvents.START.register((Start)ctx -> {
            if (localSpinning && lockedCenter != null) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    float tickDelta = com.cooptest.fabricshim.mc.RenderTickCounter.current().getTickDelta(true);
                    Vec3d rp = client.player.getLerpedPos(tickDelta);
                    double dx = rp.x - lockedCenter.x;
                    double dz = rp.z - lockedCenter.z;
                    if (!(dx * dx + dz * dz < 1.0E-6)) {
                        float yaw = (float)Math.toDegrees(Math.atan2(dx, -dz));
                        applyLockedYaw(client.player, yaw);
                    }
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> forceReleaseLocal());
        ClientPlayConnectionEvents.JOIN.register((Join)(handler, sender, client) -> forceReleaseLocal());
    }

    public static void forceReleaseLocal() {
        localSpinning = false;
        lockedCenter = null;
        partnerId = null;
        gWasHeld = false;
        strengthWeakId = null;
        liftTicks = 0;
        monkeFlying = false;
        monkeFlyTicks = 0;
        ticksSinceServerSignal = 0;
        HandSpinRenderSync.clear();
    }

    public static boolean isLocalPlayerSpinning() {
        return localSpinning;
    }

    public static Vec3d getLockedCenter() {
        return lockedCenter;
    }

    public static UUID getPartnerId() {
        return partnerId;
    }

    public static void applyLockedYaw(PlayerEntity player, float yaw) {
        player.setYaw(yaw);
        player.prevYaw = yaw;
        player.setHeadYaw(yaw);
        player.prevHeadYaw = yaw;
        player.setBodyYaw(yaw);
        player.prevBodyYaw = yaw;
    }

    public static boolean isLocalPlayerMonkeFlying() {
        return monkeFlying;
    }

    public static boolean isInputBlockedBySpin(UUID localId) {
        return !localSpinning && !monkeFlying ? PoseNetworking.poseStates.getOrDefault(localId, PoseState.NONE) == PoseState.GRAB_READY : true;
    }
}
