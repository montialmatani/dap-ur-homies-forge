package com.cooptest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.server.MinecraftServer;

public class FacingDapHandler {
    private static final double FACING_DOT = 0.97;
    private static final long ANIM_P1_MS = 3958L;
    private static final long ANIM_P2_MS = 4083L;
    private static final long IMPACT_MS = 670L;
    private static final long IMPACT_FRAME_MS = 750L;
    private static final long AURA_END_MS = 3500L;
    private static final long AURA_TICK_MS = 60L;
    private static final double START_DIST = 2.5;
    private static final double TARGET_DIST = 1.3;
    private static final long APPROACH_MS = 420L;
    private static final int ANIM_P1 = 75;
    private static final int ANIM_P2 = 76;
    private static final int ANIM_NONE = 0;
    private static final Map<UUID, FacingDapHandler.FacingSession> sessions = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FacingDapHandler::tick);
    }

    public static boolean areFacingEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        Vec3d eyes1 = p1.getEyePos();
        Vec3d eyes2 = p2.getEyePos();
        Vec3d look1 = p1.getRotationVec(1.0F);
        Vec3d look2 = p2.getRotationVec(1.0F);
        Vec3d to2 = eyes2.subtract(eyes1).normalize();
        Vec3d to1 = eyes1.subtract(eyes2).normalize();
        double dot1 = look1.dotProduct(to2);
        double dot2 = look2.dotProduct(to1);
        return dot1 >= 0.97 && dot2 >= 0.97;
    }

    public static void start(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        if (!sessions.containsKey(id1) && !sessions.containsKey(id2)) {
            FacingDapHandler.FacingSession s = new FacingDapHandler.FacingSession(id1, id2);
            sessions.put(id1, s);
            sessions.put(id2, s);
            ServerWorld world = p1.getServerWorld();
            Vec3d mid = p1.getPos().add(p2.getPos()).multiply(0.5);
            Vec3d flatDir = p2.getPos().subtract(p1.getPos());
            flatDir = new Vec3d(flatDir.x, 0.0, flatDir.z).normalize();
            Vec3d startPos1 = mid.subtract(flatDir.multiply(1.25));
            Vec3d startPos2 = mid.add(flatDir.multiply(1.25));
            float yaw1 = (float)(-Math.toDegrees(Math.atan2(flatDir.x, flatDir.z)));
            float yaw2 = yaw1 + 180.0F;
            p1.teleport(world, startPos1.x, p1.getY(), startPos1.z, Set.of(), yaw1, 0.0F);
            p2.teleport(world, startPos2.x, p2.getY(), startPos2.z, Set.of(), yaw2, 0.0F);
            PoseNetworking.broadcastAnimState(p1, 75);
            PoseNetworking.broadcastAnimState(p2, 76);
            ServerPlayNetworking.send(p1, new ChargedDapHandler.PerfectDapFreezePayload(true));
            ServerPlayNetworking.send(p2, new ChargedDapHandler.PerfectDapFreezePayload(true));
            world.playSound(
                null, mid.x, mid.y + 1.0, mid.z, (SoundEvent)SoundEvents.field_14793.comp_349(), SoundCategory.field_15248, 1.2F, 1.8F
            );
            world.playSound(null, mid.x, mid.y + 1.0, mid.z, ModSounds.DAP_HIT, SoundCategory.field_15248, 1.0F, 1.5F);
        }
    }

    private static void tick(MinecraftServer server) {
        Set<FacingDapHandler.FacingSession> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        for (FacingDapHandler.FacingSession s : new ArrayList<>(sessions.values())) {
            if (seen.add(s)) {
                tickSession(s, server);
            }
        }
    }

    private static void tickSession(FacingDapHandler.FacingSession s, MinecraftServer server) {
        ServerPlayerEntity p1 = server.getPlayerManager().getPlayer(s.p1);
        ServerPlayerEntity p2 = server.getPlayerManager().getPlayer(s.p2);
        if (p1 != null && p2 != null) {
            long elapsed = s.elapsed();
            ServerWorld world = p1.getServerWorld();
            if (elapsed <= 420L) {
                double t = elapsed / 420.0;
                double currentDist = 2.5 + -1.2 * t;
                Vec3d mid2 = p1.getPos().add(p2.getPos()).multiply(0.5);
                Vec3d fd = p2.getPos().subtract(p1.getPos());
                fd = new Vec3d(fd.x, 0.0, fd.z).normalize();
                double halfDist = currentDist * 0.5;
                Vec3d np1 = mid2.subtract(fd.multiply(halfDist));
                Vec3d np2 = mid2.add(fd.multiply(halfDist));
                p1.teleport(world, np1.x, p1.getY(), np1.z, Set.of(), p1.getYaw(), 0.0F);
                p2.teleport(world, np2.x, p2.getY(), np2.z, Set.of(), p2.getYaw(), 0.0F);
            }

            if (!s.impactFired && elapsed >= 670L) {
                s.impactFired = true;
                s.auraActive = true;
                Vec3d mid = p1.getPos().add(p2.getPos()).multiply(0.5).add(0.0, 1.2, 0.0);
                world.spawnParticles(ParticleTypes.field_11205, mid.x, mid.y, mid.z, 20, 0.4, 0.4, 0.4, 0.15);
                world.spawnParticles(ParticleTypes.field_11208, mid.x, mid.y, mid.z, 15, 0.3, 0.3, 0.3, 0.12);
                world.spawnParticles(ParticleTypes.field_17909, mid.x, mid.y, mid.z, 2, 0.0, 0.0, 0.0, 0.0);
                world.spawnParticles(ParticleTypes.field_11207, mid.x, mid.y, mid.z, 12, 0.3, 0.3, 0.3, 0.1);
            }

            if (!s.impactFrameFired && elapsed >= 750L) {
                s.impactFrameFired = true;
                Vec3d mid = p1.getPos().add(p2.getPos()).multiply(0.5).add(0.0, 1.2, 0.0);
                world.playSound(null, mid.x, mid.y, mid.z, ModSounds.PERFECT_DAP, SoundCategory.field_15248, 1.5F, 1.0F);
                int ringPoints = 16;
                double ringRadius = 0.2;

                for (int pass = 0; pass < 3; pass++) {
                    double r = ringRadius + pass * 1.2;

                    for (int i = 0; i < ringPoints; i++) {
                        double angle = (Math.PI * 2) * i / ringPoints;
                        double px = mid.x + r * Math.cos(angle);
                        double pz = mid.z + r * Math.sin(angle);
                        world.spawnParticles(ParticleTypes.field_11207, px, mid.y, pz, 1, 0.0, 0.1, 0.0, 0.02);
                        world.spawnParticles(ParticleTypes.field_11205, px, mid.y, pz, 1, 0.0, 0.05, 0.0, 0.01);
                    }
                }

                world.spawnParticles(ParticleTypes.field_17909, mid.x, mid.y, mid.z, 3, 0.05, 0.05, 0.05, 0.0);
                world.spawnParticles(ParticleTypes.field_11221, mid.x, mid.y, mid.z, 1, 0.0, 0.0, 0.0, 0.0);
                ServerPlayNetworking.send(p1, new ChargedDapHandler.FacingDapImpactPayload());
                ServerPlayNetworking.send(p2, new ChargedDapHandler.FacingDapImpactPayload());
            }

            if (s.auraActive && elapsed < 3500L) {
                long nowMs = System.currentTimeMillis();
                if (nowMs - s.lastAuraTick >= 60L) {
                    s.lastAuraTick = nowMs;
                    s.auraAngle += 0.3;
                    float alpha = Math.max(0.0F, 1.0F - (float)(elapsed - 670L) / 2830.0F);
                    int orbCount = Math.max(3, (int)(8.0F * alpha));
                    double orbRadius = 0.65 + 0.1 * Math.sin(elapsed / 400.0);

                    for (ServerPlayerEntity tgt : new ServerPlayerEntity[]{p1, p2}) {
                        Vec3d pp = tgt.getPos().add(0.0, 1.0, 0.0);

                        for (int i = 0; i < orbCount; i++) {
                            double a = s.auraAngle + (Math.PI * 2) * i / orbCount;
                            world.spawnParticles(
                                ParticleTypes.field_11207,
                                pp.x + Math.cos(a) * orbRadius,
                                pp.y,
                                pp.z + Math.sin(a) * orbRadius,
                                1,
                                0.0,
                                0.02,
                                0.0,
                                0.0
                            );
                            if (alpha > 0.5F) {
                                double a2 = -s.auraAngle * 1.6 + (Math.PI * 2) * i / orbCount;
                                world.spawnParticles(
                                    ParticleTypes.field_11208,
                                    pp.x + Math.cos(a2) * orbRadius * 0.5,
                                    pp.y + 0.3,
                                    pp.z + Math.sin(a2) * orbRadius * 0.5,
                                    1,
                                    0.0,
                                    0.0,
                                    0.0,
                                    0.0
                                );
                            }
                        }
                    }
                }
            }

            if (elapsed >= 4083L) {
                ServerPlayNetworking.send(p1, new ChargedDapHandler.PerfectDapFreezePayload(false));
                ServerPlayNetworking.send(p2, new ChargedDapHandler.PerfectDapFreezePayload(false));
                PoseNetworking.broadcastAnimState(p1, 0);
                PoseNetworking.broadcastAnimState(p2, 0);
                cleanup(s);
            }
        } else {
            cleanup(s);
        }
    }

    private static void cleanup(FacingDapHandler.FacingSession s) {
        sessions.remove(s.p1);
        sessions.remove(s.p2);
    }

    public static boolean isActive(UUID id) {
        return sessions.containsKey(id);
    }

    private static class FacingSession {
        final UUID p1;
        final UUID p2;
        final long startMs;
        boolean impactFired = false;
        boolean impactFrameFired = false;
        boolean auraActive = false;
        long lastAuraTick = 0L;
        double auraAngle = 0.0;

        FacingSession(UUID p1, UUID p2) {
            this.p1 = p1;
            this.p2 = p2;
            this.startMs = System.currentTimeMillis();
        }

        long elapsed() {
            return System.currentTimeMillis() - this.startMs;
        }
    }
}
