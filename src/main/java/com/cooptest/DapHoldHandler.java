package com.cooptest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Hand;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
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

public class DapHoldHandler {
    private static final long ANIM_LENGTH_MS = 1042L;
    private static final long J_WINDOW_START_MS = 330L;
    private static final long IMPACT_MS = 420L;
    private static final long J_WINDOW_END_MS = 1330L;
    private static final double STOP_DISTANCE = 1.5;
    private static final double TP_SPEED = 0.08;
    private static final Map<UUID, UUID> activePairs = new HashMap<>();
    private static final Map<UUID, Long> pairStartTime = new HashMap<>();
    private static final Set<UUID> windowOpen = new HashSet<>();
    private static final Set<UUID> impactFired = new HashSet<>();
    private static final Set<UUID> looping = new HashSet<>();
    private static final Set<UUID> endingAnimation = new HashSet<>();
    private static final Map<UUID, Long> jHoldLastTick = new HashMap<>();
    private static final Map<UUID, Long> loopStartTime = new HashMap<>();
    private static final Map<UUID, ArmorStandEntity> handStands = new HashMap<>();
    private static final Set<UUID> tpComplete = new HashSet<>();
    private static final Map<UUID, Set<UUID>> groupJoiners = new HashMap<>();
    private static final Map<UUID, UUID> joinerGroup = new HashMap<>();
    private static final Map<UUID, Long> joinerJLast = new HashMap<>();
    private static final Map<UUID, Long> releaseFirst = new HashMap<>();
    private static final Map<UUID, Set<UUID>> releasedSet = new HashMap<>();
    private static final double GROUP_JOIN_RADIUS = 2.5;
    private static final long RELEASE_WINDOW_MS = 500L;

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(DapHoldHandler.DapHoldStartPayload.ID, DapHoldHandler.DapHoldStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldHandler.DapHoldWindowPayload.ID, DapHoldHandler.DapHoldWindowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldHandler.DapHoldLoopPayload.ID, DapHoldHandler.DapHoldLoopPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldHandler.DapHoldEndPayload.ID, DapHoldHandler.DapHoldEndPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldHandler.DapHoldFreezePayload.ID, DapHoldHandler.DapHoldFreezePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldHandler.GroupJoinedPayload.ID, DapHoldHandler.GroupJoinedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapHoldHandler.GroupResultPayload.ID, DapHoldHandler.GroupResultPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DapHoldHandler.DapHoldJHoldPayload.ID, DapHoldHandler.DapHoldJHoldPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DapHoldHandler.DapHoldJReleasePayload.ID, DapHoldHandler.DapHoldJReleasePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DapHoldHandler.GroupJoinPayload.ID, DapHoldHandler.GroupJoinPayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(DapHoldHandler.DapHoldJHoldPayload.ID, (payload, ctx) -> ctx.server().execute(() -> onJHold(ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(
            DapHoldHandler.DapHoldJReleasePayload.ID, (payload, ctx) -> ctx.server().execute(() -> onJRelease(ctx.player()))
        );
        ServerPlayNetworking.registerGlobalReceiver(DapHoldHandler.GroupJoinPayload.ID, (payload, ctx) -> ctx.server().execute(() -> {
            ServerPlayerEntity player = ctx.player();
            UUID id = player.getUuid();
            if (!isInDapHold(id)) {
                tryJoinGroup(player, System.currentTimeMillis());
            }
        }));
        ServerTickEvents.END_SERVER_TICK.register(DapHoldHandler::onServerTick);
    }

    private static void makeFaceEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        Vec3d p1Pos = p1.getPos();
        Vec3d p2Pos = p2.getPos();
        p1.swingHand(Hand.MAIN_HAND);
        p2.swingHand(Hand.MAIN_HAND);
        double dx = p2Pos.x - p1Pos.x;
        double dz = p2Pos.z - p1Pos.z;
        float yawP1 = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float yawP2 = yawP1 + 180.0F;
        p1.setYaw(yawP1);
        p1.setBodyYaw(yawP1);
        p1.setHeadYaw(yawP1);
        p1.teleport(p1.getServerWorld(), p1Pos.x, p1Pos.y, p1Pos.z, yawP1, 0.0F);
        p2.setYaw(yawP2);
        p2.setBodyYaw(yawP2);
        p2.setHeadYaw(yawP2);
        p2.teleport(p2.getServerWorld(), p2Pos.x, p2Pos.y, p2Pos.z, yawP2, 0.0F);
    }

    private static boolean arePlayersFacingEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        Vec3d p1Pos = p1.getPos();
        Vec3d p2Pos = p2.getPos();
        Vec3d directionTo = p2Pos.subtract(p1Pos).normalize();
        Vec3d p1Looking = p1.getRotationVector();
        double dot1 = p1Looking.dotProduct(directionTo);
        if (dot1 < 0.85) {
            return false;
        } else {
            Vec3d directionBack = p1Pos.subtract(p2Pos).normalize();
            Vec3d p2Looking = p2.getRotationVector();
            double dot2 = p2Looking.dotProduct(directionBack);
            return dot2 >= 0.85;
        }
    }

    public static void startDapHold(ServerPlayerEntity hfPlayer, ServerPlayerEntity dapPlayer) {
        UUID hfId = hfPlayer.getUuid();
        UUID dapId = dapPlayer.getUuid();
        if (!isInDapHold(hfId) && !isInDapHold(dapId)) {
            if (!arePlayersFacingEachOther(hfPlayer, dapPlayer)) {
                hfPlayer.sendMessage(Text.literal("§cNot facing each other!"), true);
                dapPlayer.sendMessage(Text.literal("§cNot facing each other!"), true);
            } else {
                HighFiveHandler.handRaisedTime.remove(hfId);
                HighFiveHandler.startAnimTime.remove(hfId);
                HighFiveHandler.syncHandRaised(hfPlayer, false);
                DapSession session = DapSessionManager.createSession(hfId, dapId, 1.5, DapSession.DapType.PERFECT_DAP);
                activePairs.put(hfId, dapId);
                pairStartTime.put(hfId, System.currentTimeMillis());
                sendFreeze(hfPlayer.getServer(), hfId, true);
                sendFreeze(hfPlayer.getServer(), dapId, true);
                spawnHandStand(hfPlayer, dapPlayer);
                sendToAll(hfPlayer.getServer(), new DapHoldHandler.DapHoldStartPayload(hfId, dapId, 0));
                sendToAll(hfPlayer.getServer(), new DapHoldHandler.DapHoldStartPayload(dapId, hfId, 1));
                PoseNetworking.broadcastAnimState(hfPlayer, 38);
                PoseNetworking.broadcastAnimState(dapPlayer, 39);
            }
        }
    }

    private static void onServerTick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Set<UUID> toCleanup = new HashSet<>();

        for (Entry<UUID, UUID> entry : activePairs.entrySet()) {
            UUID hfId = entry.getKey();
            UUID dapId = entry.getValue();
            ServerPlayerEntity hfPlayer = server.getPlayerManager().getPlayer(hfId);
            ServerPlayerEntity dapPlayer = server.getPlayerManager().getPlayer(dapId);
            if (hfPlayer != null && dapPlayer != null) {
                Long startMs = pairStartTime.get(hfId);
                if (startMs == null) {
                    toCleanup.add(hfId);
                } else {
                    long elapsed = now - startMs;
                    if (!tpComplete.contains(hfId)) {
                        tpComplete.add(hfId);
                    }

                    updateHandStand(hfPlayer, dapPlayer, hfId);
                    if (elapsed % 500L < 50L) {
                        hfPlayer.swingHand(Hand.MAIN_HAND);
                        dapPlayer.swingHand(Hand.MAIN_HAND);
                    }

                    if (!windowOpen.contains(hfId) && elapsed >= 330L) {
                        windowOpen.add(hfId);
                        sendToAll(server, new DapHoldHandler.DapHoldWindowPayload(true));
                        hfPlayer.sendMessage(Text.literal("§e⚡ HOLD J "), true);
                        dapPlayer.sendMessage(Text.literal("§e⚡ HOLD J "), true);
                    }

                    if (!impactFired.contains(hfId) && elapsed >= 420L) {
                        impactFired.add(hfId);
                        spawnImpactParticles(hfPlayer, dapPlayer, hfId);
                    }

                    if (windowOpen.contains(hfId) && !looping.contains(hfId) && !endingAnimation.contains(hfId) && elapsed >= 1330L) {
                        if (isHoldingJ(hfId, now) && isHoldingJ(dapId, now)) {
                            looping.add(hfId);
                            loopStartTime.put(hfId, now);
                            DapSessionManager.removeSession(hfId);
                            sendToAll(server, new DapHoldHandler.DapHoldLoopPayload(true));
                            if (hfPlayer != null && dapPlayer != null) {
                                PoseNetworking.broadcastAnimState(hfPlayer, 40);
                                PoseNetworking.broadcastAnimState(dapPlayer, 40);
                            }
                        } else {
                            endingAnimation.add(hfId);
                            sendToAll(server, new DapHoldHandler.DapHoldWindowPayload(false));
                            doUnfreeze(server, hfId, dapId);
                        }
                    }

                    if (endingAnimation.contains(hfId) && elapsed >= 1042L) {
                        sendToAll(server, new DapHoldHandler.DapHoldEndPayload(false));
                        toCleanup.add(hfId);
                    }
                }
            } else {
                toCleanup.add(hfId);
            }
        }

        Set<UUID> groupResultNeeded = new HashSet<>();

        for (UUID hfId : looping) {
            ServerPlayerEntity hfPlayer = server.getPlayerManager().getPlayer(hfId);
            UUID dapId = activePairs.get(hfId);
            ServerPlayerEntity dapPlayer = server.getPlayerManager().getPlayer(dapId);
            if (hfPlayer != null && dapPlayer != null) {
                ServerWorld world = hfPlayer.getServerWorld();
                ArmorStandEntity stand = handStands.get(hfId);
                if (stand != null && !stand.isRemoved()) {
                    Vec3d impactPos = stand.getPos();
                    world.spawnParticles(ParticleTypes.CRIT, impactPos.x, impactPos.y, impactPos.z, 2, 0.1, 0.1, 0.1, 0.02);
                }

                Set<UUID> joiners = groupJoiners.get(hfId);
                if (joiners != null && !joiners.isEmpty()) {
                    Long first = releaseFirst.get(hfId);
                    if (first != null && now - first > 500L) {
                        groupResultNeeded.add(hfId);
                    } else {
                        Set<UUID> toEvict = new HashSet<>();

                        for (UUID jId : joiners) {
                            Long lastJ = joinerJLast.get(jId);
                            if (lastJ == null || now - lastJ > 300L) {
                                toEvict.add(jId);
                            }
                        }

                        for (UUID jIdx : toEvict) {
                            joiners.remove(jIdx);
                            joinerGroup.remove(jIdx);
                            joinerJLast.remove(jIdx);
                            sendFreeze(server, jIdx, false);
                            ServerPlayerEntity jp = server.getPlayerManager().getPlayer(jIdx);
                            if (jp != null) {
                                PoseNetworking.broadcastAnimState(jp, 41);
                                jp.sendMessage(Text.literal("§7Left the group"), true);
                            }
                        }

                        if (server.getTicks() % 4 == 0) {
                            faceGroupCenter(hfId, server);
                            ServerPlayerEntity hfP2 = server.getPlayerManager().getPlayer(hfId);
                            UUID dapId2 = activePairs.get(hfId);
                            ServerPlayerEntity dapP2 = server.getPlayerManager().getPlayer(dapId2);
                            if (hfP2 != null) {
                                hfP2.setHeadYaw(hfP2.getBodyYaw());
                            }

                            if (dapP2 != null) {
                                dapP2.setHeadYaw(dapP2.getBodyYaw());
                            }

                            for (UUID jIdxx : joiners) {
                                ServerPlayerEntity jp = server.getPlayerManager().getPlayer(jIdxx);
                                if (jp != null) {
                                    jp.setHeadYaw(jp.getBodyYaw());
                                }
                            }
                        }

                        Vec3d mid = getGroupMidpoint(hfId, server);
                        int chargeParticles = joiners.size() + 1;
                        world.spawnParticles(ParticleTypes.ENCHANTED_HIT, mid.x, mid.y + 1.2, mid.z, chargeParticles, 0.3, 0.2, 0.3, 0.05);
                    }
                }
            }
        }

        for (UUID hfIdx : groupResultNeeded) {
            if (looping.contains(hfIdx)) {
                doGroupResult(hfIdx, server, false);
            }
        }

        toCleanup.forEach(hfIdxx -> cleanupPair(hfIdxx, server));
    }

    private static void onJHold(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        long now = System.currentTimeMillis();
        UUID hfId = getPairHfId(id);
        if (hfId != null && windowOpen.contains(hfId)) {
            jHoldLastTick.put(id, now);
        } else if (joinerGroup.containsKey(id)) {
            joinerJLast.put(id, now);
        } else {
            if (hfId == null) {
                tryJoinGroup(player, now);
            }
        }
    }

    private static void onJRelease(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        jHoldLastTick.remove(id);
        joinerJLast.remove(id);
        UUID joinerHfId = joinerGroup.get(id);
        if (joinerHfId != null) {
            logGroupRelease(id, joinerHfId, player.getServer());
        } else {
            UUID hfId = getPairHfId(id);
            if (hfId != null && looping.contains(hfId)) {
                MinecraftServer server = player.getServer();
                if (server != null) {
                    if (groupJoiners.containsKey(hfId) && !groupJoiners.get(hfId).isEmpty()) {
                        logGroupRelease(id, hfId, server);
                    } else {
                        UUID dapId = activePairs.get(hfId);
                        looping.remove(hfId);
                        loopStartTime.remove(hfId);
                        doUnfreeze(server, hfId, dapId);
                        sendToAll(server, new DapHoldHandler.DapHoldEndPayload(true));
                        ServerPlayerEntity hfP = server.getPlayerManager().getPlayer(hfId);
                        ServerPlayerEntity dapP = server.getPlayerManager().getPlayer(dapId);
                        if (hfP != null) {
                            PoseNetworking.broadcastAnimState(hfP, 41);
                        }

                        if (dapP != null) {
                            PoseNetworking.broadcastAnimState(dapP, 41);
                        }

                        pairStartTime.put(hfId, System.currentTimeMillis() + 100L - 1042L);
                    }
                }
            }
        }
    }

    private static void tryJoinGroup(ServerPlayerEntity player, long now) {
        UUID id = player.getUuid();

        for (UUID hfId : looping) {
            UUID dapId = activePairs.get(hfId);
            if (!hfId.equals(id) && (dapId == null || !dapId.equals(id))) {
                Vec3d mid = getGroupMidpoint(hfId, player.getServer());
                if (!(player.getPos().distanceTo(mid) > 2.5)) {
                    addGroupJoiner(player, hfId);
                    return;
                }
            }
        }
    }

    private static void addGroupJoiner(ServerPlayerEntity joiner, UUID hfId) {
        UUID id = joiner.getUuid();
        MinecraftServer server = joiner.getServer();
        groupJoiners.computeIfAbsent(hfId, k -> new HashSet<>()).add(id);
        joinerGroup.put(id, hfId);
        joinerJLast.put(id, System.currentTimeMillis());
        sendFreeze(server, id, true);
        PoseNetworking.broadcastAnimState(joiner, 38);
        int total = 2 + groupJoiners.get(hfId).size();
        DapHoldHandler.GroupJoinedPayload pkt = new DapHoldHandler.GroupJoinedPayload(id, hfId, total);
        sendToAll(server, pkt);
        faceGroupCenter(hfId, server);
        joiner.sendMessage(Text.literal("§a§l⚡ JOINED GROUP DAP! (" + total + " players)"), true);
        ServerPlayerEntity hfP = server.getPlayerManager().getPlayer(hfId);
        if (hfP != null) {
            hfP.sendMessage(Text.literal("§e§l+" + joiner.getName().getString() + " joined! (" + total + " total)"), true);
        }
    }

    private static void logGroupRelease(UUID id, UUID hfId, MinecraftServer server) {
        if (server != null) {
            releasedSet.computeIfAbsent(hfId, k -> new HashSet<>()).add(id);
            if (!releaseFirst.containsKey(hfId)) {
                releaseFirst.put(hfId, System.currentTimeMillis());
            }

            checkGroupRelease(hfId, server);
        }
    }

    private static void checkGroupRelease(UUID hfId, MinecraftServer server) {
        Set<UUID> joiners = groupJoiners.getOrDefault(hfId, Collections.emptySet());
        int total = 2 + joiners.size();
        int released = releasedSet.getOrDefault(hfId, Collections.emptySet()).size();
        long elapsed = System.currentTimeMillis() - releaseFirst.getOrDefault(hfId, Long.MAX_VALUE);
        if (released >= total) {
            doGroupResult(hfId, server, elapsed <= 500L);
        }
    }

    private static void doGroupResult(UUID hfId, MinecraftServer server, boolean perfect) {
        UUID dapId = activePairs.get(hfId);
        Set<UUID> joiners = new HashSet<>(groupJoiners.getOrDefault(hfId, Collections.emptySet()));
        int memberCount = 2 + joiners.size();
        List<ServerPlayerEntity> all = new ArrayList<>();
        ServerPlayerEntity hfP = server.getPlayerManager().getPlayer(hfId);
        ServerPlayerEntity dapP = server.getPlayerManager().getPlayer(dapId);
        if (hfP != null) {
            all.add(hfP);
        }

        if (dapP != null) {
            all.add(dapP);
        }

        for (UUID jId : joiners) {
            ServerPlayerEntity jp = server.getPlayerManager().getPlayer(jId);
            if (jp != null) {
                all.add(jp);
            }
        }

        Vec3d center = all.stream()
            .<Vec3d>map(Entity::getPos)
            .reduce(Vec3d.ZERO, Vec3d::add)
            .multiply(1.0 / Math.max(1, all.size()));
        ServerWorld world = hfP != null ? hfP.getServerWorld() : server.getOverworld();
        if (perfect) {
            for (ServerPlayerEntity p : all) {
                PoseNetworking.broadcastAnimState(p, 68);
            }

            for (ServerPlayerEntity p : all) {
                sendFreeze(server, p.getUuid(), false);
            }

            new Thread(
                    () -> {
                        try {
                            Thread.sleep(1670L);
                        } catch (InterruptedException var6x) {
                        }

                        server.execute(
                            () -> {
                                for (ServerPlayerEntity p : all) {
                                    if (p.isAlive()) {
                                        p.addVelocity(0.0, 0.4 + memberCount * 0.1, 0.0);
                                        p.velocityModified = true;
                                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 120, Math.min(2, memberCount - 1)));
                                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 120, 0));
                                        p.sendMessage(Text.literal("§6§l✨ PERFECT GROUP DAP! §e" + memberCount + " players!"), true);
                                    }
                                }

                                for (int i = 0; i < memberCount * 3; i++) {
                                    double ox = (world.random.nextDouble() - 0.5) * 3.0;
                                    double oz = (world.random.nextDouble() - 0.5) * 3.0;
                                    world.spawnParticles(
                                        ParticleTypes.FIREWORK,
                                        center.x + ox,
                                        center.y + 2.0 + i * 0.5,
                                        center.z + oz,
                                        6,
                                        0.3,
                                        0.1,
                                        0.3,
                                        0.12
                                    );
                                }

                                world.spawnParticles(
                                    ParticleTypes.TOTEM_OF_UNDYING, center.x, center.y + 1.5, center.z, memberCount * 5, 0.6, 0.6, 0.6, 0.3
                                );
                                world.spawnParticles(
                                    ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1.0, center.z, memberCount, 0.4, 0.3, 0.4, 0.0
                                );
                                world.playSound(
                                    null,
                                    center.x,
                                    center.y,
                                    center.z,
                                    ModSounds.EPIC_DAP,
                                    SoundCategory.PLAYERS,
                                    1.5F,
                                    0.9F + memberCount * 0.05F
                                );
                                world.playSound(
                                    null, center.x, center.y, center.z, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 1.2F, 0.8F
                                );
                            }
                        );
                    }
                )
                .start();
        } else {
            for (ServerPlayerEntity p : all) {
                Vec3d dir = p.getPos().subtract(center).normalize();
                if (dir.lengthSquared() < 0.01) {
                    dir = new Vec3d(1.0, 0.0, 0.0);
                }

                p.addVelocity(dir.x * 0.9, 0.3, dir.z * 0.9);
                p.velocityModified = true;
                p.sendMessage(Text.literal("§c❌ Release not synced!"), true);
            }

            world.spawnParticles(ParticleTypes.POOF, center.x, center.y + 1.0, center.z, 12, 0.4, 0.3, 0.4, 0.05);
            world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.6F, 0.8F);
        }

        sendToAll(server, new DapHoldHandler.GroupResultPayload(perfect, memberCount));
        if (!perfect) {
            sendToAll(server, new DapHoldHandler.DapHoldEndPayload(false));
            if (hfP != null) {
                PoseNetworking.broadcastAnimState(hfP, 41);
            }

            if (dapP != null) {
                PoseNetworking.broadcastAnimState(dapP, 41);
            }

            for (UUID jIdx : joiners) {
                ServerPlayerEntity jp = server.getPlayerManager().getPlayer(jIdx);
                if (jp != null) {
                    PoseNetworking.broadcastAnimState(jp, 41);
                }
            }
        }

        for (UUID jIdxx : joiners) {
            sendFreeze(server, jIdxx, false);
            joinerGroup.remove(jIdxx);
            joinerJLast.remove(jIdxx);
        }

        groupJoiners.remove(hfId);
        releaseFirst.remove(hfId);
        releasedSet.remove(hfId);
        looping.remove(hfId);
        loopStartTime.remove(hfId);
        if (dapId != null) {
            doUnfreeze(server, hfId, dapId);
        }

        pairStartTime.put(hfId, System.currentTimeMillis() + 100L - 1042L);
    }

    private static Vec3d getGroupMidpoint(UUID hfId, MinecraftServer server) {
        List<Vec3d> positions = new ArrayList<>();
        ServerPlayerEntity hfP = server.getPlayerManager().getPlayer(hfId);
        UUID dapId = activePairs.get(hfId);
        ServerPlayerEntity dapP = server.getPlayerManager().getPlayer(dapId);
        if (hfP != null) {
            positions.add(hfP.getPos());
        }

        if (dapP != null) {
            positions.add(dapP.getPos());
        }

        for (UUID jId : groupJoiners.getOrDefault(hfId, Collections.emptySet())) {
            ServerPlayerEntity jp = server.getPlayerManager().getPlayer(jId);
            if (jp != null) {
                positions.add(jp.getPos());
            }
        }

        return positions.isEmpty()
            ? Vec3d.ZERO
            : positions.stream().reduce(Vec3d.ZERO, Vec3d::add).multiply(1.0 / positions.size());
    }

    private static void faceGroupCenter(UUID hfId, MinecraftServer server) {
        List<ServerPlayerEntity> members = new ArrayList<>();
        ServerPlayerEntity hfP = server.getPlayerManager().getPlayer(hfId);
        UUID dapId = activePairs.get(hfId);
        ServerPlayerEntity dapP = server.getPlayerManager().getPlayer(dapId);
        if (hfP != null) {
            members.add(hfP);
        }

        if (dapP != null) {
            members.add(dapP);
        }

        for (UUID jId : groupJoiners.getOrDefault(hfId, Collections.emptySet())) {
            ServerPlayerEntity jp = server.getPlayerManager().getPlayer(jId);
            if (jp != null) {
                members.add(jp);
            }
        }

        if (members.size() >= 2) {
            Vec3d center = members.stream()
                .<Vec3d>map(Entity::getPos)
                .reduce(Vec3d.ZERO, Vec3d::add)
                .multiply(1.0 / members.size());

            for (ServerPlayerEntity p : members) {
                Vec3d diff = center.subtract(p.getPos());
                if (!(diff.horizontalLengthSquared() < 0.001)) {
                    float yaw = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
                    p.setYaw(yaw);
                    p.setBodyYaw(yaw);
                    p.setHeadYaw(yaw);
                }
            }
        }
    }

    public static void forceUnfreeze(MinecraftServer server, UUID id) {
        sendFreeze(server, id, false);
    }

    public static UUID getPairHfId(UUID id) {
        if (activePairs.containsKey(id)) {
            return id;
        } else {
            for (Entry<UUID, UUID> e : activePairs.entrySet()) {
                if (e.getValue().equals(id)) {
                    return e.getKey();
                }
            }

            return null;
        }
    }

    private static UUID getPairHfIdPrivate(UUID id) {
        return getPairHfId(id);
    }

    private static boolean isHoldingJ(UUID id, long now) {
        Long last = jHoldLastTick.get(id);
        return last != null && now - last < 200L;
    }

    private static void smoothTP(ServerPlayerEntity hf, ServerPlayerEntity dap, UUID hfId) {
        double dist = hf.getPos().distanceTo(dap.getPos());
        if (dist <= 1.5) {
            tpComplete.add(hfId);
            faceEachOther(hf, dap);
        } else {
            double move = Math.min(0.08, (dist - 1.5) / 2.0);
            Vec3d dir = dap.getPos().subtract(hf.getPos()).normalize();
            Vec3d newHf = hf.getPos().add(dir.multiply(move));
            Vec3d newDap = dap.getPos().add(dir.negate().multiply(move));
            hf.teleport(hf.getServerWorld(), newHf.x, newHf.y, newHf.z, hf.getYaw(), hf.getPitch());
            dap.teleport(dap.getServerWorld(), newDap.x, newDap.y, newDap.z, dap.getYaw(), dap.getPitch());
        }
    }

    private static void faceEachOther(ServerPlayerEntity a, ServerPlayerEntity b) {
        Vec3d diff = b.getPos().subtract(a.getPos());
        float yawA = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
        a.teleport(a.getServerWorld(), a.getX(), a.getY(), a.getZ(), yawA, a.getPitch());
        b.teleport(b.getServerWorld(), b.getX(), b.getY(), b.getZ(), yawA + 180.0F, b.getPitch());
    }

    private static void spawnHandStand(ServerPlayerEntity hf, ServerPlayerEntity dap) {
        ServerWorld world = hf.getServerWorld();
        Vec3d mid = hf.getPos().add(0.0, 1.4, 0.0).add(dap.getPos().add(0.0, 1.4, 0.0)).multiply(0.5);
        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        stand.setPosition(mid.x, mid.y, mid.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        world.spawnEntity(stand);
        handStands.put(hf.getUuid(), stand);
    }

    private static void updateHandStand(ServerPlayerEntity hf, ServerPlayerEntity dap, UUID hfId) {
        ArmorStandEntity stand = handStands.get(hfId);
        if (stand != null && !stand.isRemoved()) {
            Vec3d mid = hf.getPos().add(0.0, 1.4, 0.0).add(dap.getPos().add(0.0, 1.4, 0.0)).multiply(0.5);
            stand.setPosition(mid.x, mid.y, mid.z);
        }
    }

    private static void spawnImpactParticles(ServerPlayerEntity hf, ServerPlayerEntity dap, UUID hfId) {
        ServerWorld world = hf.getServerWorld();
        ArmorStandEntity stand = handStands.get(hfId);
        double x;
        double y;
        double z;
        if (stand != null && !stand.isRemoved()) {
            x = stand.getX();
            y = stand.getY();
            z = stand.getZ();
        } else {
            Vec3d mid = hf.getPos().add(dap.getPos()).multiply(0.5).add(0.0, 1.4, 0.0);
            x = mid.x;
            y = mid.y;
            z = mid.z;
        }

        world.spawnParticles(ParticleTypes.FLASH, x, y, z, 3, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 40, 0.4, 0.4, 0.4, 0.15);
        world.spawnParticles(ParticleTypes.WHITE_ASH, x, y, z, 80, 0.6, 0.6, 0.6, 0.08);
        world.spawnParticles(ParticleTypes.CLOUD, x, y, z, 20, 0.3, 0.3, 0.3, 0.05);
        world.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 5, 0.3, 0.3, 0.3, 0.0);
        double groundY = hf.getY() + 0.1;

        for (double angle = 0.0; angle < 360.0; angle += 8.0) {
            double rad = Math.toRadians(angle);

            for (double r = 0.5; r <= 3.0; r += 0.5) {
                world.spawnParticles(ParticleTypes.END_ROD, x + Math.cos(rad) * r, groundY, z + Math.sin(rad) * r, 2, 0.05, 0.05, 0.05, 0.02);
            }
        }

        world.playSound(null, x, y, z, ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    private static void sendFreeze(MinecraftServer server, UUID targetId, boolean freeze) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, new DapHoldHandler.DapHoldFreezePayload(targetId, freeze));
        }
    }

    private static void doUnfreeze(MinecraftServer server, UUID hfId, UUID dapId) {
        sendFreeze(server, hfId, false);
        sendFreeze(server, dapId, false);
        DapSessionManager.removeSession(hfId);
        ArmorStandEntity stand = handStands.remove(hfId);
        if (stand != null && !stand.isRemoved()) {
            stand.discard();
        }
    }

    private static void sendToAll(MinecraftServer server, CustomPayload payload) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    private static void cleanupPair(UUID hfId, MinecraftServer server) {
        UUID dapId = activePairs.remove(hfId);
        pairStartTime.remove(hfId);
        windowOpen.remove(hfId);
        impactFired.remove(hfId);
        looping.remove(hfId);
        endingAnimation.remove(hfId);
        tpComplete.remove(hfId);
        loopStartTime.remove(hfId);
        jHoldLastTick.remove(hfId);
        if (dapId != null) {
            jHoldLastTick.remove(dapId);
        }

        ArmorStandEntity stand = handStands.remove(hfId);
        if (stand != null && !stand.isRemoved()) {
            stand.discard();
        }

        Set<UUID> joiners = groupJoiners.remove(hfId);
        if (joiners != null) {
            for (UUID jId : joiners) {
                joinerGroup.remove(jId);
                joinerJLast.remove(jId);
                sendFreeze(server, jId, false);
                ServerPlayerEntity jp = server.getPlayerManager().getPlayer(jId);
                if (jp != null) {
                    PoseNetworking.broadcastAnimState(jp, 41);
                }
            }
        }

        releaseFirst.remove(hfId);
        releasedSet.remove(hfId);
        DapSessionManager.removeSession(hfId);
        sendFreeze(server, hfId, false);
        if (dapId != null) {
            sendFreeze(server, dapId, false);
        }

        long now = System.currentTimeMillis();
        ChargedDapHandler.cooldowns.put(hfId, now + 1000L);
        if (dapId != null) {
            ChargedDapHandler.cooldowns.put(dapId, now + 1000L);
        }

        HighFiveHandler.highFiveCooldown.put(hfId, now);
        if (dapId != null) {
            HighFiveHandler.highFiveCooldown.put(dapId, now);
        }
    }

    public static boolean tryDetect(ServerPlayerEntity player, ServerPlayerEntity partner) {
        boolean playerHF = HighFiveHandler.hasHandRaised(player.getUuid());
        boolean partnerHF = HighFiveHandler.hasHandRaised(partner.getUuid());
        if (playerHF && !partnerHF) {
            startDapHold(player, partner);
            return true;
        } else if (partnerHF && !playerHF) {
            startDapHold(partner, player);
            return true;
        } else {
            return false;
        }
    }

    public static boolean isInDapHold(UUID playerId) {
        return activePairs.containsKey(playerId) || activePairs.containsValue(playerId) || joinerGroup.containsKey(playerId);
    }

    public record DapHoldEndPayload(boolean wasLooping) implements CustomPayload {
        public static final Id<DapHoldHandler.DapHoldEndPayload> ID = new Id(new Identifier("cooptest", "daphold_end"));
        public static final PacketCodec<PacketByteBuf, DapHoldHandler.DapHoldEndPayload> CODEC = PacketCodec.of(
            (p, buf) -> buf.writeBoolean(p.wasLooping()), buf -> new DapHoldHandler.DapHoldEndPayload(buf.readBoolean())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record DapHoldFreezePayload(UUID playerId, boolean frozen) implements CustomPayload {
        public static final Id<DapHoldHandler.DapHoldFreezePayload> ID = new Id(new Identifier("cooptest", "daphold_freeze"));
        public static final PacketCodec<PacketByteBuf, DapHoldHandler.DapHoldFreezePayload> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeUuid(p.playerId());
            buf.writeBoolean(p.frozen());
        }, buf -> new DapHoldHandler.DapHoldFreezePayload(buf.readUuid(), buf.readBoolean()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record DapHoldJHoldPayload() implements CustomPayload {
        public static final Id<DapHoldHandler.DapHoldJHoldPayload> ID = new Id(new Identifier("cooptest", "daphold_jhold"));
        public static final PacketCodec<PacketByteBuf, DapHoldHandler.DapHoldJHoldPayload> CODEC = PacketCodec.unit(new DapHoldHandler.DapHoldJHoldPayload());

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record DapHoldJReleasePayload() implements CustomPayload {
        public static final Id<DapHoldHandler.DapHoldJReleasePayload> ID = new Id(new Identifier("cooptest", "daphold_jrelease"));
        public static final PacketCodec<PacketByteBuf, DapHoldHandler.DapHoldJReleasePayload> CODEC = PacketCodec.unit(
            new DapHoldHandler.DapHoldJReleasePayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record DapHoldLoopPayload(boolean looping) implements CustomPayload {
        public static final Id<DapHoldHandler.DapHoldLoopPayload> ID = new Id(new Identifier("cooptest", "daphold_loop"));
        public static final PacketCodec<PacketByteBuf, DapHoldHandler.DapHoldLoopPayload> CODEC = PacketCodec.of(
            (p, buf) -> buf.writeBoolean(p.looping()), buf -> new DapHoldHandler.DapHoldLoopPayload(buf.readBoolean())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record DapHoldStartPayload(UUID playerId, UUID partnerId, int role) implements CustomPayload {
        public static final Id<DapHoldHandler.DapHoldStartPayload> ID = new Id(new Identifier("cooptest", "daphold_start"));
        public static final PacketCodec<PacketByteBuf, DapHoldHandler.DapHoldStartPayload> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeUuid(p.playerId());
            buf.writeUuid(p.partnerId());
            buf.writeInt(p.role());
        }, buf -> new DapHoldHandler.DapHoldStartPayload(buf.readUuid(), buf.readUuid(), buf.readInt()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record DapHoldWindowPayload(boolean open) implements CustomPayload {
        public static final Id<DapHoldHandler.DapHoldWindowPayload> ID = new Id(new Identifier("cooptest", "daphold_window"));
        public static final PacketCodec<PacketByteBuf, DapHoldHandler.DapHoldWindowPayload> CODEC = PacketCodec.of(
            (p, buf) -> buf.writeBoolean(p.open()), buf -> new DapHoldHandler.DapHoldWindowPayload(buf.readBoolean())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record GroupJoinPayload() implements CustomPayload {
        public static final Id<DapHoldHandler.GroupJoinPayload> ID = new Id(new Identifier("cooptest", "daphold_group_join_req"));
        public static final PacketCodec<PacketByteBuf, DapHoldHandler.GroupJoinPayload> CODEC = PacketCodec.unit(new DapHoldHandler.GroupJoinPayload());

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record GroupJoinedPayload(UUID joinerId, UUID hfId, int memberCount) implements CustomPayload {
        public static final Id<DapHoldHandler.GroupJoinedPayload> ID = new Id(new Identifier("cooptest", "daphold_group_join"));
        public static final PacketCodec<PacketByteBuf, DapHoldHandler.GroupJoinedPayload> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeUuid(p.joinerId());
            buf.writeUuid(p.hfId());
            buf.writeInt(p.memberCount());
        }, buf -> new DapHoldHandler.GroupJoinedPayload(buf.readUuid(), buf.readUuid(), buf.readInt()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record GroupResultPayload(boolean perfect, int memberCount) implements CustomPayload {
        public static final Id<DapHoldHandler.GroupResultPayload> ID = new Id(new Identifier("cooptest", "daphold_group_result"));
        public static final PacketCodec<PacketByteBuf, DapHoldHandler.GroupResultPayload> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeBoolean(p.perfect());
            buf.writeInt(p.memberCount());
        }, buf -> new DapHoldHandler.GroupResultPayload(buf.readBoolean(), buf.readInt()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
