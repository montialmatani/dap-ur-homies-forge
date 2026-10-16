package com.cooptest.client;

import com.cooptest.SpearStrikeHandler;
import com.cooptest.fabricshim.api.EnvType;
import com.cooptest.fabricshim.api.Environment;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public final class SpearStrikeClientHandler {
    private static int targetId = -2;
    private static float speed;
    private static float turn;
    private static boolean missile;

    private SpearStrikeClientHandler() {
    }

    public static boolean isSteering() {
        return targetId != -2;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SpearStrikeHandler.SpearLockPayload.ID, (p, ctx) -> {
            targetId = p.targetId();
            speed = p.speed();
            turn = p.turn();
            missile = p.missile();
        });
        ClientTickEvents.END_CLIENT_TICK.register(SpearStrikeClientHandler::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(h, c) -> clear());
    }

    private static void clear() {
        targetId = -2;
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            clear();
        } else if (targetId != -2) {
            if (!mc.player.isOnGround() && !mc.player.hasVehicle()) {
                Vec3d want;
                if (targetId >= 0) {
                    Entity t = mc.world.getEntityById(targetId);
                    if (t == null || !t.isAlive()) {
                        clear();
                        return;
                    }

                    want = t.getBoundingBox().getCenter().subtract(mc.player.getBoundingBox().getCenter());
                    if (want.lengthSquared() < 1.0E-6) {
                        return;
                    }

                    want = want.normalize();
                } else {
                    if (!missile) {
                        return;
                    }

                    want = mc.player.getRotationVector().normalize();
                }

                Vec3d v = mc.player.getVelocity();
                double sp = missile ? speed : Math.max(v.length(), (double)speed);
                Vec3d cur = v.lengthSquared() > 1.0E-6 ? v.normalize() : want;
                Vec3d dir = cur.multiply(1.0 - turn).add(want.multiply(turn)).normalize();
                mc.player.setVelocity(dir.multiply(sp));
                mc.player.onLanding();
            } else {
                clear();
            }
        }
    }
}
