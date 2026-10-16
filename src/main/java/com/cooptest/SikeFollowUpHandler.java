package com.cooptest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.PlayerLookup;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Hand;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class SikeFollowUpHandler {
    private static final double TP_TARGET_DIST = 0.9;
    private static final double TP_LERP = 0.45;
    private static final int TP_MAX_TICKS = 60;
    private static final long QTE_WINDOW_MS = 1000L;
    private static final long DMG_START_MS = 290L;
    private static final long DMG_END_MS = 2500L;
    private static final long FREE_MS = 2960L;
    private static final long DMG_INTERVAL_MS = 200L;
    private static final long SPAM_INTERVAL_MS = 90L;
    private static final float DMG_PER_TICK = 2.0F;
    private static final float LAUNCH_POWER = 1.1F;
    private static final int ANIM_NOYA = 94;
    private static final Map<UUID, SikeFollowUpHandler.NoyaSession> sessions = new HashMap<>();
    private static final Map<UUID, UUID> pendingQTE = new HashMap<>();
    private static final Map<UUID, Long> qteOpenedAt = new HashMap<>();
    private static final Map<UUID, SikeFollowUpHandler.NoyaSession> animSessions = new HashMap<>();

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(SikeFollowUpHandler.NoyaFlickerPayload.ID, SikeFollowUpHandler.NoyaFlickerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SikeFollowUpHandler.SikeWindowPayload.ID, SikeFollowUpHandler.SikeWindowPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SikeFollowUpHandler.SikePressPayload.ID, SikeFollowUpHandler.SikePressPayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SikeFollowUpHandler.SikePressPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> onSikePress(player));
        });
        ServerTickEvents.END_SERVER_TICK.register(SikeFollowUpHandler::tick);
    }

    public static void onSikeExecuted(ServerPlayerEntity siker, ServerPlayerEntity victim) {
        UUID sid = siker.getUuid();
        UUID vid = victim.getUuid();
        pendingQTE.put(sid, vid);
        qteOpenedAt.put(sid, System.currentTimeMillis());
        ServerPlayNetworking.send(siker, new SikeFollowUpHandler.SikeWindowPayload(true, 1000, 0));
    }

    private static void onSikePress(ServerPlayerEntity player) {
        UUID sid = player.getUuid();
        UUID vid = pendingQTE.get(sid);
        if (vid != null) {
            pendingQTE.remove(sid);
            qteOpenedAt.remove(sid);
            ServerPlayNetworking.send(player, new SikeFollowUpHandler.SikeWindowPayload(false, 0, 0));
            MinecraftServer server = player.getServer();
            if (server != null) {
                ServerPlayerEntity victim = server.getPlayerManager().getPlayer(vid);
                if (victim != null) {
                    startNoyaSequence(player, victim);
                }
            }
        }
    }

    private static void startNoyaSequence(ServerPlayerEntity siker, ServerPlayerEntity victim) {
        SikeFollowUpHandler.NoyaSession session = new SikeFollowUpHandler.NoyaSession(siker.getUuid(), victim.getUuid());
        sessions.put(siker.getUuid(), session);
        Vec3d toVictim = victim.getPos().subtract(siker.getPos());
        float yawAwayFromVictim = 0.0F;
        if (toVictim.lengthSquared() > 0.001) {
            yawAwayFromVictim = (float)Math.toDegrees(Math.atan2(-toVictim.x, toVictim.z)) + 180.0F;
            siker.setYaw(yawAwayFromVictim);
            siker.setBodyYaw(yawAwayFromVictim);
            siker.setHeadYaw(yawAwayFromVictim);
            siker.prevBodyYaw = yawAwayFromVictim;
        }

        freezePlayer(siker, true);
        freezePlayer(victim, true);
        StrongSlapHandler.sendLookLockAtYaw(siker, yawAwayFromVictim);
        siker.swingHand(Hand.field_5808, true);
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<>();

        for (Entry<UUID, Long> entry : qteOpenedAt.entrySet()) {
            if (now - entry.getValue() > 1000L) {
                expired.add(entry.getKey());
            }
        }

        expired.forEach(id -> {
            pendingQTE.remove(id);
            qteOpenedAt.remove(id);
            ServerPlayerEntity sikerx = server.getPlayerManager().getPlayer(id);
            if (sikerx != null) {
                ServerPlayNetworking.send(sikerx, new SikeFollowUpHandler.SikeWindowPayload(false, 0, 0));
            }
        });
        List<UUID> done = new ArrayList<>();

        for (Entry<UUID, SikeFollowUpHandler.NoyaSession> entryx : new ArrayList<>(sessions.entrySet())) {
            SikeFollowUpHandler.NoyaSession s = entryx.getValue();
            if (!s.cancelled && !s.animRunning) {
                ServerPlayerEntity siker = server.getPlayerManager().getPlayer(s.sikerId);
                ServerPlayerEntity victim = server.getPlayerManager().getPlayer(s.victimId);
                if (siker != null && victim != null) {
                    siker.setVelocity(Vec3d.ZERO);
                    siker.velocityModified = true;
                    victim.setVelocity(Vec3d.ZERO);
                    victim.velocityModified = true;
                    double dist = siker.getPos().distanceTo(victim.getPos());
                    if (s.tpTick <= 60 && !(dist <= 0.9)) {
                        Vec3d cur = siker.getPos();
                        Vec3d target = victim.getPos();
                        double nx = cur.x + (target.x - cur.x) * 0.45;
                        double nz = cur.z + (target.z - cur.z) * 0.45;
                        siker.requestTeleport(nx, cur.y, nz);
                        s.tpTick++;
                    } else {
                        Vec3d toVictim = victim.getPos().subtract(siker.getPos());
                        if (toVictim.lengthSquared() > 0.001) {
                            Vec3d dir = toVictim.normalize();
                            Vec3d finalPos = victim.getPos().subtract(dir.multiply(0.9));
                            siker.requestTeleport(finalPos.x, siker.getY(), finalPos.z);
                        }

                        s.animRunning = true;
                        done.add(entryx.getKey());
                        launchAnimation(server, s, siker, victim);
                    }
                } else {
                    s.cancelled = true;
                    done.add(entryx.getKey());
                    releaseSession(server, s);
                }
            }
        }

        done.forEach(sessions::remove);
    }

    private static void launchAnimation(MinecraftServer server, SikeFollowUpHandler.NoyaSession session, ServerPlayerEntity siker, ServerPlayerEntity victim) {
        siker.swingHand(Hand.field_5808, true);
        siker.swingHand(Hand.field_5808, true);
        PoseNetworking.broadcastAnimState(siker, 94);
        UUID sikerUUID = session.sikerId;
        UUID victimUUID = session.victimId;
        animSessions.put(sikerUUID, session);
        new Thread(() -> runDamageLoop(server, session, sikerUUID, victimUUID)).start();
    }

    private static void runDamageLoop(MinecraftServer server, SikeFollowUpHandler.NoyaSession session, UUID sikerUUID, UUID victimUUID) {
        try {
            Thread.sleep(290L);
        } catch (InterruptedException var15) {
            return;
        }

        if (!session.cancelled) {
            server.execute(() -> {
                ServerPlayerEntity v = server.getPlayerManager().getPlayer(victimUUID);
                if (v != null) {
                    ServerPlayNetworking.send(v, new SikeFollowUpHandler.NoyaFlickerPayload(victimUUID, true, 2210L));
                }
            });
            long windowEnd = System.currentTimeMillis() + 2210L;
            long lastDmg = 0L;
            long lastSpam = 0L;

            while (System.currentTimeMillis() < windowEnd) {
                if (session.cancelled) {
                    return;
                }

                long now = System.currentTimeMillis();
                if (now - lastSpam >= 90L) {
                    lastSpam = now;
                    server.execute(
                        () -> {
                            if (!session.cancelled) {
                                ServerPlayerEntity v = server.getPlayerManager().getPlayer(victimUUID);
                                if (v != null) {
                                    ServerWorld world = (ServerWorld)v.getWorld();
                                    Vec3d head = v.getPos().add(0.0, 1.7, 0.0);
                                    world.spawnParticles(ParticleTypes.field_11205, head.x, head.y, head.z, 4, 0.15, 0.1, 0.15, 0.2);
                                    world.spawnParticles(ParticleTypes.field_11208, head.x, head.y, head.z, 3, 0.1, 0.1, 0.1, 0.1);
                                    world.playSound(
                                        null,
                                        head.x,
                                        head.y,
                                        head.z,
                                        SoundEvents.field_14840,
                                        SoundCategory.field_15248,
                                        0.6F,
                                        1.0F + (float)(Math.random() * 0.4F)
                                    );
                                }
                            }
                        }
                    );
                }

                if (now - lastDmg >= 200L) {
                    lastDmg = now;
                    server.execute(() -> {
                        if (!session.cancelled) {
                            ServerPlayerEntity v = server.getPlayerManager().getPlayer(victimUUID);
                            if (v != null) {
                                v.damage(((ServerWorld)v.getWorld()).getDamageSources().generic(), 2.0F);
                            }
                        }
                    });
                }

                try {
                    Thread.sleep(15L);
                } catch (InterruptedException var14) {
                    return;
                }
            }

            server.execute(() -> {
                if (!session.cancelled) {
                    ServerPlayerEntity v = server.getPlayerManager().getPlayer(victimUUID);
                    if (v != null) {
                        ServerPlayNetworking.send(v, new SikeFollowUpHandler.NoyaFlickerPayload(victimUUID, false, 0L));
                    }
                }
            });

            try {
                Thread.sleep(460L);
            } catch (InterruptedException var13) {
                return;
            }

            if (!session.cancelled) {
                server.execute(
                    () -> {
                        if (!session.cancelled) {
                            ServerPlayerEntity siker = server.getPlayerManager().getPlayer(sikerUUID);
                            ServerPlayerEntity victim = server.getPlayerManager().getPlayer(victimUUID);
                            if (victim != null) {
                                freezePlayer(victim, false);
                            }

                            if (siker != null) {
                                freezePlayer(siker, false);
                                StrongSlapHandler.sendLookLockPublic(siker, false);
                            }

                            if (victim != null) {
                                ServerWorld world = (ServerWorld)victim.getWorld();
                                Vec3d head = victim.getPos().add(0.0, 1.7, 0.0);
                                world.spawnParticles(ParticleTypes.field_11221, head.x, head.y, head.z, 3, 0.2, 0.2, 0.2, 0.0);
                                world.spawnParticles(ParticleTypes.field_11205, head.x, head.y, head.z, 20, 0.3, 0.3, 0.3, 0.25);
                                world.spawnParticles(ParticleTypes.field_11208, head.x, head.y, head.z, 15, 0.25, 0.25, 0.25, 0.15);
                                world.spawnParticles(ParticleTypes.field_11227, head.x, head.y, head.z, 6, 0.2, 0.1, 0.2, 0.05);
                                world.playSound(
                                    null,
                                    head.x,
                                    head.y,
                                    head.z,
                                    (SoundEvent)SoundEvents.field_15152.comp_349(),
                                    SoundCategory.field_15248,
                                    1.5F,
                                    0.9F
                                );
                                world.playSound(
                                    null, head.x, head.y, head.z, SoundEvents.field_15016, SoundCategory.field_15248, 1.2F, 0.6F
                                );
                                Vec3d launchDir;
                                if (siker != null) {
                                    launchDir = victim.getPos().subtract(siker.getPos()).normalize();
                                    if (launchDir.lengthSquared() < 0.001) {
                                        launchDir = new Vec3d(1.0, 0.0, 0.0);
                                    }
                                } else {
                                    launchDir = victim.getRotationVec(1.0F);
                                }

                                victim.setVelocity(launchDir.x * 1.1F, 0.6, launchDir.z * 1.1F);
                                victim.velocityModified = true;
                                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5916, 100, 1, false, true));
                                victim.damage(world.getDamageSources().generic(), 4.0F);
                            }

                            if (siker != null) {
                                PoseNetworking.broadcastAnimState(siker, 0);
                            }

                            cleanupSession(sikerUUID, victimUUID);
                        }
                    }
                );
            }
        }
    }

    private static void cleanupSession(UUID sikerId, UUID victimId) {
        sessions.remove(sikerId);
        animSessions.remove(sikerId);
        pendingQTE.remove(sikerId);
        qteOpenedAt.remove(sikerId);
    }

    public static void onPlayerLeave(UUID id) {
        onPlayerLeave(id, null);
    }

    public static void onPlayerLeave(UUID id, MinecraftServer server) {
        if (pendingQTE.remove(id) != null && server != null) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) {
                ServerPlayNetworking.send(p, new SikeFollowUpHandler.SikeWindowPayload(false, 0, 0));
            }
        }

        qteOpenedAt.remove(id);
        pendingQTE.entrySet().removeIf(e -> {
            if (!e.getValue().equals(id)) {
                return false;
            } else {
                qteOpenedAt.remove(e.getKey());
                if (server != null) {
                    ServerPlayerEntity sk = server.getPlayerManager().getPlayer(e.getKey());
                    if (sk != null) {
                        ServerPlayNetworking.send(sk, new SikeFollowUpHandler.SikeWindowPayload(false, 0, 0));
                    }
                }

                return true;
            }
        });
        Set<SikeFollowUpHandler.NoyaSession> hit = new HashSet<>();

        for (SikeFollowUpHandler.NoyaSession s : sessions.values()) {
            if (s.sikerId.equals(id) || s.victimId.equals(id)) {
                hit.add(s);
            }
        }

        for (SikeFollowUpHandler.NoyaSession sx : animSessions.values()) {
            if (sx.sikerId.equals(id) || sx.victimId.equals(id)) {
                hit.add(sx);
            }
        }

        for (SikeFollowUpHandler.NoyaSession sxx : hit) {
            sxx.cancelled = true;
            sessions.remove(sxx.sikerId);
            animSessions.remove(sxx.sikerId);
            releaseSession(server, sxx);
        }
    }

    private static void releaseSession(MinecraftServer server, SikeFollowUpHandler.NoyaSession s) {
        if (server != null) {
            ServerPlayerEntity siker = server.getPlayerManager().getPlayer(s.sikerId);
            ServerPlayerEntity victim = server.getPlayerManager().getPlayer(s.victimId);
            if (siker != null) {
                freezePlayer(siker, false);
                StrongSlapHandler.sendLookLockPublic(siker, false);
                PoseNetworking.broadcastAnimState(siker, 0);
                ServerPlayNetworking.send(siker, new SikeFollowUpHandler.SikeWindowPayload(false, 0, 0));
            }

            if (victim != null) {
                freezePlayer(victim, false);
                ServerPlayNetworking.send(victim, new SikeFollowUpHandler.NoyaFlickerPayload(s.victimId, false, 0L));
            }
        }
    }

    private static void freezePlayer(ServerPlayerEntity player, boolean frozen) {
        for (ServerPlayerEntity other : PlayerLookup.all(player.getServer())) {
            ServerPlayNetworking.send(other, new HighFiveHandler.FreezeStatePayload(player.getUuid(), frozen));
        }
    }

    public record NoyaFlickerPayload(UUID victimId, boolean start, long durationMs) implements CustomPayload {
        public static final Id<SikeFollowUpHandler.NoyaFlickerPayload> ID = new Id(new Identifier("testcoop", "noya_flicker"));
        public static final PacketCodec<PacketByteBuf, SikeFollowUpHandler.NoyaFlickerPayload> CODEC = PacketCodec.of((v, buf) -> {
            buf.writeUuid(v.victimId());
            buf.writeBoolean(v.start());
            buf.writeLong(v.durationMs());
        }, buf -> new SikeFollowUpHandler.NoyaFlickerPayload(buf.readUuid(), buf.readBoolean(), buf.readLong()));

        public Id<SikeFollowUpHandler.NoyaFlickerPayload> getId() {
            return ID;
        }
    }

    private static class NoyaSession {
        final UUID sikerId;
        final UUID victimId;
        int tpTick = 0;
        boolean tpDone = false;
        boolean animRunning = false;
        volatile boolean cancelled = false;

        NoyaSession(UUID siker, UUID victim) {
            this.sikerId = siker;
            this.victimId = victim;
        }
    }

    public record SikePressPayload() implements CustomPayload {
        public static final Id<SikeFollowUpHandler.SikePressPayload> ID = new Id(new Identifier("testcoop", "sike_press"));
        public static final PacketCodec<PacketByteBuf, SikeFollowUpHandler.SikePressPayload> CODEC = PacketCodec.unit(
            new SikeFollowUpHandler.SikePressPayload()
        );

        public Id<SikeFollowUpHandler.SikePressPayload> getId() {
            return ID;
        }
    }

    public record SikeWindowPayload(boolean open, int durationMs, int promptDelayMs) implements CustomPayload {
        public static final Id<SikeFollowUpHandler.SikeWindowPayload> ID = new Id(new Identifier("testcoop", "sike_window"));
        public static final PacketCodec<PacketByteBuf, SikeFollowUpHandler.SikeWindowPayload> CODEC = PacketCodec.of((v, buf) -> {
            buf.writeBoolean(v.open());
            buf.writeInt(v.durationMs());
            buf.writeInt(v.promptDelayMs());
        }, buf -> new SikeFollowUpHandler.SikeWindowPayload(buf.readBoolean(), buf.readInt(), buf.readInt()));

        public Id<SikeFollowUpHandler.SikeWindowPayload> getId() {
            return ID;
        }
    }
}
