package com.cooptest.spin.client;

import com.cooptest.spin.HandSpinHandler;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.WorldRenderEvents;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.WorldRenderEvents.Start;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;

public class HandSpinRenderSync {
    private static final int OBSERVER_STALE_TICKS = 6;
    private static final double SYNC_BLEND = 0.5;
    private static boolean active = false;
    private static UUID p1Id = null;
    private static UUID p2Id = null;
    private static Vec3d center = null;
    private static double angle = 0.0;
    private static double angularVel = 0.0;
    private static double p1Lift = 0.0;
    private static double p2Lift = 0.0;
    private static double correctionRemaining = 0.0;
    private static int ticksSinceSync = 999;
    private static Vec3d lastLocalPos = null;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HandSpinHandler.HandSpinObserverPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            p1Id = payload.p1();
            p2Id = payload.p2();
            center = new Vec3d(payload.centerX(), payload.centerY(), payload.centerZ());
            p1Lift = payload.p1Lift();
            p2Lift = payload.p2Lift();
            if (!active) {
                angle = payload.angle();
                correctionRemaining = 0.0;
                lastLocalPos = null;
                active = true;
            } else {
                correctionRemaining = payload.angle() - angle;
            }

            angularVel = payload.angularVel();
            ticksSinceSync = 0;
        }));
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
            if (active) {
                if (++ticksSinceSync > 6) {
                    clear();
                } else {
                    double step = correctionRemaining * 0.5 / Math.max(1, 3);
                    correctionRemaining -= step;
                    angle = angle + (angularVel + step);
                    apply(client, 0.0F, true);
                }
            }
        });
        WorldRenderEvents.START.register((Start)ctx -> {
            if (active) {
                apply(MinecraftClient.getInstance(), tickDelta(), false);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> clear());
    }

    private static float tickDelta() {
        return com.cooptest.fabricshim.mc.RenderTickCounter.current().getTickDelta(true);
    }

    public static void clear() {
        active = false;
        p1Id = null;
        p2Id = null;
        center = null;
        correctionRemaining = 0.0;
        ticksSinceSync = 999;
        lastLocalPos = null;
    }

    private static void apply(MinecraftClient client, float tickDelta, boolean isTick) {
        if (client != null && client.world != null && center != null) {
            double a = angle + angularVel * tickDelta;
            PlayerEntity e1 = null;
            PlayerEntity e2 = null;

            for (PlayerEntity p : client.world.getPlayers()) {
                UUID id = p.getUuid();
                if (id.equals(p1Id)) {
                    e1 = p;
                } else if (id.equals(p2Id)) {
                    e2 = p;
                }
            }

            if (e1 != null) {
                place(client, e1, a, p1Lift, isTick);
            }

            if (e2 != null) {
                place(client, e2, a + Math.PI, p2Lift, isTick);
            }
        }
    }

    private static void place(MinecraftClient client, PlayerEntity p, double a, double lift, boolean isTick) {
        double x = center.x + Math.cos(a) * 0.75;
        double z = center.z + Math.sin(a) * 0.75;
        double y = center.y + lift;
        float yaw = (float)Math.toDegrees(Math.atan2(Math.cos(a), -Math.sin(a)));
        boolean isLocal = p == client.player;
        if (isLocal) {
            if (isTick) {
                p.setPosition(x, y, z);
                if (lastLocalPos != null) {
                    p.prevX = lastLocalPos.x;
                    p.prevY = lastLocalPos.y;
                    p.prevZ = lastLocalPos.z;
                    p.lastRenderX = lastLocalPos.x;
                    p.lastRenderY = lastLocalPos.y;
                    p.lastRenderZ = lastLocalPos.z;
                }

                lastLocalPos = new Vec3d(x, y, z);
            }
        } else {
            if (isTick) {
                p.updateTrackedPositionAndAngles(x, y, z, yaw, p.getPitch(), 0);
            }

            p.setPosition(x, y, z);
            p.prevX = x;
            p.prevY = y;
            p.prevZ = z;
            p.lastRenderX = x;
            p.lastRenderY = y;
            p.lastRenderZ = z;
            HandSpinClientHandler.applyLockedYaw(p, yaw);
        }
    }

    public static boolean isActive() {
        return active;
    }
}
