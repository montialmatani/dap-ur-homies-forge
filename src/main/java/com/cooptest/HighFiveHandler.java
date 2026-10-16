package com.cooptest;

import com.cooptest.bros.BrosHandler;
import com.cooptest.highfive.HighFiveNormalHandler;
import com.cooptest.highfive.ReadyFiveHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.PlayerLookup;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.attribute.EntityAttributes;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class HighFiveHandler {
    public static final float HIGH_FIVE_RANGE = 1.6F;
    public static final long HAND_RAISED_DURATION = 2500L;
    public static final long COOLDOWN_MS = 1000L;
    public static final long HIGH_FIVE_ANIM_DURATION = 1500L;
    public static final long START_ANIM_DELAY_MS = 0L;
    public static final long HIT_EFFECT_DELAY_MS = 100L;
    public static final long END_ANIM_DURATION_MS = 1500L;
    public static final double SPEED_TIER_1 = 5.0;
    public static final double SPEED_TIER_2 = 7.5;
    public static final double SPEED_TIER_3 = 12.0;
    public static final int SPEED_HISTORY_TICKS = 60;
    public static final double SLOT_FORWARD_OFFSET = 0.4;
    public static final double SLOT_LEFT_OFFSET = -0.8;
    public static final double SLOT_RADIUS = 1.0;
    public static final double TP_PUSHBACK_OFFSET = 0.1;
    public static final double TP_PUSHBACK_LEFT_OFFSET = 0.2;
    public static final double FRONT_EXCLUDE_DEG = 30.0;
    public static final boolean PASS_SLOT_DEBUG_PARTICLES = false;
    public static final float PASS_DETECT_RANGE = 4.0F;
    public static final double REACH_FORWARD_OFFSET = 0.7;
    public static final double REACH_RADIUS = 0.6;
    public static final double FAST_SPEED_THRESHOLD = 4.5;
    public static final double STANDUP_NORMAL_SEPARATION = 1.1;
    public static final double STANDUP_NORMAL_RIGHT = 0.2;
    public static final long STANDUP_NORMAL_IMPACT_MS = 290L;
    public static final long STANDUP_NORMAL_PULLBACK_MS = 540L;
    public static final double STANDUP_NORMAL_PULLBACK = 0.4;
    public static final double STANDUP_FAST_SEPARATION = 0.9;
    public static final double STANDUP_FAST_RIGHT = 0.2;
    public static final long STANDUP_FAST_IMPACT_MS = 210L;
    public static final double STANDUP_FAST_PUSH_THROUGH = 0.04;
    public static final double STANDUP_MIXED_STILL_NUDGE_FWD = 0.12;
    public static final double STANDUP_MIXED_STILL_NUDGE_BACK = 0.08;
    public static final int STANDUP_YAW_LERP_TICKS = 5;
    public static final boolean STANDUP_REACH_DEBUG = false;
    public static final Map<UUID, Long> handRaisedTime = new HashMap<>();
    public static final Map<UUID, Long> highFiveCooldown = new HashMap<>();
    public static final Map<UUID, Long> highFiveAnimStart = new HashMap<>();
    public static final Map<UUID, Long> startAnimTime = new HashMap<>();
    public static final Map<UUID, Long> lastHoldRefresh = new HashMap<>();
    public static final Map<UUID, Boolean> handRaiseSprinting = new HashMap<>();
    public static final Map<UUID, Long> endAnimTime = new HashMap<>();
    private static final Map<UUID, Long> comboWindowStart = new HashMap<>();
    private static final Map<UUID, UUID> comboPartner = new HashMap<>();
    private static final Map<UUID, Long> comboRequested = new HashMap<>();
    private static final Map<UUID, Long> comboFreezeEnd = new HashMap<>();
    private static final Map<UUID, HighFiveHandler.ComboImpact> pendingComboImpacts = new HashMap<>();
    private static final long COMBO_WINDOW_MS = 1000L;
    private static final long COMBO_FREEZE_MS = 2250L;
    private static final long COMBO_SECOND_HIT_MS = 1290L;
    private static final Map<UUID, Vec3d> frozenPositions = new HashMap<>();
    private static final Map<BlockPos, HighFiveHandler.BeaconRemoval> pendingBeaconRemovals = new HashMap<>();
    private static final List<HighFiveHandler.ParticleBeam> activeParticleBeams = new ArrayList<>();
    private static final Map<UUID, HighFiveHandler.PendingHighFive> pendingEffects = new HashMap<>();
    public static final Map<UUID, LinkedList<Double>> speedHistory = new HashMap<>();
    public static final Identifier HIGH_FIVE_REQUEST_ID = new Identifier("cooptest", "high_five_request");
    public static final Identifier HIGH_FIVE_HOLD_ID = new Identifier("cooptest", "high_five_hold");
    public static final Identifier HAND_RAISED_SYNC_ID = new Identifier("cooptest", "hand_raised_sync");
    public static final Identifier HIGH_FIVE_SUCCESS_ID = new Identifier("cooptest", "high_five_success");
    public static final Identifier HIGH_FIVE_ANIM_ID = new Identifier("cooptest", "high_five_anim");
    public static final Identifier COMBO_REQUEST_ID = new Identifier("cooptest", "highfive_combo_request");
    public static final Identifier COMBO_WINDOW_ID = new Identifier("cooptest", "highfive_combo_window");
    public static final Identifier COMBO_WINDOW_CLOSE_ID = new Identifier("cooptest", "highfive_combo_window_close");
    public static final Identifier FREEZE_STATE_ID = new Identifier("testcoop", "freeze_state");
    public static final int ANIM_START = 1;
    public static final int ANIM_END = 2;
    public static final int ANIM_HIT = 3;
    public static final int ANIM_SIKE = 4;
    private static final Set<UUID> sikeMode = new HashSet<>();
    private static final Map<UUID, Long> sikeStunEnd = new HashMap<>();
    private static final Map<UUID, Long> sikeSlowEnd = new HashMap<>();
    private static final long SIKE_ANIM_MS = 1458L;
    private static final long SIKE_SLOW_MS = 2000L;
    private static final Identifier SIKE_SLOW_ID = new Identifier("testcoop", "sike_slow");
    public static final float HF_SHAKE_BASE = 0.35F;
    public static final float HF_SHAKE_PER_TIER = 0.25F;
    public static final int HF_SHAKE_MS = 200;
    public static final float HF_RARE_CHANCE = 0.12F;
    public static final int HF_FAST_BUFF_TICKS = 120;
    public static final int HF_FAST_SPEED_AMP = 2;
    public static final int HF_FAST_JUMP_AMP = 1;

    private static void spawnParticleBeam(ServerWorld world, Vec3d playerPos, boolean isYellow) {
        long now = System.currentTimeMillis();
        long endTime = now + 1750L;
        activeParticleBeams.add(new HighFiveHandler.ParticleBeam(world, playerPos.add(0.0, 0.5, 0.0), isYellow, endTime));
    }

    private static void tickParticleBeams(long now) {
        Iterator<HighFiveHandler.ParticleBeam> it = activeParticleBeams.iterator();

        while (it.hasNext()) {
            HighFiveHandler.ParticleBeam beam = it.next();
            if (now >= beam.endTime) {
                it.remove();
            } else {
                for (int i = 0; i < 30; i++) {
                    double y = beam.startPos.y + i * 0.5;
                    if (beam.isYellow) {
                        beam.world.spawnParticles(ParticleTypes.END_ROD, beam.startPos.x, y, beam.startPos.z, 3, 0.15, 0.0, 0.15, 0.0);
                        beam.world.spawnParticles(ParticleTypes.FLAME, beam.startPos.x, y, beam.startPos.z, 2, 0.1, 0.0, 0.1, 0.0);
                        double spiralAngle = i * 0.3;
                        double spiralRadius = 0.5;
                        double spiralX = beam.startPos.x + Math.cos(spiralAngle) * spiralRadius;
                        double spiralZ = beam.startPos.z + Math.sin(spiralAngle) * spiralRadius;
                        beam.world.spawnParticles(ParticleTypes.END_ROD, spiralX, y, spiralZ, 1, 0.0, 0.0, 0.0, 0.0);
                    } else {
                        beam.world.spawnParticles(ParticleTypes.SQUID_INK, beam.startPos.x, y, beam.startPos.z, 3, 0.15, 0.0, 0.15, 0.0);
                        beam.world.spawnParticles(ParticleTypes.LARGE_SMOKE, beam.startPos.x, y, beam.startPos.z, 2, 0.1, 0.0, 0.1, 0.0);
                        double spiralAngle = i * 0.3;
                        double spiralRadius = 0.5;
                        double spiralX = beam.startPos.x + Math.cos(spiralAngle) * spiralRadius;
                        double spiralZ = beam.startPos.z + Math.sin(spiralAngle) * spiralRadius;
                        beam.world.spawnParticles(ParticleTypes.SQUID_INK, spiralX, y, spiralZ, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }
        }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(HighFiveHandler.HighFiveRequestPayload.ID, HighFiveHandler.HighFiveRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HighFiveHandler.HighFiveHoldPayload.ID, HighFiveHandler.HighFiveHoldPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HighFiveHandler.HighFiveSuccessPayload.ID, HighFiveHandler.HighFiveSuccessPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HighFiveHandler.HandRaisedSyncPayload.ID, HighFiveHandler.HandRaisedSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HighFiveHandler.HighFiveAnimPayload.ID, HighFiveHandler.HighFiveAnimPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HighFiveHandler.ComboRequestPayload.ID, HighFiveHandler.ComboRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HighFiveHandler.ComboWindowPayload.ID, HighFiveHandler.ComboWindowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HighFiveHandler.ComboWindowClosePayload.ID, HighFiveHandler.ComboWindowClosePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HighFiveHandler.FreezeStatePayload.ID, HighFiveHandler.FreezeStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HighFiveHandler.SikeRequestPayload.ID, HighFiveHandler.SikeRequestPayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(HighFiveHandler.HighFiveRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (CoopMovesConfig.get().enableHighFive) {
                    onHighFiveRequest(player);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(HighFiveHandler.HighFiveHoldPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                UUID id = player.getUuid();
                if (handRaisedTime.containsKey(id)) {
                    lastHoldRefresh.put(id, System.currentTimeMillis());
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(HighFiveHandler.ComboRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (CoopMovesConfig.get().enableHighFiveCombo) {
                    onComboRequest(player);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(HighFiveHandler.SikeRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (CoopMovesConfig.get().enableHighFive) {
                    UUID id = player.getUuid();
                    sikeMode.add(id);
                    onHighFiveRequest(player);
                }
            });
        });
        ServerTickEvents.END_SERVER_TICK
            .register(
                (EndTick)server -> {
                    long now = System.currentTimeMillis();
                    tickParticleBeams(now);

                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        UUID id = player.getUuid();
                        Vec3d velocity = player.getVelocity();
                        double speed = Math.hypot(velocity.x, velocity.z) * 20.0;
                        LinkedList<Double> history = speedHistory.computeIfAbsent(id, k -> new LinkedList<>());
                        history.addLast(speed);

                        while (history.size() > 60) {
                            history.removeFirst();
                        }
                    }

                    Iterator<Entry<UUID, HighFiveHandler.PendingHighFive>> pendingIt = pendingEffects.entrySet().iterator();

                    while (pendingIt.hasNext()) {
                        Entry<UUID, HighFiveHandler.PendingHighFive> entry = pendingIt.next();
                        HighFiveHandler.PendingHighFive pending = entry.getValue();
                        if (now >= pending.effectTime) {
                            executeHighFiveEffects(pending.p1, pending.p2, pending.pos, pending.tier);
                            pendingIt.remove();
                        }
                    }

                    Set<String> processedComboPairs = new HashSet<>();
                    Iterator<Entry<UUID, Long>> comboWindowIt = comboWindowStart.entrySet().iterator();

                    while (comboWindowIt.hasNext()) {
                        Entry<UUID, Long> entry = comboWindowIt.next();
                        UUID playerId = entry.getKey();
                        long windowStart = entry.getValue();
                        if (now - windowStart > 1000L) {
                            boolean playerPressed = comboRequested.containsKey(playerId);
                            UUID partnerId = comboPartner.get(playerId);
                            if (playerPressed && partnerId != null) {
                                String pairKey = playerId.compareTo(partnerId) < 0 ? playerId + ":" + partnerId : partnerId + ":" + playerId;
                                if (!processedComboPairs.contains(pairKey)) {
                                    processedComboPairs.add(pairKey);
                                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                                    ServerPlayerEntity partner = server.getPlayerManager().getPlayer(partnerId);
                                    if (player != null && partner != null) {
                                        boolean partnerPressed = comboRequested.containsKey(partnerId);
                                        if (!partnerPressed) {
                                            player.sendMessage(Text.literal("§c✗ " + partner.getName().getString() + " missed the combo!"), true);
                                            partner.sendMessage(
                                                Text.literal("§c✗ You missed the combo! " + player.getName().getString() + " pressed H!"), true
                                            );
                                        }
                                    }
                                }
                            }

                            comboWindowIt.remove();
                            comboPartner.remove(playerId);
                            comboRequested.remove(playerId);
                        }
                    }

                    Iterator<Entry<UUID, HighFiveHandler.ComboImpact>> comboImpactIt = pendingComboImpacts.entrySet().iterator();

                    while (comboImpactIt.hasNext()) {
                        Entry<UUID, HighFiveHandler.ComboImpact> entry = comboImpactIt.next();
                        HighFiveHandler.ComboImpact impact = entry.getValue();
                        if (now >= impact.impactTime) {
                            executeSecondImpact(impact.p1, impact.p2);
                            comboImpactIt.remove();
                        }
                    }

                    Iterator<Entry<UUID, Long>> freezeIt = comboFreezeEnd.entrySet().iterator();

                    while (freezeIt.hasNext()) {
                        Entry<UUID, Long> entry = freezeIt.next();
                        if (now >= entry.getValue()) {
                            UUID playerId = entry.getKey();
                            freezeIt.remove();
                            frozenPositions.remove(playerId);
                            handRaisedTime.remove(playerId);
                            startAnimTime.remove(playerId);
                            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                            if (player != null) {
                                syncHandRaised(player, false);
                                PoseNetworking.broadcastAnimState(player, 0);

                                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                                    ServerPlayNetworking.send(p, new HighFiveHandler.FreezeStatePayload(playerId, false));
                                }

                                ServerPlayNetworking.send(player, new HighFiveHandler.ComboWindowClosePayload(playerId));
                            }
                        }
                    }

                    for (Entry<UUID, Vec3d> entry : frozenPositions.entrySet()) {
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                        if (player != null) {
                            Vec3d frozenPos = entry.getValue();
                            Vec3d currentPos = player.getPos();
                            if (currentPos.squaredDistanceTo(frozenPos) > 0.01) {
                                player.requestTeleport(frozenPos.x, frozenPos.y, frozenPos.z);
                                player.setVelocity(Vec3d.ZERO);
                                player.velocityModified = true;
                            }
                        }
                    }

                    Iterator<Entry<UUID, Long>> animIt = highFiveAnimStart.entrySet().iterator();

                    while (animIt.hasNext()) {
                        Entry<UUID, Long> entryx = animIt.next();
                        if (now - entryx.getValue() > 1500L) {
                            UUID playerId = entryx.getKey();
                            boolean inCombo = comboFreezeEnd.containsKey(playerId);
                            boolean inDapCombo = DapComboChain.isInCombo(playerId);
                            if (!inCombo && !inDapCombo) {
                                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                                if (player != null) {
                                    PoseNetworking.broadcastAnimState(player, 0);
                                }
                            }

                            animIt.remove();
                        }
                    }

                    Iterator<Entry<BlockPos, HighFiveHandler.BeaconRemoval>> beaconIt = pendingBeaconRemovals.entrySet().iterator();

                    while (beaconIt.hasNext()) {
                        Entry<BlockPos, HighFiveHandler.BeaconRemoval> entryx = beaconIt.next();
                        HighFiveHandler.BeaconRemoval removal = entryx.getValue();
                        if (now >= removal.removeTime) {
                            removal.world.setBlockState(removal.beaconPos, Blocks.AIR.getDefaultState());
                            removal.world.setBlockState(removal.glassPos, Blocks.AIR.getDefaultState());
                            beaconIt.remove();
                        }
                    }

                    Iterator<Entry<UUID, Long>> sikeStunIt = sikeStunEnd.entrySet().iterator();

                    while (sikeStunIt.hasNext()) {
                        Entry<UUID, Long> entryx = sikeStunIt.next();
                        if (now >= entryx.getValue()) {
                            UUID victimId = entryx.getKey();
                            sikeStunIt.remove();
                            ServerPlayerEntity victim = server.getPlayerManager().getPlayer(victimId);
                            if (victim != null) {
                                for (ServerPlayerEntity p : PlayerLookup.all(server)) {
                                    ServerPlayNetworking.send(p, new HighFiveHandler.FreezeStatePayload(victimId, false));
                                }

                                frozenPositions.remove(victimId);
                                PoseNetworking.broadcastAnimState(victim, 0);
                                syncHandRaised(victim, false);
                                applySikeSlow(victim);
                                sikeSlowEnd.put(victimId, now + 2000L);
                            }
                        }
                    }

                    Iterator<Entry<UUID, Long>> sikeSlowIt = sikeSlowEnd.entrySet().iterator();

                    while (sikeSlowIt.hasNext()) {
                        Entry<UUID, Long> entryx = sikeSlowIt.next();
                        if (now >= entryx.getValue()) {
                            UUID victimId = entryx.getKey();
                            sikeSlowIt.remove();
                            ServerPlayerEntity victim = server.getPlayerManager().getPlayer(victimId);
                            if (victim != null) {
                                removeSikeSlow(victim);
                            }
                        }
                    }

                    Iterator<Entry<UUID, Long>> endIt = endAnimTime.entrySet().iterator();

                    while (endIt.hasNext()) {
                        Entry<UUID, Long> entryx = endIt.next();
                        if (now >= entryx.getValue()) {
                            UUID playerId = entryx.getKey();
                            endIt.remove();
                            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                            if (player != null) {
                                PoseNetworking.broadcastAnimState(player, 0);
                            }
                        }
                    }

                    Iterator<Entry<UUID, Long>> it = handRaisedTime.entrySet().iterator();

                    while (it.hasNext()) {
                        Entry<UUID, Long> entryx = it.next();
                        UUID playerId = entryx.getKey();
                        long startAnimStartTime = startAnimTime.getOrDefault(playerId, entryx.getValue());
                        long baseline = Math.max(startAnimStartTime, lastHoldRefresh.getOrDefault(playerId, 0L));
                        if (now - baseline > 2500L) {
                            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                            it.remove();
                            startAnimTime.remove(playerId);
                            lastHoldRefresh.remove(playerId);
                            handRaiseSprinting.remove(playerId);
                            if (player != null) {
                                executeEndAnimation(player);
                                syncHandRaised(player, false);
                            }
                        }
                    }

                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        UUID playerId = player.getUuid();
                        if (handRaisedTime.containsKey(playerId)) {
                            if (BrosHandler.isEngaged(playerId)) {
                                consumeHandRaise(player);
                            } else if (!isOnCooldown(playerId) && !isInBlockingState(playerId)) {
                                Long startTime = startAnimTime.get(playerId);
                                if (startTime == null || now - startTime >= 0L) {
                                    ServerPlayerEntity passPartner = findPassByPartner(player);
                                    if (passPartner != null) {
                                        Long passPartnerStartTime = startAnimTime.get(passPartner.getUuid());
                                        if (passPartnerStartTime == null || now - passPartnerStartTime >= 0L) {
                                            consumeHandRaise(player);
                                            consumeHandRaise(passPartner);
                                            Vec3d targetP1 = slotPoint(passPartner)
                                                .subtract(horizontalForward(player).multiply(0.1))
                                                .add(horizontalLeft(player).multiply(0.2));
                                            Vec3d targetP2 = slotPoint(player)
                                                .subtract(horizontalForward(passPartner).multiply(0.1))
                                                .add(horizontalLeft(passPartner).multiply(0.2));
                                            HighFivePassHandler.executePassHighFive(player, passPartner, targetP1, targetP2);
                                        }
                                    } else {
                                        ServerPlayerEntity partner = findHighFivePartner(player);
                                        if (partner != null) {
                                            Long partnerStartTime = startAnimTime.get(partner.getUuid());
                                            if ((partnerStartTime == null || now - partnerStartTime >= 0L)
                                                && isRoughlyAhead(player, partner)
                                                && isRoughlyAhead(partner, player)) {
                                                executeHighFive(player, partner);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            );
    }

    public static boolean isInBlockingState(UUID playerId) {
        return isInBlockingAnimation(playerId) || FallDapHandler.isSquashed(playerId) || sikeStunEnd.containsKey(playerId) || sikeSlowEnd.containsKey(playerId);
    }

    public static boolean isInHighFiveMode(UUID playerId) {
        return handRaisedTime.containsKey(playerId) || startAnimTime.containsKey(playerId);
    }

    public static boolean isInAnyHighFiveState(UUID playerId) {
        return isInHighFiveMode(playerId) || isInBlockingState(playerId) || highFiveAnimStart.containsKey(playerId);
    }

    public static boolean canPerformAction(UUID playerId) {
        return !isInBlockingState(playerId);
    }

    private static void executeEndAnimation(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        long now = System.currentTimeMillis();
        endAnimTime.put(playerId, now + 1500L);
        broadcastHighFiveAnim(player, 2);
        ServerWorld world = player.getServerWorld();
        Vec3d pos = player.getPos().add(0.0, 1.6, 0.0);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8F, 0.5F);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 0.4F, 1.2F);
        world.spawnParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 6, 0.15, 0.15, 0.15, 0.01);
        player.sendMessage(Text.literal("§7*left hanging*"), true);
    }

    private static void broadcastHighFiveAnim(ServerPlayerEntity player, int animState) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            HighFiveHandler.HighFiveAnimPayload payload = new HighFiveHandler.HighFiveAnimPayload(player.getUuid(), animState);

            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    private static double getMaxRecentSpeed(UUID playerId) {
        LinkedList<Double> history = speedHistory.get(playerId);
        if (history != null && !history.isEmpty()) {
            double maxSpeed = 0.0;

            for (Double speed : history) {
                if (speed > maxSpeed) {
                    maxSpeed = speed;
                }
            }

            return maxSpeed;
        } else {
            return 0.0;
        }
    }

    private static void onHighFiveRequest(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (!ReadyFiveHandler.onHandRaise(player)) {
            if (BrosHandler.isEngaged(uuid)) {
                consumeHandRaise(player);
            } else if (!handRaisedTime.containsKey(uuid) && ChargedDapHandler.isCharging(uuid)) {
                syncHandRaised(player, false);
            } else if (ChargedDapHandler.isInComboCooldown(uuid)) {
                player.sendMessage(Text.literal("§cWait 1 second after combo!"), true);
                syncHandRaised(player, false);
            } else if (!isInBlockingState(uuid)) {
                if (!FallCatchHandler.isInCatchReadyMode(uuid)) {
                    if (!isOnCooldown(uuid)) {
                        if (player.getMainHandStack().isEmpty()) {
                            if (handRaisedTime.containsKey(uuid)) {
                                handRaisedTime.remove(uuid);
                                startAnimTime.remove(uuid);
                                lastHoldRefresh.remove(uuid);
                                handRaiseSprinting.remove(uuid);
                                syncHandRaised(player, false);
                                executeEndAnimation(player);
                            } else {
                                long now = System.currentTimeMillis();
                                handRaisedTime.put(uuid, now);
                                startAnimTime.put(uuid, now);
                                handRaiseSprinting.put(uuid, player.isSprinting());
                                syncHandRaised(player, true);
                                broadcastHighFiveAnim(player, 1);
                                player.getServerWorld()
                                    .playSound(
                                        null,
                                        player.getX(),
                                        player.getY(),
                                        player.getZ(),
                                        SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                                        SoundCategory.PLAYERS,
                                        0.3F,
                                        1.5F
                                    );
                            }
                        }
                    }
                }
            }
        }
    }

    public static void syncHandRaised(ServerPlayerEntity player, boolean raised) {
        if (player != null) {
            HighFiveHandler.HandRaisedSyncPayload payload = new HighFiveHandler.HandRaisedSyncPayload(player.getUuid(), raised);

            for (ServerPlayerEntity other : PlayerLookup.all(player.getServer())) {
                ServerPlayNetworking.send(other, payload);
            }
        }
    }

    private static void spawnSlotDebugMarker(ServerPlayerEntity player) {
        Vec3d slot = slotPoint(player);
        double y = player.getY() + 1.0;
        ServerWorld world = player.getServerWorld();
        world.spawnParticles(ParticleTypes.END_ROD, slot.x, y, slot.z, 3, 0.0, 0.05, 0.0, 0.01);
        int ringPoints = 16;

        for (int i = 0; i < ringPoints; i++) {
            double angle = (Math.PI * 2) * i / ringPoints;
            double rx = slot.x + Math.cos(angle) * 1.0;
            double rz = slot.z + Math.sin(angle) * 1.0;
            world.spawnParticles(ParticleTypes.WITCH, rx, y, rz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    public static Vec3d horizontalForward(ServerPlayerEntity player) {
        double yawRad = Math.toRadians(player.getYaw());
        return new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
    }

    public static Vec3d horizontalLeft(ServerPlayerEntity player) {
        double yawRad = Math.toRadians(player.getYaw());
        return new Vec3d(Math.cos(yawRad), 0.0, Math.sin(yawRad));
    }

    public static Vec3d slotPoint(ServerPlayerEntity player) {
        Vec3d pos = player.getPos();
        Vec3d forward = horizontalForward(player);
        Vec3d left = horizontalLeft(player);
        return new Vec3d(
            pos.x + forward.x * 0.4 + left.x * -0.8,
            pos.y,
            pos.z + forward.z * 0.4 + left.z * -0.8
        );
    }

    private static double horizontalDistance(Vec3d a, Vec3d b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static boolean inEachOthersSlot(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        double d1 = horizontalDistance(slotPoint(p1), p2.getPos());
        double d2 = horizontalDistance(slotPoint(p2), p1.getPos());
        return d1 <= 1.0 || d2 <= 1.0;
    }

    private static boolean isRoughlyAhead(ServerPlayerEntity self, ServerPlayerEntity other) {
        Vec3d toOther = other.getPos().subtract(self.getPos());
        Vec3d dir = new Vec3d(toOther.x, 0.0, toOther.z);
        if (dir.lengthSquared() < 1.0E-6) {
            return false;
        } else {
            dir = dir.normalize();
            double dot = MathHelper.clamp(horizontalForward(self).dotProduct(dir), -1.0, 1.0);
            double angleDeg = Math.toDegrees(Math.acos(dot));
            return angleDeg <= 30.0;
        }
    }

    private static boolean isValidPassByGeometry(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        return !isRoughlyAhead(p1, p2) && !isRoughlyAhead(p2, p1) ? inEachOthersSlot(p1, p2) : false;
    }

    private static void consumeHandRaise(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        handRaisedTime.remove(id);
        startAnimTime.remove(id);
        lastHoldRefresh.remove(id);
        handRaiseSprinting.remove(id);
        syncHandRaised(player, false);
    }

    private static ServerPlayerEntity findPassByPartner(ServerPlayerEntity player) {
        Box searchBox = player.getBoundingBox().expand(4.0);
        long now = System.currentTimeMillis();

        for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
            if (other != player
                && handRaisedTime.containsKey(other.getUuid())
                && !isOnCooldown(other.getUuid())
                && !isInBlockingState(other.getUuid())) {
                Long otherStartTime = startAnimTime.get(other.getUuid());
                if ((otherStartTime == null || now - otherStartTime >= 0L) && searchBox.intersects(other.getBoundingBox())) {
                    if (isValidPassByGeometry(player, other)) {
                        return other;
                    }

                    logPassByDebug(player, other);
                }
            }
        }

        return null;
    }

    private static void logPassByDebug(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        double d1 = horizontalDistance(slotPoint(p1), p2.getPos());
        double d2 = horizontalDistance(slotPoint(p2), p1.getPos());
        float facingDiff = MathHelper.wrapDegrees(p1.getYaw() - p2.getYaw());
    }

    private static void scheduleYawLerp(ServerPlayerEntity player, ServerPlayerEntity target) {
        Vec3d toTarget = target.getPos().subtract(player.getPos());
        float targetYaw = (float)Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        float startYaw = player.getYaw();
        int ticks = 5;
        new Thread(() -> {
            for (int i = 1; i <= ticks; i++) {
                float t = (float)i / ticks;
                float newYaw = startYaw + MathHelper.wrapDegrees(targetYaw - startYaw) * t;

                try {
                    Thread.sleep(50L);
                } catch (InterruptedException var8) {
                }

                player.getServer().execute(() -> {
                    player.setYaw(newYaw);
                    player.setHeadYaw(newYaw);
                    player.setBodyYaw(newYaw);
                });
            }
        }).start();
    }

    public static Vec3d reachPoint(ServerPlayerEntity player) {
        CoopMovesConfig c = CoopMovesConfig.get();
        Vec3d fwd = horizontalForward(player);
        Vec3d right = new Vec3d(-fwd.z, 0.0, fwd.x);
        Vec3d pos = player.getPos();
        return new Vec3d(
            pos.x + fwd.x * c.highFiveReachForward + right.x * c.highFiveReachRightOffset,
            pos.y + 1.3,
            pos.z + fwd.z * c.highFiveReachForward + right.z * c.highFiveReachRightOffset
        );
    }

    public static boolean reachPointsOverlap(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        CoopMovesConfig c = CoopMovesConfig.get();
        Vec3d rp1 = reachPoint(p1);
        Vec3d rp2 = reachPoint(p2);
        double dx = rp2.x - rp1.x;
        double dz = rp2.z - rp1.z;
        Vec3d fwd = horizontalForward(p1);
        double depth = dx * fwd.x + dz * fwd.z;
        double width = dx * -fwd.z + dz * fwd.x;
        double d = Math.max(0.05, c.highFiveReachDepth);
        double w = Math.max(0.05, c.highFiveReachWidth);
        return depth * depth / (d * d) + width * width / (w * w) <= 1.0;
    }

    private static ServerPlayerEntity findHighFivePartner(ServerPlayerEntity player) {
        Box searchBox = player.getBoundingBox().expand(4.0);
        long now = System.currentTimeMillis();

        for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
            if (other != player
                && handRaisedTime.containsKey(other.getUuid())
                && !isOnCooldown(other.getUuid())
                && !isInBlockingState(other.getUuid())) {
                Long otherStartTime = startAnimTime.get(other.getUuid());
                if ((otherStartTime == null || now - otherStartTime >= 0L) && searchBox.intersects(other.getBoundingBox()) && reachPointsOverlap(player, other)) {
                    return other;
                }
            }
        }

        return null;
    }

    private static void executeHighFive(ServerPlayerEntity player1, ServerPlayerEntity player2) {
        boolean p1Siking = sikeMode.remove(player1.getUuid());
        boolean p2Siking = sikeMode.remove(player2.getUuid());
        if (p1Siking && p2Siking) {
            executeMutualSike(player1, player2);
        } else if (p1Siking) {
            executeSike(player1, player2);
        } else if (p2Siking) {
            executeSike(player2, player1);
        } else {
            long now = System.currentTimeMillis();
            highFiveCooldown.put(player1.getUuid(), now);
            highFiveCooldown.put(player2.getUuid(), now);
            handRaisedTime.remove(player1.getUuid());
            handRaisedTime.remove(player2.getUuid());
            startAnimTime.remove(player1.getUuid());
            startAnimTime.remove(player2.getUuid());
            lastHoldRefresh.remove(player1.getUuid());
            lastHoldRefresh.remove(player2.getUuid());
            syncHandRaised(player1, false);
            syncHandRaised(player2, false);
            highFiveAnimStart.put(player1.getUuid(), now);
            highFiveAnimStart.put(player2.getUuid(), now);
            boolean p1Fast = handRaiseSprinting.getOrDefault(player1.getUuid(), false);
            boolean p2Fast = handRaiseSprinting.getOrDefault(player2.getUuid(), false);
            double speed1 = getMaxRecentSpeed(player1.getUuid());
            double speed2 = getMaxRecentSpeed(player2.getUuid());
            double maxSpeed = Math.max(speed1, speed2);
            int tier;
            if (maxSpeed >= 12.0) {
                tier = 3;
            } else if (p1Fast && p2Fast) {
                tier = 2;
            } else if (!p1Fast && !p2Fast) {
                tier = 0;
            } else {
                tier = 1;
            }

            speedHistory.remove(player1.getUuid());
            speedHistory.remove(player2.getUuid());
            scheduleYawLerp(player1, player2);
            scheduleYawLerp(player2, player1);
            long effectDelay;
            if (!p1Fast && !p2Fast) {
                broadcastHighFiveAnim(player1, 3);
                broadcastHighFiveAnim(player2, 3);
                PoseNetworking.broadcastAnimState(player1, 20);
                PoseNetworking.broadcastAnimState(player2, 20);
                HighFiveNormalHandler.executeNormal(player1, player2, now);
                effectDelay = 290L;
            } else if (p1Fast && p2Fast) {
                broadcastHighFiveAnim(player1, 3);
                broadcastHighFiveAnim(player2, 3);
                PoseNetworking.broadcastAnimState(player1, 112);
                PoseNetworking.broadcastAnimState(player2, 112);
                HighFiveNormalHandler.executeFast(player1, player2, now);
                effectDelay = 210L;
            } else {
                ServerPlayerEntity fastP = p1Fast ? player1 : player2;
                ServerPlayerEntity stillP = p1Fast ? player2 : player1;
                broadcastHighFiveAnim(fastP, 3);
                broadcastHighFiveAnim(stillP, 3);
                PoseNetworking.broadcastAnimState(fastP, 112);
                PoseNetworking.broadcastAnimState(stillP, 20);
                HighFiveNormalHandler.executeMixed(fastP, stillP, now);
                effectDelay = 210L;
            }

            Vec3d pos1 = player1.getPos();
            Vec3d pos2 = player2.getPos();
            Vec3d highFivePos = pos1.add(pos2).multiply(0.5).add(0.0, 1.4, 0.0);
            pendingEffects.put(player1.getUuid(), new HighFiveHandler.PendingHighFive(player1, player2, highFivePos, tier, now + effectDelay));
            syncHandRaised(player1, false);
            syncHandRaised(player2, false);
            HighFiveHandler.HighFiveSuccessPayload successPayload = new HighFiveHandler.HighFiveSuccessPayload(
                highFivePos.x, highFivePos.y, highFivePos.z, player1.getUuid(), player2.getUuid(), tier
            );

            for (ServerPlayerEntity other : PlayerLookup.all(player1.getServer())) {
                ServerPlayNetworking.send(other, successPayload);
            }

            comboWindowStart.put(player1.getUuid(), now);
            comboWindowStart.put(player2.getUuid(), now);
            comboPartner.put(player1.getUuid(), player2.getUuid());
            comboPartner.put(player2.getUuid(), player1.getUuid());
            new Thread(() -> {
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException var3x) {
                }

                player1.getServer().execute(() -> {
                    ServerPlayNetworking.send(player1, new HighFiveHandler.ComboWindowPayload(player1.getUuid()));
                    ServerPlayNetworking.send(player2, new HighFiveHandler.ComboWindowPayload(player2.getUuid()));
                });
            }).start();
        }
    }

    private static void executeSike(ServerPlayerEntity siker, ServerPlayerEntity victim) {
        long now = System.currentTimeMillis();
        UUID sikerId = siker.getUuid();
        UUID victimId = victim.getUuid();
        boolean mutualSike = sikeMode.contains(victimId);
        sikeMode.remove(victimId);
        handRaisedTime.remove(sikerId);
        handRaisedTime.remove(victimId);
        startAnimTime.remove(sikerId);
        startAnimTime.remove(victimId);
        syncHandRaised(siker, false);
        syncHandRaised(victim, false);
        if (mutualSike) {
            siker.damage(siker.getServerWorld().getDamageSources().generic(), 6.0F);
            victim.damage(victim.getServerWorld().getDamageSources().generic(), 6.0F);
            Vec3d toVictim = victim.getPos().subtract(siker.getPos()).normalize();
            if (toVictim.lengthSquared() < 0.001) {
                toVictim = new Vec3d(1.0, 0.0, 0.0);
            }

            siker.addVelocity(toVictim.negate().x * 0.6, 0.5, toVictim.negate().z * 0.6);
            siker.velocityModified = true;
            victim.addVelocity(toVictim.x * 0.6, 0.5, toVictim.z * 0.6);
            victim.velocityModified = true;
            broadcastHighFiveAnim(siker, 4);
            broadcastHighFiveAnim(victim, 4);
            PoseNetworking.broadcastAnimState(siker, 63);
            PoseNetworking.broadcastAnimState(victim, 63);
            sikeStunEnd.put(sikerId, now + 1458L);
            sikeStunEnd.put(victimId, now + 1458L);

            for (ServerPlayerEntity p : PlayerLookup.all(siker.getServer())) {
                ServerPlayNetworking.send(p, new HighFiveHandler.FreezeStatePayload(sikerId, true));
                ServerPlayNetworking.send(p, new HighFiveHandler.FreezeStatePayload(victimId, true));
            }

            frozenPositions.put(sikerId, siker.getPos());
            frozenPositions.put(victimId, victim.getPos());
            ServerWorld world = siker.getServerWorld();
            Vec3d mid = siker.getPos().add(victim.getPos()).multiply(0.5).add(0.0, 1.0, 0.0);
            world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.2F, 0.7F);
            world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_DONKEY_ANGRY, SoundCategory.PLAYERS, 0.9F, 0.8F);
            world.spawnParticles(ParticleTypes.EXPLOSION, mid.x, mid.y, mid.z, 2, 0.3, 0.3, 0.3, 0.0);
            world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, mid.x, mid.y + 1.0, mid.z, 8, 0.4, 0.3, 0.4, 0.05);
            siker.sendMessage(Text.literal("§4§l\ud83d\udca5 MUTUAL SIKE! You both suffer!"), true);
            victim.sendMessage(Text.literal("§4§l\ud83d\udca5 MUTUAL SIKE! You both suffer!"), true);
            highFiveCooldown.put(sikerId, now);
            highFiveCooldown.put(victimId, now);
        } else {
            PoseNetworking.broadcastAnimState(siker, 0);
            broadcastHighFiveAnim(victim, 4);
            PoseNetworking.broadcastAnimState(victim, 63);
            sikeStunEnd.put(victimId, now + 1458L);
            frozenPositions.put(victimId, victim.getPos());
            victim.setVelocity(Vec3d.ZERO);
            victim.velocityModified = true;

            for (ServerPlayerEntity p : PlayerLookup.all(siker.getServer())) {
                ServerPlayNetworking.send(p, new HighFiveHandler.FreezeStatePayload(victimId, true));
            }

            siker.getServerWorld()
                .playSound(
                    null, siker.getX(), siker.getY(), siker.getZ(), SoundEvents.ENTITY_WITCH_CELEBRATE, SoundCategory.PLAYERS, 1.0F, 1.0F
                );
            ServerWorld world = victim.getServerWorld();
            Vec3d vp = victim.getPos().add(0.0, 1.8, 0.0);
            world.playSound(null, vp.x, vp.y, vp.z, SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.0F, 0.8F);
            world.playSound(null, vp.x, vp.y, vp.z, SoundEvents.UI_TOAST_OUT, SoundCategory.PLAYERS, 0.7F, 0.6F);
            world.spawnParticles(ParticleTypes.FALLING_WATER, vp.x - 0.15, vp.y, vp.z, 6, 0.1, 0.05, 0.1, 0.01);
            world.spawnParticles(ParticleTypes.FALLING_WATER, vp.x + 0.15, vp.y, vp.z, 6, 0.1, 0.05, 0.1, 0.01);
            world.spawnParticles(ParticleTypes.SPLASH, vp.x, vp.y - 0.5, vp.z, 10, 0.2, 0.05, 0.2, 0.02);
            world.spawnParticles(ParticleTypes.POOF, vp.x, vp.y + 0.3, vp.z, 8, 0.2, 0.1, 0.2, 0.03);
            world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, vp.x, vp.y + 0.6, vp.z, 4, 0.3, 0.2, 0.3, 0.05);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, vp.x, vp.y, vp.z, 5, 0.15, 0.2, 0.15, 0.01);
            siker.sendMessage(Text.literal("§6§l\ud83d\ude02 SIKE!"), true);
            victim.sendMessage(Text.literal("§c§lSIKE!"), true);
            SikeFollowUpHandler.onSikeExecuted(siker, victim);
            highFiveCooldown.put(sikerId, now);
        }
    }

    private static void executeHighFiveEffects(ServerPlayerEntity player1, ServerPlayerEntity player2, Vec3d highFivePos, int tier) {
        ServerWorld world = player1.getServerWorld();
        switch (tier) {
            case 0:
                executeTier0(world, highFivePos, player1, player2);
                break;
            case 1:
                executeTier1(world, highFivePos, player1, player2);
                break;
            case 2:
                executeTier2(world, highFivePos, player1, player2);
                break;
            case 3:
                executeTier3(world, highFivePos, player1, player2);
        }

        float m = 1.0F + tier * 0.45F;
        world.playSound(
            null,
            highFivePos.x,
            highFivePos.y,
            highFivePos.z,
            ModSounds.DAP_HIT,
            SoundCategory.PLAYERS,
            0.55F + tier * 0.15F,
            1.45F - tier * 0.09F
        );
        world.playSound(
            null,
            highFivePos.x,
            highFivePos.y,
            highFivePos.z,
            SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
            SoundCategory.PLAYERS,
            0.45F * m,
            1.5F - tier * 0.08F
        );
        if (tier >= 1) {
            world.playSound(
                null,
                highFivePos.x,
                highFivePos.y,
                highFivePos.z,
                ModSounds.CLAP_SOUNDS[tier % ModSounds.CLAP_SOUNDS.length],
                SoundCategory.PLAYERS,
                0.5F + tier * 0.12F,
                1.2F
            );
        }

        world.spawnParticles(
            ParticleTypes.CRIT,
            highFivePos.x,
            highFivePos.y,
            highFivePos.z,
            (int)(8.0F * m),
            0.18 * m,
            0.18 * m,
            0.18 * m,
            0.12 * m
        );
        world.spawnParticles(
            ParticleTypes.ENCHANTED_HIT,
            highFivePos.x,
            highFivePos.y,
            highFivePos.z,
            (int)(5.0F * m),
            0.15 * m,
            0.15 * m,
            0.15 * m,
            0.09 * m
        );
        float shake = 0.35F + 0.25F * Math.max(0, tier);
        DapFlair.FlairShakePayload pulse = new DapFlair.FlairShakePayload(shake, 200);
        ServerPlayNetworking.send(player1, pulse);
        ServerPlayNetworking.send(player2, pulse);
        Random rng = new Random();
        if (rng.nextFloat() < 0.12F) {
            Object[] flav = DapFlair.flavour(rng, false);
            String label = (String)flav[0];
            if ((Boolean)flav[1]) {
                DapFlair.playRareEffect(world, highFivePos, label);
                player1.sendMessage(Text.literal(label), true);
                player2.sendMessage(Text.literal(label), true);
            }
        }

        PactHandler.onHandshakeCounted(player1, player2);
        boolean fastP1 = handRaiseSprinting.getOrDefault(player1.getUuid(), false);
        boolean fastP2 = handRaiseSprinting.getOrDefault(player2.getUuid(), false);
        if (CoopMovesConfig.get().enableHypeTornado || CoopMovesConfig.get().enableHighFiveBuffs) {
            if (fastP1) {
                HighFiveStreakHandler.onFastFive(player1);
            }

            if (fastP2) {
                HighFiveStreakHandler.onFastFive(player2);
            }
        }
    }

    private static void executeTier0(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.2F, 1.1F);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8F, 1.8F);
        spawnStarBurst(world, pos, 10, 0.3);
        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 8, 0.1, 0.1, 0.1, 0.08);
        world.spawnParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 6, 0.2, 0.2, 0.2, 0.02);
        applyKnockback(p1, p2, pos, 0.1);
    }

    private static void executeTier1(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.5F, 1.0F);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0F, 2.0F);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.PLAYERS, 0.8F, 1.2F);
        spawnStarBurst(world, pos, 16, 0.5);
        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 15, 0.15, 0.15, 0.15, 0.12);
        world.spawnParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 10, 0.25, 0.25, 0.25, 0.03);
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 5, 0.2, 0.2, 0.2, 0.05);
        applyKnockback(p1, p2, pos, 0.4);
    }

    private static void executeTier2(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 2.0F, 0.9F);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 1.2F, 1.0F);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.2F, 2.0F);
        spawnStarBurst(world, pos, 24, 0.7);
        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 25, 0.2, 0.2, 0.2, 0.18);
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 15, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 15, 0.3, 0.3, 0.3, 0.05);
        applyKnockback(p1, p2, pos, 0.8);
    }

    private static void executeTier3(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        world.playSound(null, pos.x, pos.y, pos.z, ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 2.0F, 1.0F);
        world.playSound(null, pos.x, pos.y, pos.z, ModSounds.EXPLOSION_IMPACT, SoundCategory.PLAYERS, 1.5F, 1.0F);
        world.playSound(
            null, pos.x, pos.y, pos.z, (SoundEvent)SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.2F, 1.3F
        );
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 1.5F, 1.0F);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 2.0F, 0.5F);
        spawnStarBurst(world, pos, 32, 1.0);
        world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 3, 0.5, 0.5, 0.5, 0.0);
        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 40, 0.3, 0.3, 0.3, 0.25);
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 25, 0.5, 0.5, 0.5, 0.15);
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 2, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 20, 0.4, 0.4, 0.4, 0.1);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, 30, 0.5, 0.5, 0.5, 0.3);
        createBattleShockwave(world, pos, p1, p2, 10.0);
        ChargedDapHandler.applyImpactFreeze(p1, p2, 3);
        applyKnockback(p1, p2, pos, 0.3);
        p1.sendMessage(Text.literal("§6§l⚡ SHOCKWAVE! ⚡"), true);
        p2.sendMessage(Text.literal("§6§l⚡ SHOCKWAVE! ⚡"), true);
    }

    private static void createBattleShockwave(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2, double radius) {
        for (int ring = 1; ring <= 5; ring++) {
            double r = ring * 2.0;
            int points = (int)(r * 8.0);

            for (int i = 0; i < points; i++) {
                double angle = Math.toRadians(360.0 / points * i);
                double x = pos.x + Math.cos(angle) * r;
                double z = pos.z + Math.sin(angle) * r;
                if (ring <= 2) {
                    world.spawnParticles(ParticleTypes.CLOUD, x, pos.y, z, 1, 0.1, 0.2, 0.1, 0.02);
                } else if (ring <= 4) {
                    world.spawnParticles(ParticleTypes.SWEEP_ATTACK, x, pos.y + 0.5, z, 1, 0.0, 0.0, 0.0, 0.0);
                } else {
                    world.spawnParticles(ParticleTypes.CRIT, x, pos.y + 0.5, z, 2, 0.1, 0.1, 0.1, 0.05);
                }
            }
        }

        Box pushBox = new Box(
            pos.x - radius,
            pos.y - radius,
            pos.z - radius,
            pos.x + radius,
            pos.y + radius,
            pos.z + radius
        );

        for (Entity entity : world.getOtherEntities(null, pushBox)) {
            if (entity != p1 && entity != p2) {
                double dist = entity.getPos().distanceTo(pos);
                if (!(dist > radius) && !(dist < 0.5)) {
                    double strength = (1.0 - dist / radius) * 4.0 + 1.0;
                    Vec3d dir = entity.getPos().subtract(pos).normalize();
                    if (dir.lengthSquared() < 0.01) {
                        dir = new Vec3d(Math.random() - 0.5, 0.0, Math.random() - 0.5).normalize();
                    }

                    entity.addVelocity(dir.x * strength, strength * 0.6, dir.z * strength);
                    entity.velocityModified = true;
                    world.spawnParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 1.0, entity.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
                }
            }
        }
    }

    public static void spawnStarBurst(ServerWorld world, Vec3d pos, int rays, double spread) {
        for (int i = 0; i < rays; i++) {
            double angle = (Math.PI * 2) * i / rays;
            double dx = Math.cos(angle) * spread;
            double dz = Math.sin(angle) * spread;
            world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 2, dx, 0.2, dz, 0.15);
        }
    }

    private static void applyKnockback(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d center, double strength) {
        Vec3d dir1 = p1.getPos().subtract(center).normalize();
        Vec3d dir2 = p2.getPos().subtract(center).normalize();
        if (dir1.lengthSquared() < 0.01) {
            dir1 = new Vec3d(1.0, 0.0, 0.0);
        }

        if (dir2.lengthSquared() < 0.01) {
            dir2 = new Vec3d(-1.0, 0.0, 0.0);
        }

        double push = 0.15 * strength;
        p1.setVelocity(dir1.x * push, 0.05, dir1.z * push);
        p2.setVelocity(dir2.x * push, 0.05, dir2.z * push);
        p1.velocityModified = true;
        p2.velocityModified = true;
    }

    private static void createHighFiveExplosion(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        double radius = 4.0;
        Box damageBox = new Box(
            pos.x - radius,
            pos.y - radius,
            pos.z - radius,
            pos.x + radius,
            pos.y + radius,
            pos.z + radius
        );

        for (Entity entity : world.getOtherEntities(null, damageBox)) {
            if (entity != p1 && entity != p2) {
                double dist = entity.getPos().distanceTo(pos);
                if (!(dist > radius)) {
                    double knockbackStrength = (1.0 - dist / radius) * 2.0;
                    Vec3d knockDir = entity.getPos().subtract(pos).normalize();
                    entity.addVelocity(knockDir.x * knockbackStrength, knockbackStrength * 0.5, knockDir.z * knockbackStrength);
                    entity.velocityModified = true;
                    if (entity instanceof ServerPlayerEntity target) {
                        float damage = (float)((1.0 - dist / radius) * 8.0);
                        target.damage(world.getDamageSources().explosion(null, null), damage);
                    }
                }
            }
        }
    }

    private static boolean isOnCooldown(UUID uuid) {
        Long cooldownStart = highFiveCooldown.get(uuid);
        return cooldownStart == null ? false : System.currentTimeMillis() - cooldownStart < 1000L;
    }

    public static boolean hasHandRaised(UUID uuid) {
        return handRaisedTime.containsKey(uuid);
    }

    public static boolean isInBlockingAnimation(UUID uuid) {
        if (highFiveAnimStart.containsKey(uuid)) {
            return true;
        } else if (endAnimTime.containsKey(uuid)) {
            return true;
        } else {
            return comboFreezeEnd.containsKey(uuid) ? true : ChargedDapHandler.isInBlockingAnimation(uuid);
        }
    }

    public static float getHighFiveAnimProgress(UUID uuid) {
        Long startTime = highFiveAnimStart.get(uuid);
        if (startTime == null) {
            return -1.0F;
        } else {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > 1500L) {
                highFiveAnimStart.remove(uuid);
                return -1.0F;
            } else {
                return (float)elapsed / 1500.0F;
            }
        }
    }

    private static void onComboRequest(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        long now = System.currentTimeMillis();
        Long windowStart = comboWindowStart.get(playerId);
        if (windowStart != null) {
            long elapsed = now - windowStart;
            if (elapsed > 1000L) {
                comboWindowStart.remove(playerId);
                comboPartner.remove(playerId);
                comboRequested.remove(playerId);
            } else {
                comboRequested.put(playerId, now);
                UUID partnerId = comboPartner.get(playerId);
                if (partnerId != null) {
                    ServerPlayerEntity partner = player.getServer().getPlayerManager().getPlayer(partnerId);
                    if (partner != null) {
                        if (comboWindowStart.containsKey(partnerId)) {
                            if (comboRequested.containsKey(partnerId)) {
                                executeCombo(player, partner);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void executeCombo(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();
        comboWindowStart.remove(id1);
        comboWindowStart.remove(id2);
        comboPartner.remove(id1);
        comboPartner.remove(id2);
        comboRequested.remove(id1);
        comboRequested.remove(id2);
        handRaisedTime.remove(id1);
        handRaisedTime.remove(id2);
        startAnimTime.remove(id1);
        startAnimTime.remove(id2);
        syncHandRaised(p1, false);
        syncHandRaised(p2, false);
        comboFreezeEnd.put(id1, now + 2250L);
        comboFreezeEnd.put(id2, now + 2250L);
        frozenPositions.put(id1, p1.getPos());
        frozenPositions.put(id2, p2.getPos());
        p1.setVelocity(Vec3d.ZERO);
        p2.setVelocity(Vec3d.ZERO);
        p1.velocityModified = true;
        p2.velocityModified = true;

        for (ServerPlayerEntity p : PlayerLookup.all(p1.getServer())) {
            ServerPlayNetworking.send(p, new HighFiveHandler.FreezeStatePayload(id1, true));
            ServerPlayNetworking.send(p, new HighFiveHandler.FreezeStatePayload(id2, true));
        }

        PoseNetworking.broadcastAnimState(p1, 21);
        PoseNetworking.broadcastAnimState(p2, 21);
        pendingComboImpacts.put(id1, new HighFiveHandler.ComboImpact(p1, p2, now + 1290L));
        p1.sendMessage(Text.literal("§6§l✨ COMBO! ✨"), true);
        p2.sendMessage(Text.literal("§6§l✨ COMBO! ✨"), true);
    }

    private static void executeSecondImpact(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        Vec3d pos = p1.getPos().add(p2.getPos()).multiply(0.5).add(0.0, 0.5, 0.0);
        ServerWorld world = p1.getServerWorld();
        world.playSound(null, pos.x, pos.y, pos.z, ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.5F, 1.0F);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, 30, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 20, 0.3, 0.3, 0.3, 0.15);
        p1.sendMessage(Text.literal("§e⚡ PERFECT! ⚡"), true);
        p2.sendMessage(Text.literal("§e⚡ PERFECT! ⚡"), true);
    }

    private static void spawnComboAura(ServerWorld world, Vec3d pos1, Vec3d pos2) {
        int particleCount = 20;
        double radius = 1.5;

        for (int i = 0; i < particleCount; i++) {
            double angle = (Math.PI * 2) * i / particleCount;
            double x1 = pos1.x + Math.cos(angle) * radius;
            double z1 = pos1.z + Math.sin(angle) * radius;
            world.spawnParticles(ParticleTypes.SQUID_INK, x1, pos1.y + 1.0, z1, 1, 0.1, 0.3, 0.1, 0.02);
            double innerRadius = radius * 0.7;
            double x1Inner = pos1.x + Math.cos(angle) * innerRadius;
            double z1Inner = pos1.z + Math.sin(angle) * innerRadius;
            world.spawnParticles(ParticleTypes.END_ROD, x1Inner, pos1.y + 1.0, z1Inner, 1, 0.1, 0.3, 0.1, 0.02);
            double x2 = pos2.x + Math.cos(angle) * radius;
            double z2 = pos2.z + Math.sin(angle) * radius;
            world.spawnParticles(ParticleTypes.SQUID_INK, x2, pos2.y + 1.0, z2, 1, 0.1, 0.3, 0.1, 0.02);
            double x2Inner = pos2.x + Math.cos(angle) * innerRadius;
            double z2Inner = pos2.z + Math.sin(angle) * innerRadius;
            world.spawnParticles(ParticleTypes.END_ROD, x2Inner, pos2.y + 1.0, z2Inner, 1, 0.1, 0.3, 0.1, 0.02);
        }
    }

    public static boolean isInComboFreeze(UUID playerId) {
        Long freezeEnd = comboFreezeEnd.get(playerId);
        return freezeEnd == null ? false : System.currentTimeMillis() < freezeEnd;
    }

    public static void cleanup(UUID playerId) {
        handRaisedTime.remove(playerId);
        highFiveCooldown.remove(playerId);
        highFiveAnimStart.remove(playerId);
        startAnimTime.remove(playerId);
        endAnimTime.remove(playerId);
        speedHistory.remove(playerId);
        pendingEffects.remove(playerId);
        comboWindowStart.remove(playerId);
        comboPartner.remove(playerId);
        comboRequested.remove(playerId);
        comboFreezeEnd.remove(playerId);
        pendingComboImpacts.remove(playerId);
        frozenPositions.remove(playerId);
        sikeMode.remove(playerId);
        sikeStunEnd.remove(playerId);
        sikeSlowEnd.remove(playerId);
    }

    private static void applySikeSlow(ServerPlayerEntity player) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(SIKE_SLOW_ID);
            attr.addTemporaryModifier(new EntityAttributeModifier(SIKE_SLOW_ID, -1.0, Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeSikeSlow(ServerPlayerEntity player) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(SIKE_SLOW_ID);
        }
    }

    private static void executeMutualSike(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        long now = System.currentTimeMillis();

        for (ServerPlayerEntity p : List.of(p1, p2)) {
            UUID id = p.getUuid();
            handRaisedTime.remove(id);
            startAnimTime.remove(id);
            syncHandRaised(p, false);
            PoseNetworking.broadcastAnimState(p, 0);
            highFiveCooldown.put(id, now);
        }

        Vec3d toP2 = p2.getPos().subtract(p1.getPos()).normalize();
        if (toP2.lengthSquared() < 0.01) {
            toP2 = new Vec3d(1.0, 0.0, 0.0);
        }

        ServerWorld world = p1.getServerWorld();
        p1.damage(world.getDamageSources().magic(), 6.0F);
        p2.damage(world.getDamageSources().magic(), 6.0F);
        p1.setVelocity(toP2.negate().multiply(0.65).add(0.0, 0.5, 0.0));
        p1.velocityModified = true;
        p2.setVelocity(toP2.multiply(0.65).add(0.0, 0.5, 0.0));
        p2.velocityModified = true;
        Vec3d mid = p1.getPos().add(p2.getPos()).multiply(0.5).add(0.0, 1.0, 0.0);
        world.spawnParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 24, 0.4, 0.4, 0.4, 0.2);
        world.spawnParticles(ParticleTypes.SMOKE, mid.x, mid.y, mid.z, 12, 0.3, 0.3, 0.3, 0.02);
        world.spawnParticles(ParticleTypes.FALLING_WATER, mid.x, mid.y + 0.5, mid.z, 20, 0.3, 0.2, 0.3, 0.02);
        world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.2F, 0.7F);
        world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.0F, 0.8F);
        world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_WITCH_CELEBRATE, SoundCategory.PLAYERS, 0.8F, 1.2F);
        p1.sendMessage(Text.literal("§c§l\ud83d\udca5 MUTUAL SIKE! You both played dirty!"), true);
        p2.sendMessage(Text.literal("§c§l\ud83d\udca5 MUTUAL SIKE! You both played dirty!"), true);
    }

    private static class BeaconRemoval {
        BlockPos beaconPos;
        BlockPos glassPos;
        ServerWorld world;
        long removeTime;

        BeaconRemoval(BlockPos beaconPos, BlockPos glassPos, ServerWorld world, long removeTime) {
            this.beaconPos = beaconPos;
            this.glassPos = glassPos;
            this.world = world;
            this.removeTime = removeTime;
        }
    }

    private static class ComboImpact {
        ServerPlayerEntity p1;
        ServerPlayerEntity p2;
        long impactTime;

        ComboImpact(ServerPlayerEntity p1, ServerPlayerEntity p2, long impactTime) {
            this.p1 = p1;
            this.p2 = p2;
            this.impactTime = impactTime;
        }
    }

    public record ComboRequestPayload() implements CustomPayload {
        public static final Id<HighFiveHandler.ComboRequestPayload> ID = new Id(HighFiveHandler.COMBO_REQUEST_ID);
        public static final PacketCodec<PacketByteBuf, HighFiveHandler.ComboRequestPayload> CODEC = PacketCodec.unit(
            new HighFiveHandler.ComboRequestPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ComboWindowClosePayload(UUID playerId) implements CustomPayload {
        public static final Id<HighFiveHandler.ComboWindowClosePayload> ID = new Id(HighFiveHandler.COMBO_WINDOW_CLOSE_ID);
        public static final PacketCodec<PacketByteBuf, HighFiveHandler.ComboWindowClosePayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeUuid(payload.playerId), buf -> new HighFiveHandler.ComboWindowClosePayload(buf.readUuid())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ComboWindowPayload(UUID playerId) implements CustomPayload {
        public static final Id<HighFiveHandler.ComboWindowPayload> ID = new Id(HighFiveHandler.COMBO_WINDOW_ID);
        public static final PacketCodec<PacketByteBuf, HighFiveHandler.ComboWindowPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeUuid(payload.playerId), buf -> new HighFiveHandler.ComboWindowPayload(buf.readUuid())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FreezeStatePayload(UUID playerId, boolean frozen) implements CustomPayload {
        public static final Id<HighFiveHandler.FreezeStatePayload> ID = new Id(HighFiveHandler.FREEZE_STATE_ID);
        public static final PacketCodec<PacketByteBuf, HighFiveHandler.FreezeStatePayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeUuid(payload.playerId);
            buf.writeBoolean(payload.frozen);
        }, buf -> new HighFiveHandler.FreezeStatePayload(buf.readUuid(), buf.readBoolean()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HandRaisedSyncPayload(UUID playerId, boolean raised) implements CustomPayload {
        public static final Id<HighFiveHandler.HandRaisedSyncPayload> ID = new Id(HighFiveHandler.HAND_RAISED_SYNC_ID);
        public static final PacketCodec<PacketByteBuf, HighFiveHandler.HandRaisedSyncPayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeUuid(payload.playerId);
            buf.writeBoolean(payload.raised);
        }, buf -> new HighFiveHandler.HandRaisedSyncPayload(buf.readUuid(), buf.readBoolean()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HighFiveAnimPayload(UUID playerId, int animState) implements CustomPayload {
        public static final Id<HighFiveHandler.HighFiveAnimPayload> ID = new Id(HighFiveHandler.HIGH_FIVE_ANIM_ID);
        public static final PacketCodec<PacketByteBuf, HighFiveHandler.HighFiveAnimPayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeUuid(payload.playerId);
            buf.writeInt(payload.animState);
        }, buf -> new HighFiveHandler.HighFiveAnimPayload(buf.readUuid(), buf.readInt()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HighFiveHoldPayload() implements CustomPayload {
        public static final Id<HighFiveHandler.HighFiveHoldPayload> ID = new Id(HighFiveHandler.HIGH_FIVE_HOLD_ID);
        public static final PacketCodec<PacketByteBuf, HighFiveHandler.HighFiveHoldPayload> CODEC = PacketCodec.unit(
            new HighFiveHandler.HighFiveHoldPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HighFiveRequestPayload() implements CustomPayload {
        public static final Id<HighFiveHandler.HighFiveRequestPayload> ID = new Id(HighFiveHandler.HIGH_FIVE_REQUEST_ID);
        public static final PacketCodec<PacketByteBuf, HighFiveHandler.HighFiveRequestPayload> CODEC = PacketCodec.unit(
            new HighFiveHandler.HighFiveRequestPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HighFiveSuccessPayload(double x, double y, double z, UUID player1, UUID player2, int tier) implements CustomPayload {
        public static final Id<HighFiveHandler.HighFiveSuccessPayload> ID = new Id(HighFiveHandler.HIGH_FIVE_SUCCESS_ID);
        public static final PacketCodec<PacketByteBuf, HighFiveHandler.HighFiveSuccessPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeDouble(payload.x);
                buf.writeDouble(payload.y);
                buf.writeDouble(payload.z);
                buf.writeUuid(payload.player1);
                buf.writeUuid(payload.player2);
                buf.writeInt(payload.tier);
            },
            buf -> new HighFiveHandler.HighFiveSuccessPayload(
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readUuid(), buf.readUuid(), buf.readInt()
            )
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static class ParticleBeam {
        ServerWorld world;
        Vec3d startPos;
        boolean isYellow;
        long endTime;

        ParticleBeam(ServerWorld world, Vec3d startPos, boolean isYellow, long endTime) {
            this.world = world;
            this.startPos = startPos;
            this.isYellow = isYellow;
            this.endTime = endTime;
        }
    }

    private static class PendingHighFive {
        ServerPlayerEntity p1;
        ServerPlayerEntity p2;
        Vec3d pos;
        int tier;
        long effectTime;

        PendingHighFive(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d pos, int tier, long effectTime) {
            this.p1 = p1;
            this.p2 = p2;
            this.pos = pos;
            this.tier = tier;
            this.effectTime = effectTime;
        }
    }

    public record SikeRequestPayload() implements CustomPayload {
        public static final Id<HighFiveHandler.SikeRequestPayload> ID = new Id(new Identifier("testcoop", "highfive_sike_request"));
        public static final PacketCodec<PacketByteBuf, HighFiveHandler.SikeRequestPayload> CODEC = PacketCodec.unit(new HighFiveHandler.SikeRequestPayload());

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
