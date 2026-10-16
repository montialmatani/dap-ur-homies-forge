package com.cooptest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class DuoPoseHandler {
    private static final long WINDOW_MS = 3000L;
    private static final long HOLD_REQUIRED_MS = 200L;
    private static final double RANGE = 5.0;
    private static final long DUO_POSE_MS = 1125L;
    private static final long PARTICLE_HIT_MS = 670L;
    private static final long AURA_INTERVAL_MS = 300L;
    private static final long DUO_POSE_END_MS = 1625L;
    private static final long RELEASE_GRACE_MS = 500L;
    public static final int ANIM_DUO_POSE = 95;
    public static final int ANIM_DUO_POSE_IDLE = 96;
    public static final int ANIM_DUO_POSE_END = 97;
    public static final int ANIM_NONE = 0;
    private static final Map<UUID, DuoPoseHandler.DuoSession> sessions = new HashMap<>();
    private static final Map<UUID, DuoPoseHandler.DuoSession> playerIndex = new HashMap<>();
    private static final Map<UUID, Long> windowOpenAt = new HashMap<>();
    private static final Map<UUID, UUID> windowPartner = new HashMap<>();
    private static final Map<UUID, Long> fHoldSince = new HashMap<>();
    private static final Map<UUID, Long> huddleBlockUntil = new HashMap<>();

    public static boolean isBlockingHuddle(UUID id) {
        Long until = huddleBlockUntil.get(id);
        if (until == null) {
            return false;
        } else if (System.currentTimeMillis() > until) {
            huddleBlockUntil.remove(id);
            return false;
        } else {
            return true;
        }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(DuoPoseHandler.DuoPoseFreezePayload.ID, DuoPoseHandler.DuoPoseFreezePayload.CODEC);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DuoPoseHandler::tick);
    }

    public static void onDapEnded(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        openWindow(p1, p2);
    }

    public static void onComboEnded(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        new Thread(() -> {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException var3) {
            }

            if (p1.getServer() != null) {
                p1.getServer().execute(() -> openWindow(p1, p2));
            }
        }).start();
    }

    private static void openWindow(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        if (!playerIndex.containsKey(id1) && !playerIndex.containsKey(id2)) {
            long now = System.currentTimeMillis();
            windowOpenAt.put(id1, now);
            windowOpenAt.put(id2, now);
            windowPartner.put(id1, id2);
            windowPartner.put(id2, id1);
            huddleBlockUntil.put(id1, now + 3000L);
            huddleBlockUntil.put(id2, now + 3000L);
        }
    }

    public static void onFHold(ServerPlayerEntity player, boolean holding) {
        UUID id = player.getUuid();
        DuoPoseHandler.DuoSession active = playerIndex.get(id);
        if (active != null && active.stage == DuoPoseHandler.Stage.IDLE) {
            if (holding) {
                active.holdingF.add(id);
            } else {
                active.holdingF.remove(id);
                if (active.firstRelease == null) {
                    active.firstRelease = id;
                    active.firstReleaseMs = System.currentTimeMillis();
                }
            }
        } else if (windowOpenAt.containsKey(id)) {
            if (holding) {
                fHoldSince.putIfAbsent(id, System.currentTimeMillis());
            } else {
                fHoldSince.remove(id);
            }
        }
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        List<UUID> expiredWindows = new ArrayList<>();
        Set<UUID> processed = new HashSet<>();

        for (UUID id : new ArrayList<>(windowOpenAt.keySet())) {
            if (!processed.contains(id)) {
                Long openAt = windowOpenAt.get(id);
                if (openAt != null) {
                    if (now - openAt > 3000L) {
                        expiredWindows.add(id);
                        UUID partner = windowPartner.get(id);
                        if (partner != null) {
                            expiredWindows.add(partner);
                        }
                    } else {
                        UUID partnerId = windowPartner.get(id);
                        if (partnerId != null) {
                            processed.add(id);
                            processed.add(partnerId);
                            Long holdA = fHoldSince.get(id);
                            Long holdB = fHoldSince.get(partnerId);
                            if (holdA != null && holdB != null && now - holdA >= 200L && now - holdB >= 200L) {
                                ServerPlayerEntity p1 = server.getPlayerManager().getPlayer(id);
                                ServerPlayerEntity p2 = server.getPlayerManager().getPlayer(partnerId);
                                if (p1 != null && p2 != null) {
                                    if (!(p1.getPos().distanceTo(p2.getPos()) > 5.0)) {
                                        expiredWindows.add(id);
                                        expiredWindows.add(partnerId);
                                        fHoldSince.remove(id);
                                        fHoldSince.remove(partnerId);
                                        startDuoPose(server, p1, p2, now);
                                    }
                                } else {
                                    expiredWindows.add(id);
                                    expiredWindows.add(partnerId);
                                }
                            }
                        }
                    }
                }
            }
        }

        expiredWindows.forEach(idx -> {
            windowOpenAt.remove(idx);
            windowPartner.remove(idx);
            fHoldSince.remove(idx);
        });
        Set<DuoPoseHandler.DuoSession> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        for (DuoPoseHandler.DuoSession s : new ArrayList<>(sessions.values())) {
            if (seen.add(s)) {
                tickSession(s, server, now);
            }
        }
    }

    private static void startDuoPose(MinecraftServer server, ServerPlayerEntity p1, ServerPlayerEntity p2, long now) {
        DuoPoseHandler.DuoSession session = new DuoPoseHandler.DuoSession(p1.getUuid(), p2.getUuid());
        sessions.put(p1.getUuid(), session);
        playerIndex.put(p1.getUuid(), session);
        playerIndex.put(p2.getUuid(), session);
        sendFreeze(p1, true);
        sendFreeze(p2, true);
        PoseNetworking.broadcastAnimState(p1, 95);
        PoseNetworking.broadcastAnimState(p2, 95);
        session.holdingF.add(p1.getUuid());
        session.holdingF.add(p2.getUuid());
        new Thread(() -> {
            try {
                Thread.sleep(670L);
            } catch (InterruptedException var3) {
                return;
            }

            server.execute(() -> {
                if (sessions.containsKey(session.p1)) {
                    spawnArmParticles(server, session);
                }
            });
        }).start();
        new Thread(() -> {
            try {
                Thread.sleep(1125L);
            } catch (InterruptedException var3) {
                return;
            }

            server.execute(() -> {
                if (sessions.containsKey(session.p1)) {
                    session.stage = DuoPoseHandler.Stage.IDLE;
                    ServerPlayerEntity a = server.getPlayerManager().getPlayer(session.p1);
                    ServerPlayerEntity b = server.getPlayerManager().getPlayer(session.p2);
                    if (a != null) {
                        PoseNetworking.broadcastAnimState(a, 96);
                    }

                    if (b != null) {
                        PoseNetworking.broadcastAnimState(b, 96);
                    }
                }
            });
        }).start();
    }

    private static void tickSession(DuoPoseHandler.DuoSession s, MinecraftServer server, long now) {
        ServerPlayerEntity p1 = server.getPlayerManager().getPlayer(s.p1);
        ServerPlayerEntity p2 = server.getPlayerManager().getPlayer(s.p2);
        if (p1 != null && p2 != null) {
            if (s.stage == DuoPoseHandler.Stage.IDLE) {
                p1.setVelocity(Vec3d.ZERO);
                p1.velocityModified = true;
                p2.setVelocity(Vec3d.ZERO);
                p2.velocityModified = true;
                if (now - s.lastAuraMs >= 300L) {
                    s.lastAuraMs = now;
                    spawnAuraParticles(server, s);
                }

                boolean p1Holds = s.holdingF.contains(s.p1);
                boolean p2Holds = s.holdingF.contains(s.p2);
                if (!p1Holds && !p2Holds) {
                    boolean simul = s.firstRelease != null && now - s.firstReleaseMs <= 500L;
                    s.simultaneous = simul;
                    endDuoPose(server, s, p1, p2);
                } else if ((!p1Holds || !p2Holds) && s.firstRelease != null && now - s.firstReleaseMs > 500L) {
                    s.simultaneous = false;
                    endDuoPose(server, s, p1, p2);
                }
            }
        } else {
            cleanupSession(s);
        }
    }

    private static void endDuoPose(MinecraftServer server, DuoPoseHandler.DuoSession s, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        s.stage = DuoPoseHandler.Stage.ENDING;
        PoseNetworking.broadcastAnimState(p1, 97);
        PoseNetworking.broadcastAnimState(p2, 97);
        if (s.simultaneous) {
            Vec3d mid = p1.getPos().add(p2.getPos()).multiply(0.5).add(0.0, 1.0, 0.0);
            ServerWorld world = p1.getServerWorld();
            world.spawnParticles(ParticleTypes.field_11208, mid.x, mid.y, mid.z, 30, 0.5, 0.5, 0.5, 0.15);
            world.spawnParticles(ParticleTypes.field_17909, mid.x, mid.y, mid.z, 5, 0.3, 0.3, 0.3, 0.0);
            world.spawnParticles(ParticleTypes.field_11205, mid.x, mid.y, mid.z, 20, 0.4, 0.4, 0.4, 0.2);
            world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.field_15016, SoundCategory.field_15248, 1.5F, 1.2F);
            world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.field_14703, SoundCategory.field_15248, 1.0F, 1.5F);
            p1.sendMessage(Text.literal("§6§l✨ Perfect sync!"), true);
            p2.sendMessage(Text.literal("§6§l✨ Perfect sync!"), true);
        }

        new Thread(() -> {
            try {
                Thread.sleep(1625L);
            } catch (InterruptedException var3) {
            }

            server.execute(() -> {
                ServerPlayerEntity a = server.getPlayerManager().getPlayer(s.p1);
                ServerPlayerEntity b = server.getPlayerManager().getPlayer(s.p2);
                if (a != null) {
                    PoseNetworking.broadcastAnimState(a, 0);
                    sendFreeze(a, false);
                }

                if (b != null) {
                    PoseNetworking.broadcastAnimState(b, 0);
                    sendFreeze(b, false);
                }

                cleanupSession(s);
            });
        }).start();
    }

    private static void spawnArmParticles(MinecraftServer server, DuoPoseHandler.DuoSession s) {
        for (UUID id : new UUID[]{s.p1, s.p2}) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) {
                ServerWorld world = p.getServerWorld();
                Vec3d look = p.getRotationVec(1.0F);
                Vec3d right = new Vec3d(-look.z, 0.0, look.x).normalize();
                Vec3d armTip = p.getEyePos().add(right.multiply(0.55)).add(look.multiply(0.4)).subtract(0.0, 0.4, 0.0);
                world.spawnParticles(ParticleTypes.field_11208, armTip.x, armTip.y, armTip.z, 10, 0.12, 0.12, 0.12, 0.1);
                world.spawnParticles(ParticleTypes.field_11205, armTip.x, armTip.y, armTip.z, 8, 0.1, 0.1, 0.1, 0.15);
                world.playSound(null, armTip.x, armTip.y, armTip.z, SoundEvents.field_15016, SoundCategory.field_15248, 1.0F, 1.3F);
            }
        }
    }

    private static void spawnAuraParticles(MinecraftServer server, DuoPoseHandler.DuoSession s) {
        for (UUID id : new UUID[]{s.p1, s.p2}) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) {
                ServerWorld world = p.getServerWorld();
                Vec3d pos = p.getPos().add(0.0, 1.0, 0.0);
                world.spawnParticles(ParticleTypes.field_11208, pos.x, pos.y, pos.z, 3, 0.3, 0.5, 0.3, 0.02);
                world.spawnParticles(ParticleTypes.field_11207, pos.x, pos.y, pos.z, 2, 0.25, 0.6, 0.25, 0.01);
            }
        }
    }

    private static void cleanupSession(DuoPoseHandler.DuoSession s) {
        sessions.remove(s.p1);
        playerIndex.remove(s.p1);
        playerIndex.remove(s.p2);
    }

    public static void onPlayerLeave(UUID id) {
        UUID partner = windowPartner.remove(id);
        if (partner != null) {
            windowOpenAt.remove(partner);
            windowPartner.remove(partner);
            fHoldSince.remove(partner);
        }

        windowOpenAt.remove(id);
        fHoldSince.remove(id);
        DuoPoseHandler.DuoSession s = playerIndex.remove(id);
        if (s != null) {
            s.stage = DuoPoseHandler.Stage.ENDING;
            sessions.remove(s.p1);
            playerIndex.remove(s.p1 == id ? s.p2 : s.p1);
        }
    }

    public static boolean isInDuoPose(UUID id) {
        return playerIndex.containsKey(id);
    }

    private static void sendFreeze(ServerPlayerEntity player, boolean frozen) {
        ServerPlayNetworking.send(player, new DuoPoseHandler.DuoPoseFreezePayload(player.getUuid(), frozen));
    }

    public record DuoPoseFreezePayload(UUID playerId, boolean frozen) implements CustomPayload {
        public static final Id<DuoPoseHandler.DuoPoseFreezePayload> ID = new Id(new Identifier("testcoop", "duo_pose_freeze"));
        public static final PacketCodec<PacketByteBuf, DuoPoseHandler.DuoPoseFreezePayload> CODEC = PacketCodec.of((v, buf) -> {
            buf.writeUuid(v.playerId());
            buf.writeBoolean(v.frozen());
        }, buf -> new DuoPoseHandler.DuoPoseFreezePayload(buf.readUuid(), buf.readBoolean()));

        public Id<DuoPoseHandler.DuoPoseFreezePayload> getId() {
            return ID;
        }
    }

    private static class DuoSession {
        final UUID p1;
        final UUID p2;
        DuoPoseHandler.Stage stage = DuoPoseHandler.Stage.ENTRY;
        final Set<UUID> holdingF = new HashSet<>();
        UUID firstRelease = null;
        long firstReleaseMs = 0L;
        boolean simultaneous = false;
        long lastAuraMs = 0L;

        DuoSession(UUID p1, UUID p2) {
            this.p1 = p1;
            this.p2 = p2;
        }

        boolean bothHoldingF() {
            return this.holdingF.contains(this.p1) && this.holdingF.contains(this.p2);
        }
    }

    private static enum Stage {
        ENTRY,
        IDLE,
        ENDING;
    }
}
