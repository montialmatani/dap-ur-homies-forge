package com.cooptest;

import com.cooptest.bully.BullyDapHandler;
import com.cooptest.client.CoopAnimationHandler;
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
import com.cooptest.fabricshim.fabric.api.event.player.UseEntityCallback;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.PlayerLookup;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import net.minecraft.world.World.ExplosionSourceType;
import net.minecraft.world.Heightmap.Type;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class ChargedDapHandler {
    public static final float DAP_RANGE = 1.6F;
    public static volatile boolean heavenRecMode = false;
    public static final float REC_DAP_RANGE = 4.0F;
    public static final long REC_PERFECT_WINDOW_MS = 400L;
    public static final long REC_RELEASE_WINDOW_MS = 1500L;
    public static final long REC_FIRE_DELAY_MS = 500L;
    public static final long REC_FIRE_BUILD_TIME_MS = 500L;
    public static final long REC_HEAVEN_READY_TIME_MS = 1000L;
    public static final double REC_MIN_MOVEMENT_SPEED = 0.0;
    public static final double REC_HEAVEN_MIN_SPEED = 0.0;
    public static final long CHARGE_TIME_MS = 250L;
    public static final long MID_DAP_IMPACT_AT_MS = 210L;
    public static final long MID_DAP_SHAKE_END_MS = 700L;
    public static final float MID_DAP_SHAKE_AMOUNT = 1.05F;
    public static final long RELEASE_WINDOW_MS = 500L;
    public static final long PERFECT_WINDOW_MS = 85L;
    public static final long GOOD_SYNC_MS = 300L;
    public static final long COOLDOWN_MS = 1500L;
    public static final long WHIFF_COOLDOWN_MS = 800L;
    public static final long FIRE_DELAY_MS = 2000L;
    public static final long FIRE_BUILD_TIME_MS = 2000L;
    public static final long MID_WINDOW_MS = 200L;
    public static final long MID_DAP_FREEZE_MS = 880L;
    private static final long PERFECT_DAP_IMPACT_FREEZE_MS = 1210L;
    private static final long PERFECT_DAP_BLOCK_MS = 1210L;
    public static final double MIN_MOVEMENT_SPEED = 1.5;
    public static final long FIRE_GRACE_PERIOD_MS = 500L;
    public static final double SPEED_BONUS_THRESHOLD = 8.0;
    public static final double SPEED_TIER_4_THRESHOLD = 25.0;
    public static final double PERFECT_LEGENDARY_MIN_INDIVIDUAL_SPEED = 10.0;
    public static final int SPEED_HISTORY_TICKS = 40;
    public static final Map<UUID, Long> chargeStartTime = new HashMap<>();
    public static final Map<UUID, Long> releaseTime = new HashMap<>();
    public static final Map<UUID, UUID> waitingForPartner = new HashMap<>();
    public static final Map<UUID, Long> cooldowns = new HashMap<>();
    public static final Map<UUID, Long> fireStartTime = new HashMap<>();
    public static final Map<UUID, Long> fireGraceTime = new HashMap<>();
    public static final Map<UUID, Float> fireLevel = new HashMap<>();
    public static final Map<UUID, Long> fireMaxedStartTime = new HashMap<>();
    public static final Set<UUID> heavenReady = new HashSet<>();
    public static final long HEAVEN_READY_TIME_MS = 5000L;
    private static final List<ChargedDapHandler.HeavenParticleSpawner> activeHeavenParticles = new ArrayList<>();
    public static final Map<UUID, LinkedList<Double>> speedHistory = new HashMap<>();
    public static final Map<UUID, Integer> impactFreezeTicks = new HashMap<>();
    private static final Map<UUID, Long> perfectDapStartTime = new HashMap<>();
    private static final Map<UUID, UUID> perfectDapPartner = new HashMap<>();
    private static final Map<UUID, Long> perfectDapFreezeEnd = new HashMap<>();
    private static final Map<UUID, Boolean> perfectDapImpactSent = new HashMap<>();
    private static final Map<UUID, Boolean> perfectDapExtendHit = new HashMap<>();
    private static final Map<UUID, Long> comboCooldown = new HashMap<>();
    private static final Map<UUID, Boolean> fireComboActive = new HashMap<>();
    private static final Map<UUID, Long> underwaterRemovalStart = new HashMap<>();
    private static final Map<UUID, Vec3d> underwaterRemovalPos = new HashMap<>();
    private static final Map<UUID, ServerWorld> underwaterRemovalWorld = new HashMap<>();
    private static final long FIRE_DAP_HIT_LENGTH = 2292L;
    private static final long FIRE_IMPACT_TIME = 210L;
    private static final long FIRE_COMBO_QTE_OPEN_MS = 830L;
    private static final long FIRE_COMBO_QTE_CLOSE_MS = 1650L;
    private static final long FIRE_COMBO_FREEZE_MS = 4000L;
    private static final long FIRE_COMBO_ARM_IMPACT = 1330L;
    private static final long FIRE_COMBO_TORNADO = 1460L;
    private static final Map<UUID, Long> fireDapStartTime = new HashMap<>();
    private static final Map<UUID, UUID> fireDapPartner = new HashMap<>();
    private static final Map<UUID, Boolean> inFireDapHit = new HashMap<>();
    private static final Map<UUID, Boolean> fireCircleSpawned = new HashMap<>();
    private static final Map<UUID, Long> fireDapComboRequestTime = new HashMap<>();
    private static final Map<UUID, Long> fireDapComboFreezeEnd = new HashMap<>();
    private static final Map<UUID, ChargedDapHandler.HeavenDapData> heavenPlayers = new HashMap<>();
    private static final List<ChargedDapHandler.TornadoSwirlEntity> activeTornadoSwirls = new ArrayList<>();
    private static long tornadoStartTime = 0L;
    private static boolean tornadoActive = false;
    private static Vec3d tornadoCenter = null;
    private static ServerWorld tornadoWorld = null;
    private static long auraBeamStartTime = 0L;
    private static boolean auraBeamsActive = false;
    private static UUID auraBeamPlayer1 = null;
    private static UUID auraBeamPlayer2 = null;
    private static final Map<UUID, Vec3d> smoothTPTarget = new HashMap<>();
    private static final Map<UUID, Integer> smoothTPProgress = new HashMap<>();
    private static final Map<UUID, ArmorStandEntity> fireDapArmorStands = new HashMap<>();
    private static final Map<UUID, ArmorStandEntity> perfectDapArmorStands = new HashMap<>();
    private static final Map<UUID, ChargedDapHandler.FireDapScheduledEvent> pendingFireArmImpacts = new HashMap<>();
    private static final Map<UUID, ChargedDapHandler.FireDapScheduledEvent> pendingFireTornadoSpawns = new HashMap<>();
    private static final Map<UUID, ChargedDapHandler.FireComboQTE> fireComboQTEs = new HashMap<>();
    private static MinecraftServer serverRef = null;
    private static final List<ChargedDapHandler.SaturnRing> activeSaturnRings = new ArrayList<>();
    private static final Map<UUID, Long> impactFreezeEnd = new HashMap<>();
    private static final long IMPACT_FREEZE_MS = 90L;
    public static final Map<UUID, Long> blockingAnimEndTime = new HashMap<>();
    private static final List<ChargedDapHandler.ScheduledParticles> scheduledParticles = new ArrayList<>();
    private static final Set<UUID> highFivePartners = new HashSet<>();
    private static final Map<UUID, Long> perfectFriendshipLevitation = new HashMap<>();
    private static final Map<UUID, UUID> perfectFriendshipPartner = new HashMap<>();
    private static final List<ChargedDapHandler.ScheduledPerfectDapEffect> scheduledPerfectDapEffects = new ArrayList<>();
    public static final Identifier CHARGE_START_ID = new Identifier("cooptest", "charged_dap_start");
    public static final Identifier CHARGE_RELEASE_ID = new Identifier("cooptest", "charged_dap_release");
    public static final Identifier CHARGE_SYNC_ID = new Identifier("cooptest", "charged_dap_sync");
    public static final Identifier DAP_RESULT_ID = new Identifier("cooptest", "charged_dap_result");
    public static final Identifier WHIFF_COOLDOWN_ID = new Identifier("cooptest", "whiff_cooldown");
    public static final Identifier IMPACT_FRAME_ID = new Identifier("cooptest", "impact_frame");
    public static final Identifier PERFECT_DAP_FREEZE_ID = new Identifier("cooptest", "perfect_dap_freeze");
    public static final Identifier PERFECT_DAP_IMPACT_FRAME_ID = new Identifier("cooptest", "perfect_dap_impact_frame");
    public static final Identifier FACING_DAP_IMPACT_ID = new Identifier("cooptest", "facing_dap_impact");
    public static final Identifier FIRE_DAP_J_PRESS_ID = new Identifier("cooptest", "fire_dap_j_press");
    public static final Identifier FIRE_DAP_WINDOW_ID = new Identifier("cooptest", "fire_dap_window");
    public static final Identifier FIRE_DAP_FREEZE_ID = new Identifier("cooptest", "fire_dap_freeze");
    public static final Identifier FIRE_DAP_FP_ID = new Identifier("cooptest", "fire_dap_fp");
    public static final double DAP_REACH_FORWARD_OFFSET = 0.7;
    public static final double DAP_REACH_RADIUS = 0.7;
    private static long tickSpeedRestoreTime = 0L;
    private static final Set<UUID> heavenFlightGranted = new HashSet<>();

    public static float dapRange() {
        return heavenRecMode ? 4.0F : 1.6F;
    }

    public static double minMovementSpeed() {
        return heavenRecMode ? 0.0 : 1.5;
    }

    public static long heavenReadyTimeMs() {
        return heavenRecMode ? 1000L : 5000L;
    }

    public static double heavenMinSpeed() {
        return heavenRecMode ? 0.0 : 10.0;
    }

    public static void applyDapCooldown(ServerPlayerEntity player) {
        if (player != null) {
            long ms = CoopMovesConfig.get().dapCooldownMs;
            cooldowns.put(player.getUuid(), System.currentTimeMillis() + ms);

            try {
                ServerPlayNetworking.send(player, new ChargedDapHandler.WhiffCooldownPayload(ms));
            } catch (Throwable var4) {
            }
        }
    }

    public static long chargeTimeMs() {
        return CoopMovesConfig.get().dapChargeWindowMs;
    }

    public static long releaseWindowMs() {
        return heavenRecMode ? 1500L : CoopMovesConfig.get().dapReleaseWindowMs;
    }

    public static long perfectWindowMs() {
        return heavenRecMode ? 400L : CoopMovesConfig.get().dapPerfectWindowMs;
    }

    public static long cooldownMs() {
        return CoopMovesConfig.get().dapCooldownMs;
    }

    public static long whiffCooldownMs() {
        return CoopMovesConfig.get().dapWhiffCooldownMs;
    }

    public static long fireDelayMs() {
        return heavenRecMode ? 500L : CoopMovesConfig.get().dapFireDelayMs;
    }

    public static long fireBuildTimeMs() {
        return heavenRecMode ? 500L : CoopMovesConfig.get().dapFireBuildTimeMs;
    }

    public static void freezeOnImpact(UUID playerId) {
        long now = System.currentTimeMillis();
        impactFreezeEnd.put(playerId, now + 90L);
    }

    public static boolean isInImpactFreeze(UUID playerId) {
        Long freezeEnd = impactFreezeEnd.get(playerId);
        if (freezeEnd == null) {
            return false;
        } else if (System.currentTimeMillis() >= freezeEnd) {
            impactFreezeEnd.remove(playerId);
            return false;
        } else {
            return true;
        }
    }

    private static void broadcastHeavenReadyStatus(MinecraftServer server, UUID playerId, boolean ready) {
        ChargedDapHandler.HeavenReadyPayload payload = new ChargedDapHandler.HeavenReadyPayload(playerId, ready);

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(ChargedDapHandler.ChargeStartPayload.ID, ChargedDapHandler.ChargeStartPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChargedDapHandler.ChargeReleasePayload.ID, ChargedDapHandler.ChargeReleasePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChargedDapHandler.HeavenReadyPayload.ID, ChargedDapHandler.HeavenReadyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChargedDapHandler.ChargeSyncPayload.ID, ChargedDapHandler.ChargeSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChargedDapHandler.DapResultPayload.ID, ChargedDapHandler.DapResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChargedDapHandler.WhiffCooldownPayload.ID, ChargedDapHandler.WhiffCooldownPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChargedDapHandler.ImpactFramePayload.ID, ChargedDapHandler.ImpactFramePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChargedDapHandler.PerfectDapFreezePayload.ID, ChargedDapHandler.PerfectDapFreezePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChargedDapHandler.PerfectDapImpactFramePayload.ID, ChargedDapHandler.PerfectDapImpactFramePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChargedDapHandler.FacingDapImpactPayload.ID, ChargedDapHandler.FacingDapImpactPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChargedDapHandler.FireDapJPressPayload.ID, ChargedDapHandler.FireDapJPressPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChargedDapHandler.FireDapWindowPayload.ID, ChargedDapHandler.FireDapWindowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChargedDapHandler.FireDapFreezePayload.ID, ChargedDapHandler.FireDapFreezePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChargedDapHandler.FireDapFirstPersonPayload.ID, ChargedDapHandler.FireDapFirstPersonPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(QTEButtonPressPayload.ID, QTEButtonPressPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QTEWindowPayload.ID, QTEWindowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QTEClearPayload.ID, QTEClearPayload.CODEC);
    }

    public static void register() {
        HeavenCrater.register();
        ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> revokeHeavenFlight(handler.player));
        ServerPlayNetworking.registerGlobalReceiver(ChargedDapHandler.ChargeStartPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (CoopMovesConfig.get().enableDap) {
                    onChargeStart(player);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(ChargedDapHandler.ChargeReleasePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (CoopMovesConfig.get().enableDap) {
                    onChargeRelease(player);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(ChargedDapHandler.FireDapJPressPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> onFireComboPress(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(QTEButtonPressPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            String button = payload.button();
            context.server().execute(() -> {
                if (!PerfectDapComboHandler.onButtonPress(player, button)) {
                    if (!DapFlair.onButtonPress(player, button)) {
                        if (!StrongSlapHandler.onButtonPress(player, button)) {
                            if (!HuddleHandler.onButtonPress(player, button)) {
                                if (!CoopMovesConfig.get().enableDapCombo || !DapComboChain.onButtonPress(player, button)) {
                                    QTEManager.onButtonPress(player, button);
                                }
                            }
                        }
                    }
                }
            });
        });
        UseEntityCallback.EVENT
            .register(
                (UseEntityCallback)(player, world, hand, entity, hitResult) -> {
                    if (world.isClient) {
                        return ActionResult.PASS;
                    } else if (!(player instanceof ServerPlayerEntity sp)) {
                        return ActionResult.PASS;
                    } else if (entity instanceof ServerPlayerEntity target) {
                        if (sp.isSneaking()) {
                            return ActionResult.PASS;
                        } else if (getChargePercent(sp.getUuid()) < 0.95F) {
                            return ActionResult.PASS;
                        } else if (getChargePercent(target.getUuid()) < 0.95F) {
                            NormalFacingDapHandler.recordRightClick(sp, target);
                            return ActionResult.PASS;
                        } else {
                            if (!NormalFacingDapHandler.isConfirmed(sp.getUuid(), target.getUuid())
                                && !NormalFacingDapHandler.isConfirmedOneSide(target.getUuid(), sp.getUuid())) {
                                NormalFacingDapHandler.recordRightClick(sp, target);
                            } else {
                                NormalFacingDapHandler.clearConfirm(sp.getUuid(), target.getUuid());
                                chargeStartTime.remove(sp.getUuid());
                                chargeStartTime.remove(target.getUuid());
                                broadcastChargeCancel(sp);
                                broadcastChargeCancel(target);
                                NormalFacingDapHandler.start(sp, target);
                            }

                            return ActionResult.PASS;
                        }
                    } else {
                        return ActionResult.PASS;
                    }
                }
            );
        ServerTickEvents.END_SERVER_TICK
            .register(
                (EndTick)server -> {
                    long now = System.currentTimeMillis();
                    serverRef = server;
                    Iterator<ChargedDapHandler.ScheduledParticles> particleIt = scheduledParticles.iterator();

                    while (particleIt.hasNext()) {
                        ChargedDapHandler.ScheduledParticles sp = particleIt.next();
                        if (now >= sp.spawnTime()) {
                            sp.world().spawnParticles(ParticleTypes.CRIT, sp.x(), sp.y(), sp.z(), 15, 0.3, 0.3, 0.3, 0.15);
                            sp.world().spawnParticles(ParticleTypes.ENCHANT, sp.x(), sp.y(), sp.z(), 10, 0.2, 0.2, 0.2, 0.1);
                            particleIt.remove();
                        }
                    }

                    Iterator<ChargedDapHandler.ScheduledPerfectDapEffect> effectIt = scheduledPerfectDapEffects.iterator();

                    while (effectIt.hasNext()) {
                        ChargedDapHandler.ScheduledPerfectDapEffect effect = effectIt.next();
                        if (now >= effect.effectTime()) {
                            ArmorStandEntity stand = perfectDapArmorStands.get(effect.p1().getUuid());
                            Vec3d pos;
                            if (stand != null && !stand.isRemoved()) {
                                pos = stand.getPos();
                            } else {
                                pos = effect.pos();
                            }

                            ServerWorld world = effect.world();
                            ServerPlayNetworking.send(effect.p1(), new ChargedDapHandler.PerfectDapImpactFramePayload(1));
                            ServerPlayNetworking.send(effect.p2(), new ChargedDapHandler.PerfectDapImpactFramePayload(1));
                            world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 5, 0.2, 0.2, 0.2, 0.0);
                            world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 40, 0.4, 0.4, 0.4, 0.12);
                            world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 50, 0.5, 0.5, 0.5, 0.15);
                            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.DAP_HIT, SoundCategory.PLAYERS, 1.5F, 1.0F);
                            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.IMPACT, SoundCategory.PLAYERS, 1.0F, 1.0F);
                            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 1.2F, 1.0F);
                            createExplosion(world, pos, effect.p1(), effect.p2(), 3.5, 6.0F);
                            if (CoopMovesConfig.get().perfectDapShockwaveEnabled) {
                                createShockwave(
                                    world,
                                    pos,
                                    effect.p1(),
                                    effect.p2(),
                                    CoopMovesConfig.get().perfectDapShockwaveRadius,
                                    CoopMovesConfig.get().perfectDapShockwaveStrength
                                );
                            }

                            handleUnderwaterPerfectDap(world, pos, effect.p1(), effect.p2());
                            effectIt.remove();
                        }
                    }

                    Iterator<Entry<UUID, Long>> underwaterIt = underwaterRemovalStart.entrySet().iterator();

                    while (underwaterIt.hasNext()) {
                        Entry<UUID, Long> entry = underwaterIt.next();
                        UUID trackId = entry.getKey();
                        long startTime = entry.getValue();
                        long elapsed = now - startTime;
                        if (elapsed >= 5000L) {
                            underwaterIt.remove();
                            underwaterRemovalPos.remove(trackId);
                            underwaterRemovalWorld.remove(trackId);
                        } else {
                            Vec3d posx = underwaterRemovalPos.get(trackId);
                            ServerWorld world = underwaterRemovalWorld.get(trackId);
                            if (posx != null && world != null && !CoopMovesConfig.get().noGriefMode) {
                                BlockPos centerPos = BlockPos.ofFloored(posx);
                                double radius = 3.0;
                                int radiusInt = (int)Math.ceil(radius);

                                for (int x = -radiusInt; x <= radiusInt; x++) {
                                    for (int y = -radiusInt; y <= radiusInt; y++) {
                                        for (int z = -radiusInt; z <= radiusInt; z++) {
                                            double distance = Math.sqrt(x * x + y * y + z * z);
                                            if (distance <= radius) {
                                                BlockPos blockPos = centerPos.add(x, y, z);
                                                if (world.getBlockState(blockPos).isOf(Blocks.WATER)) {
                                                    world.setBlockState(blockPos, Blocks.AIR.getDefaultState());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Iterator<ChargedDapHandler.HeavenParticleSpawner> heavenParticleIt = activeHeavenParticles.iterator();

                    while (heavenParticleIt.hasNext()) {
                        ChargedDapHandler.HeavenParticleSpawner spawner = heavenParticleIt.next();
                        if (now >= spawner.endTime) {
                            heavenParticleIt.remove();
                        } else {
                            ServerWorld world = spawner.world;
                            Vec3d posx = spawner.pos;
                            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, posx.x, posx.y, posx.z, 2, 3.0, 3.0, 3.0, 0.0);
                            world.spawnParticles(ParticleTypes.FIREWORK, posx.x, posx.y, posx.z, 10, 5.0, 5.0, 5.0, 0.3);
                            world.spawnParticles(ParticleTypes.END_ROD, posx.x, posx.y, posx.z, 5, 4.0, 4.0, 4.0, 0.2);
                            world.spawnParticles(ParticleTypes.FLASH, posx.x, posx.y, posx.z, 1, 0.0, 0.0, 0.0, 0.0);
                        }
                    }

                    Iterator<Entry<UUID, Long>> perfectDapIt = perfectDapStartTime.entrySet().iterator();

                    while (perfectDapIt.hasNext()) {
                        Entry<UUID, Long> entry = perfectDapIt.next();
                        UUID playerId = entry.getKey();
                        long startTime = entry.getValue();
                        long elapsed = now - startTime;
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                        if (player == null) {
                            perfectDapIt.remove();
                            perfectDapPartner.remove(playerId);
                            perfectDapFreezeEnd.remove(playerId);
                            perfectDapImpactSent.remove(playerId);
                            ArmorStandEntity standx = perfectDapArmorStands.remove(playerId);
                            if (standx != null && !standx.isRemoved()) {
                                standx.discard();
                            }
                        } else {
                            UUID partnerId = perfectDapPartner.get(playerId);
                            ServerPlayerEntity partner = partnerId != null ? server.getPlayerManager().getPlayer(partnerId) : null;
                            if (partner != null) {
                                ArmorStandEntity standx = perfectDapArmorStands.get(playerId);
                                if (standx != null && !standx.isRemoved()) {
                                    Vec3d p1Hand = player.getPos().add(0.0, 1.4, 0.0);
                                    Vec3d p2Hand = partner.getPos().add(0.0, 1.4, 0.0);
                                    Vec3d handMid = p1Hand.add(p2Hand).multiply(0.5);
                                    standx.setPosition(handMid.x, handMid.y, handMid.z);
                                    standx.setFireTicks(0);
                                }

                                smoothDapDescent(player, standx);
                            }

                            if (elapsed >= 150L && elapsed <= 1330L && partner != null) {
                                ServerWorld world = player.getServerWorld();
                                ArmorStandEntity standx = perfectDapArmorStands.get(playerId);
                                if (standx != null && !standx.isRemoved()) {
                                    Vec3d particlePos = standx.getPos();
                                } else {
                                    Vec3d var121 = player.getPos().add(partner.getPos()).multiply(0.5).add(0.0, 1.4, 0.0);
                                }
                            }

                            if (elapsed >= 290L && elapsed < 310L && partner != null && !perfectDapImpactSent.getOrDefault(playerId, false)) {
                                ServerPlayNetworking.send(player, new ChargedDapHandler.PerfectDapImpactFramePayload(1));
                                perfectDapImpactSent.put(playerId, true);
                            }

                            if (elapsed >= 812L && perfectDapFreezeEnd.containsKey(playerId)) {
                                perfectDapFreezeEnd.remove(playerId);
                                ServerPlayNetworking.send(player, new ChargedDapHandler.PerfectDapFreezePayload(false));
                                if (partnerId != null) {
                                    ServerPlayerEntity partner2 = server.getPlayerManager().getPlayer(partnerId);
                                    if (partner2 != null) {
                                        perfectDapFreezeEnd.remove(partnerId);
                                        ServerPlayNetworking.send(partner2, new ChargedDapHandler.PerfectDapFreezePayload(false));
                                    }
                                }
                            }

                            if (elapsed >= 1625L) {
                                perfectDapIt.remove();
                                perfectDapPartner.remove(playerId);
                                perfectDapFreezeEnd.remove(playerId);
                                perfectDapImpactSent.remove(playerId);
                                perfectDapExtendHit.remove(playerId);
                                ArmorStandEntity standx = perfectDapArmorStands.remove(playerId);
                                if (standx != null && !standx.isRemoved()) {
                                    standx.discard();
                                }
                            }
                        }
                    }

                    Iterator<Entry<UUID, ChargedDapHandler.HeavenDapData>> heavenIt = heavenPlayers.entrySet().iterator();

                    while (heavenIt.hasNext()) {
                        Entry<UUID, ChargedDapHandler.HeavenDapData> entry = heavenIt.next();
                        UUID playerId = entry.getKey();
                        ChargedDapHandler.HeavenDapData data = entry.getValue();
                        long elapsed = now - data.startTime;
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                        if (player == null) {
                            heavenIt.remove();
                        } else {
                            if (elapsed >= 3000L && elapsed <= 9000L) {
                                Vec3d posx = player.getPos();
                                data.world
                                    .spawnParticles(ParticleTypes.WHITE_ASH, posx.x, posx.y + 1.0, posx.z, 5, 1.0, 1.0, 1.0, 0.02);
                                data.world.spawnParticles(ParticleTypes.CLOUD, posx.x, posx.y, posx.z, 3, 0.5, 0.5, 0.5, 0.01);
                            }

                            if (elapsed >= 9500L && elapsed < 9600L) {
                                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 40, 0, false, false));
                            }

                            if (elapsed >= 11500L) {
                                Vec3d returnPos = data.originalMidpoint;
                                UUID partnerIdx = data.partnerId;
                                ServerPlayerEntity partnerx = server.getPlayerManager().getPlayer(partnerIdx);
                                if (partnerx != null) {
                                    double dx = partnerx.getX() - player.getX();
                                    double dz = partnerx.getZ() - player.getZ();
                                    float yawTowardsPartner = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
                                    float yawAwayFromPartner = yawTowardsPartner + 180.0F;
                                    revokeHeavenFlight(player);
                                    player.teleport(data.world, returnPos.x, returnPos.y, returnPos.z, yawAwayFromPartner, 0.0F);
                                    player.stopFallFlying();
                                    player.setVelocity(Vec3d.ZERO);
                                    player.velocityModified = true;
                                    ServerPlayNetworking.send(player, new ChargedDapHandler.PerfectDapFreezePayload(false));
                                    PoseNetworking.broadcastAnimState(player, 0);
                                    player.removeStatusEffect(StatusEffects.NAUSEA);
                                    if (partnerx != null && heavenPlayers.containsKey(partnerIdx)) {
                                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 255, false, false));
                                        partnerx.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 255, false, false));
                                        HeavenCrater.returnBlast(data.world, returnPos);
                                    }

                                    data.world
                                        .spawnParticles(
                                            ParticleTypes.FLASH,
                                            returnPos.x,
                                            returnPos.y + 1.0,
                                            returnPos.z,
                                            5,
                                            0.5,
                                            0.5,
                                            0.5,
                                            0.0
                                        );
                                    data.world
                                        .spawnParticles(
                                            ParticleTypes.EXPLOSION_EMITTER, returnPos.x, returnPos.y, returnPos.z, 10, 3.0, 3.0, 3.0, 0.5
                                        );
                                    new Thread(() -> {
                                        try {
                                            Thread.sleep(3000L);
                                            player.getServer().execute(() -> ServerPlayNetworking.send(player, new HeavenDapPayloads.RestoreVolumePayload()));
                                        } catch (InterruptedException var2) {
                                            var2.printStackTrace();
                                        }
                                    }).start();
                                    if (partnerx != null && heavenPlayers.containsKey(partnerIdx)) {
                                        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                                            p.sendMessage(
                                                Text.literal(
                                                    "§d§l✨ "
                                                        + player.getName().getString()
                                                        + " §7and §d§l"
                                                        + partnerx.getName().getString()
                                                        + " §7have achieved §d§lPERFECT FRIENDSHIP! ✨"
                                                ),
                                                false
                                            );
                                        }

                                        server.getOverworld()
                                            .playSound(
                                                null,
                                                returnPos.x,
                                                returnPos.y,
                                                returnPos.z,
                                                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                                                SoundCategory.PLAYERS,
                                                3.0F,
                                                1.0F
                                            );
                                        server.getOverworld()
                                            .playSound(
                                                null,
                                                returnPos.x,
                                                returnPos.y,
                                                returnPos.z,
                                                SoundEvents.ENTITY_PLAYER_LEVELUP,
                                                SoundCategory.PLAYERS,
                                                2.0F,
                                                1.5F
                                            );
                                    }
                                }

                                heavenIt.remove();
                            }
                        }
                    }

                    tickFireComboQTEs(server, now);
                    Iterator<Entry<UUID, Long>> fireDapIt = fireDapStartTime.entrySet().iterator();

                    while (fireDapIt.hasNext()) {
                        Entry<UUID, Long> entry = fireDapIt.next();
                        UUID playerId = entry.getKey();
                        long startTime = entry.getValue();
                        long elapsed = now - startTime;
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                        if (player == null) {
                            fireDapIt.remove();
                            inFireDapHit.remove(playerId);
                            fireDapPartner.remove(playerId);
                        } else if (!inFireDapHit.getOrDefault(playerId, false) && elapsed >= 2292L) {
                            fireDapIt.remove();
                            fireCircleSpawned.remove(playerId);
                        } else {
                            if (elapsed >= 210L && !fireCircleSpawned.getOrDefault(playerId, true) && inFireDapHit.getOrDefault(playerId, false)) {
                                UUID partnerIdx = fireDapPartner.get(playerId);
                                if (partnerIdx != null && fireDapStartTime.containsKey(partnerIdx)) {
                                    ServerPlayerEntity partnerx = server.getPlayerManager().getPlayer(partnerIdx);
                                    if (partnerx != null && !fireCircleSpawned.getOrDefault(partnerIdx, true)) {
                                        spawnFireCircle(player, partnerx);
                                        long freezeEnd = now + (2292L - elapsed);
                                        fireDapComboFreezeEnd.put(playerId, freezeEnd);
                                        fireDapComboFreezeEnd.put(partnerIdx, freezeEnd);

                                        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                                            ServerPlayNetworking.send(p, new ChargedDapHandler.FireDapFreezePayload(playerId, true));
                                            ServerPlayNetworking.send(p, new ChargedDapHandler.FireDapFreezePayload(partnerIdx, true));
                                        }

                                        fireCircleSpawned.put(playerId, true);
                                        fireCircleSpawned.put(partnerIdx, true);
                                    }
                                }
                            }

                            if (elapsed >= 210L && inFireDapHit.getOrDefault(playerId, false)) {
                                UUID partnerIdx = fireDapPartner.get(playerId);
                                if (partnerIdx != null) {
                                    ServerPlayerEntity partnerx = server.getPlayerManager().getPlayer(partnerIdx);
                                    if (partnerx != null) {
                                        ArmorStandEntity standx = fireDapArmorStands.get(playerId);
                                        if (standx != null && !standx.isRemoved()) {
                                            Vec3d p1Hand = player.getPos().add(0.0, 1.4, 0.0);
                                            Vec3d p2Hand = partnerx.getPos().add(0.0, 1.4, 0.0);
                                            Vec3d handMid = p1Hand.add(p2Hand).multiply(0.5);
                                            standx.setPosition(handMid.x, handMid.y, handMid.z);
                                            standx.setFireTicks(0);
                                            smoothDapDescent(player, standx);
                                            smoothDapDescent(partnerx, standx);
                                        }
                                    }
                                }
                            }

                            if (elapsed >= 2292L && inFireDapHit.getOrDefault(playerId, false)) {
                                fireDapIt.remove();
                                inFireDapHit.remove(playerId);
                                fireDapComboRequestTime.remove(playerId);
                                DapSessionManager.removeSessionForPlayer(playerId);
                                fireDapComboFreezeEnd.remove(playerId);

                                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                                    ServerPlayNetworking.send(p, new ChargedDapHandler.FireDapFreezePayload(playerId, false));
                                    ServerPlayNetworking.send(p, new ChargedDapHandler.FireDapFirstPersonPayload(playerId, false));
                                }

                                PoseNetworking.broadcastAnimState(player, 0);
                                releaseFireDapLeftovers(playerId);
                            }
                        }
                    }

                    for (Entry<UUID, Long> entry : new HashMap<>(fireDapComboFreezeEnd).entrySet()) {
                        UUID playerId = entry.getKey();
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                        if (player != null && !inFireDapHit.getOrDefault(playerId, false)) {
                            UUID partnerIdx = fireDapPartner.get(playerId);
                            if (partnerIdx != null) {
                                ServerPlayerEntity partnerx = server.getPlayerManager().getPlayer(partnerIdx);
                                if (partnerx != null) {
                                    teleportFireDapFacingEachOther(player, partnerx, 1.2);
                                }
                            }
                        }
                    }

                    Iterator<Entry<UUID, ChargedDapHandler.FireDapScheduledEvent>> armIt = pendingFireArmImpacts.entrySet().iterator();

                    while (armIt.hasNext()) {
                        Entry<UUID, ChargedDapHandler.FireDapScheduledEvent> entryx = armIt.next();
                        ChargedDapHandler.FireDapScheduledEvent event = entryx.getValue();
                        if (now >= event.executeTime) {
                            executeFireArmImpact(event.p1, event.p2);
                            armIt.remove();
                        }
                    }

                    Iterator<Entry<UUID, ChargedDapHandler.FireDapScheduledEvent>> tornadoIt = pendingFireTornadoSpawns.entrySet().iterator();

                    while (tornadoIt.hasNext()) {
                        Entry<UUID, ChargedDapHandler.FireDapScheduledEvent> entryx = tornadoIt.next();
                        ChargedDapHandler.FireDapScheduledEvent event = entryx.getValue();
                        if (now >= event.executeTime) {
                            spawnFireTornado(event.p1, event.p2);
                            tornadoIt.remove();
                        }
                    }

                    if (auraBeamsActive && server != null) {
                        long elapsed = now - auraBeamStartTime;
                        if (elapsed < 4000L) {
                            ServerPlayerEntity p1 = server.getPlayerManager().getPlayer(auraBeamPlayer1);
                            ServerPlayerEntity p2 = server.getPlayerManager().getPlayer(auraBeamPlayer2);
                            if (p1 != null && p2 != null) {
                                spawnAnimatedAuraBeam(p1, elapsed);
                                spawnAnimatedAuraBeam(p2, elapsed);
                            }
                        } else {
                            auraBeamsActive = false;
                        }
                    }

                    Iterator<Entry<UUID, Vec3d>> tpIt = smoothTPTarget.entrySet().iterator();

                    while (tpIt.hasNext()) {
                        Entry<UUID, Vec3d> entryx = tpIt.next();
                        UUID playerId = entryx.getKey();
                        Vec3d target = entryx.getValue();
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                        if (player == null) {
                            tpIt.remove();
                            smoothTPProgress.remove(playerId);
                        } else {
                            int progress = smoothTPProgress.getOrDefault(playerId, 0);
                            if (progress < 10) {
                                Vec3d current = player.getPos();
                                double t = (progress + 1) / 10.0;
                                Vec3d newPos = new Vec3d(
                                    current.x + (target.x - current.x) * t,
                                    current.y + (target.y - current.y) * t,
                                    current.z + (target.z - current.z) * t
                                );
                                UUID partnerIdx = fireDapPartner.get(playerId);
                                if (partnerIdx != null) {
                                    ServerPlayerEntity partnerx = server.getPlayerManager().getPlayer(partnerIdx);
                                    if (partnerx != null) {
                                        double dxx = partnerx.getPos().x - newPos.x;
                                        double dzx = partnerx.getPos().z - newPos.z;
                                        float yaw = (float)(Math.atan2(dzx, dxx) * 180.0 / Math.PI) - 90.0F;
                                        player.teleport(
                                            player.getServerWorld(), newPos.x, newPos.y, newPos.z, yaw, player.getPitch()
                                        );
                                    }
                                }

                                smoothTPProgress.put(playerId, progress + 1);
                            } else {
                                tpIt.remove();
                                smoothTPProgress.remove(playerId);
                            }
                        }
                    }

                    if (tornadoActive && tornadoCenter != null && tornadoWorld != null) {
                        long elapsed = now - tornadoStartTime;
                        if (elapsed < 5000L) {
                            if (elapsed % 40L == 0L) {
                                for (int i = 0; i < 12; i++) {
                                    double startAngle = i * 30;
                                    double startRadius = 3.0 + Math.random() * 2.0;
                                    ChargedDapHandler.TornadoSwirlEntity swirl = new ChargedDapHandler.TornadoSwirlEntity(
                                        tornadoWorld, tornadoCenter, startAngle, startRadius
                                    );
                                    activeTornadoSwirls.add(swirl);
                                }
                            }
                        } else {
                            tornadoActive = false;
                            activeTornadoSwirls.clear();
                        }

                        Iterator<ChargedDapHandler.TornadoSwirlEntity> swirlIt = activeTornadoSwirls.iterator();

                        while (swirlIt.hasNext()) {
                            ChargedDapHandler.TornadoSwirlEntity swirl = swirlIt.next();
                            boolean stillAlive = swirl.tick();
                            if (!stillAlive) {
                                swirlIt.remove();
                            }
                        }

                        if (tornadoCenter != null && tornadoWorld != null) {
                            double tornadoRadius = 30.0;
                            UUID shieldPlayer1 = auraBeamPlayer1;
                            UUID shieldPlayer2 = auraBeamPlayer2;
                            Box searchBox = new Box(
                                tornadoCenter.x - tornadoRadius,
                                tornadoCenter.y,
                                tornadoCenter.z - tornadoRadius,
                                tornadoCenter.x + tornadoRadius,
                                tornadoCenter.y + 70.0,
                                tornadoCenter.z + tornadoRadius
                            );

                            for (Entity entity : tornadoWorld.getOtherEntities(null, searchBox)) {
                                if (!entity.getUuid().equals(shieldPlayer1) && !entity.getUuid().equals(shieldPlayer2)) {
                                    Vec3d entityPos = entity.getPos();
                                    double dxx = entityPos.x - tornadoCenter.x;
                                    double dzx = entityPos.z - tornadoCenter.z;
                                    double distanceToCenter = Math.sqrt(dxx * dxx + dzx * dzx);
                                    if (distanceToCenter >= tornadoRadius - 2.0 && distanceToCenter <= tornadoRadius + 2.0) {
                                        Vec3d direction = new Vec3d(dxx, 0.0, dzx).normalize();
                                        entity.setVelocity(direction.x * 2.0, 0.5, direction.z * 2.0);
                                        entity.velocityModified = true;
                                        if (entity instanceof LivingEntity living) {
                                            living.damage(living.getDamageSources().magic(), 5.0F);
                                        }
                                    }

                                    if (entity instanceof ProjectileEntity && distanceToCenter >= tornadoRadius - 3.0) {
                                        entity.discard();
                                    }
                                }
                            }
                        }
                    }

                    Iterator<Entry<UUID, Long>> freezeIt = fireDapComboFreezeEnd.entrySet().iterator();

                    while (freezeIt.hasNext()) {
                        Entry<UUID, Long> entryx = freezeIt.next();
                        if (now >= entryx.getValue()) {
                            UUID playerId = entryx.getKey();

                            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                                ServerPlayNetworking.send(player, new ChargedDapHandler.FireDapFreezePayload(playerId, false));
                            }

                            freezeIt.remove();
                            fireDapPartner.remove(playerId);
                            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                            if (player != null) {
                                PoseNetworking.broadcastAnimState(player, 0);
                            }

                            if (!inFireDapHit.getOrDefault(playerId, false)) {
                                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                                    ServerPlayNetworking.send(p, new ChargedDapHandler.FireDapFirstPersonPayload(playerId, false));
                                }

                                releaseFireDapLeftovers(playerId);
                            }
                        }
                    }

                    Iterator<ChargedDapHandler.SaturnRing> ringIt = activeSaturnRings.iterator();

                    while (ringIt.hasNext()) {
                        ChargedDapHandler.SaturnRing ring = ringIt.next();
                        if (now >= ring.endTime) {
                            ringIt.remove();
                        } else {
                            long elapsedx = now - ring.startTime;
                            double rotationAngle = elapsedx / 100.0 * 360.0;
                            double radius = 50.0;
                            ServerWorld world = server.getOverworld();

                            for (double angle = 0.0; angle < 360.0; angle += 5.0) {
                                double rad = Math.toRadians(angle + rotationAngle);
                                double x = ring.center.x + Math.cos(rad) * radius;
                                double zx = ring.center.z + Math.sin(rad) * radius;
                                double y = ring.center.y;
                                world.spawnParticles(ParticleTypes.END_ROD, x, y, zx, 1, 0.1, 0.1, 0.1, 0.01);
                                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.5, zx, 1, 0.05, 0.05, 0.05, 0.0);
                            }
                        }
                    }

                    for (ServerPlayerEntity playerx : server.getPlayerManager().getPlayerList()) {
                        UUID id = playerx.getUuid();
                        if (impactFreezeTicks.containsKey(id)) {
                            int remaining = impactFreezeTicks.get(id);
                            if (remaining > 0) {
                                playerx.setVelocity(0.0, Math.min(0.0, playerx.getVelocity().y), 0.0);
                                playerx.velocityModified = true;
                                impactFreezeTicks.put(id, remaining - 1);
                            } else {
                                impactFreezeTicks.remove(id);
                            }
                        }

                        Vec3d velocity = getEffectiveVelocity(playerx);
                        double speed = velocity.length() * 20.0;
                        LinkedList<Double> history = speedHistory.computeIfAbsent(id, k -> new LinkedList<>());
                        history.addLast(speed);

                        while (history.size() > 40) {
                            history.removeFirst();
                        }

                        if (chargeStartTime.containsKey(id)) {
                            float charge = getChargePercent(id);
                            double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0;
                            if (charge >= 0.99F) {
                                boolean isMoving = horizontalSpeed >= minMovementSpeed();
                                if (isMoving) {
                                    fireGraceTime.remove(id);
                                    if (!CoopMovesConfig.get().enableFireDap) {
                                        fireStartTime.remove(id);
                                        fireLevel.put(id, 0.0F);
                                    } else {
                                        if (!fireStartTime.containsKey(id)) {
                                            fireStartTime.put(id, now);
                                        }

                                        long timeAtFullCharge = now - fireStartTime.get(id);
                                        if (timeAtFullCharge >= fireDelayMs()) {
                                            long fireBuildTime = timeAtFullCharge - fireDelayMs();
                                            float fire = Math.min(1.0F, (float)fireBuildTime / (float)fireBuildTimeMs());
                                            fireLevel.put(id, fire);
                                            spawnFireHandParticles(playerx, fire);
                                            if (fire >= 0.99F) {
                                                if (!fireMaxedStartTime.containsKey(id)) {
                                                    fireMaxedStartTime.put(id, now);
                                                }

                                                long timeAtMaxFire = now - fireMaxedStartTime.get(id);
                                                if (timeAtMaxFire >= heavenReadyTimeMs() && !heavenReady.contains(id)) {
                                                    heavenReady.add(id);
                                                    playerx.getServerWorld()
                                                        .playSound(
                                                            null,
                                                            playerx.getX(),
                                                            playerx.getY(),
                                                            playerx.getZ(),
                                                            SoundEvents.BLOCK_GLASS_BREAK,
                                                            SoundCategory.PLAYERS,
                                                            1.0F,
                                                            0.8F
                                                        );
                                                    playerx.sendMessage(Text.literal("§d§l✨ HEAVEN READY! ✨ §7(Fire UI broken!)"), true);
                                                    ChargedDapHandler.HeavenReadyPayload payload = new ChargedDapHandler.HeavenReadyPayload(id, true);

                                                    for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                                                        ServerPlayNetworking.send(p, payload);
                                                    }
                                                }
                                            } else {
                                                fireMaxedStartTime.remove(id);
                                            }
                                        } else {
                                            fireLevel.put(id, 0.0F);
                                        }
                                    }
                                } else {
                                    if (!fireGraceTime.containsKey(id)) {
                                        fireGraceTime.put(id, now);
                                    }

                                    long graceDuration = now - fireGraceTime.get(id);
                                    if (graceDuration > 500L) {
                                        fireStartTime.remove(id);
                                        fireLevel.put(id, 0.0F);
                                        fireMaxedStartTime.remove(id);
                                        if (heavenReady.remove(id)) {
                                            broadcastHeavenReadyStatus(server, id, false);
                                        }
                                    }
                                }
                            } else {
                                fireStartTime.remove(id);
                                fireGraceTime.remove(id);
                                fireLevel.put(id, 0.0F);
                                fireMaxedStartTime.remove(id);
                                if (heavenReady.remove(id)) {
                                    broadcastHeavenReadyStatus(server, id, false);
                                }
                            }
                        } else {
                            fireStartTime.remove(id);
                            fireGraceTime.remove(id);
                            fireLevel.remove(id);
                            fireMaxedStartTime.remove(id);
                            if (heavenReady.remove(id)) {
                                broadcastHeavenReadyStatus(server, id, false);
                            }
                        }
                    }

                    Iterator<Entry<UUID, UUID>> it = waitingForPartner.entrySet().iterator();

                    while (it.hasNext()) {
                        Entry<UUID, UUID> entryx = it.next();
                        UUID waiterId = entryx.getKey();
                        Long releaseT = releaseTime.get(waiterId);
                        if (releaseT != null && now - releaseT > releaseWindowMs()) {
                            ServerPlayerEntity waiter = server.getPlayerManager().getPlayer(waiterId);
                            ServerPlayerEntity partnerx = server.getPlayerManager().getPlayer(entryx.getValue());
                            if (waiter != null && partnerx != null) {
                                executeFizzle(waiter, partnerx);
                            }

                            if (waiter != null) {
                                cooldowns.put(waiterId, now + whiffCooldownMs());
                                ServerPlayNetworking.send(waiter, new ChargedDapHandler.WhiffCooldownPayload(whiffCooldownMs()));
                            }

                            it.remove();
                            releaseTime.remove(waiterId);
                            chargeStartTime.remove(waiterId);
                            chargeStartTime.remove(entryx.getValue());
                            broadcastChargeCancel(waiter);
                            if (partnerx != null) {
                                broadcastChargeCancel(partnerx);
                            }
                        }
                    }

                    for (ServerPlayerEntity playerx : server.getPlayerManager().getPlayerList()) {
                        if (chargeStartTime.containsKey(playerx.getUuid())) {
                            float charge = getChargePercent(playerx.getUuid());
                            float fire = fireLevel.getOrDefault(playerx.getUuid(), 0.0F);
                            ChargedDapHandler.ChargeSyncPayload syncPayload = new ChargedDapHandler.ChargeSyncPayload(playerx.getUuid(), charge, fire, true);

                            for (ServerPlayerEntity other : PlayerLookup.tracking(playerx)) {
                                ServerPlayNetworking.send(other, syncPayload);
                            }

                            ServerPlayNetworking.send(playerx, syncPayload);
                        }
                    }
                }
            );
    }

    private static void spawnFireHandParticles(ServerPlayerEntity player, float fireLevel) {
        if (!(Math.random() > 0.33)) {
            ServerWorld world = player.getServerWorld();
            Vec3d pos = player.getPos();
            float yaw = player.getYaw();
            double yawRad = Math.toRadians(yaw);
            double rightX = -Math.cos(yawRad) * 0.4;
            double rightZ = -Math.sin(yawRad) * 0.4;
            double handX = pos.x + rightX;
            double handY = pos.y + 1.3;
            double handZ = pos.z + rightZ;
            world.spawnParticles(ParticleTypes.FLAME, handX, handY, handZ, 1, 0.06, 0.06, 0.06, 0.005);
            if (fireLevel > 0.6F) {
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, handX, handY, handZ, 1, 0.05, 0.05, 0.05, 0.003);
            }
        }
    }

    private static void onChargeStart(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (isInFireDapBlockingState(uuid) || fireComboQTEs.containsKey(uuid)) {
            broadcastChargeCancel(player);
        } else if (HighfiveDapHandler.isInHighfiveDap(uuid)) {
            broadcastChargeCancel(player);
        } else if (HighFiveHandler.hasHandRaised(uuid)) {
            broadcastChargeCancel(player);
        } else if (HighFiveHandler.isInBlockingAnimation(uuid)) {
            broadcastChargeCancel(player);
        } else if (isInComboCooldown(uuid)) {
            player.sendMessage(Text.literal("§cWait 1 second after combo!"), true);
            broadcastChargeCancel(player);
        } else if (!FallCatchHandler.isInCatchReadyMode(uuid)) {
            if (!isOnCooldown(uuid)) {
                if (player.getMainHandStack().isEmpty()) {
                    chargeStartTime.put(uuid, System.currentTimeMillis());
                    fireLevel.put(uuid, 0.0F);
                    ChargedDapHandler.ChargeSyncPayload payload = new ChargedDapHandler.ChargeSyncPayload(uuid, 0.0F, 0.0F, true);
                    ServerPlayNetworking.send(player, payload);

                    for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
                        ServerPlayNetworking.send(other, payload);
                    }

                    player.getServerWorld()
                        .playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                            SoundCategory.PLAYERS,
                            0.5F,
                            0.8F
                        );
                }
            }
        }
    }

    private static void onChargeRelease(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (chargeStartTime.containsKey(uuid)) {
            long now = System.currentTimeMillis();
            float myCharge = getChargePercent(uuid);
            float myFire = fireLevel.getOrDefault(uuid, 0.0F);
            if (myCharge >= 0.95F) {
                boolean partnerAlsoCharging = chargeStartTime.keySet()
                    .stream()
                    .filter(uid -> !uid.equals(uuid))
                    .anyMatch(
                        uid -> {
                            ServerPlayerEntity nearby = player.getServerWorld()
                                .getPlayers()
                                .stream()
                                .filter(p -> p.getUuid().equals(uid) && player.distanceTo(p) < 2.5F)
                                .findFirst()
                                .orElse(null);
                            return nearby != null;
                        }
                    );
                long chargeDurationMs = now - chargeStartTime.getOrDefault(uuid, now);
                if (!partnerAlsoCharging && SlapHandler.checkSlapOnRelease(player, chargeDurationMs)) {
                    chargeStartTime.remove(uuid);
                    fireLevel.remove(uuid);
                    fireStartTime.remove(uuid);
                    if (heavenReady.remove(uuid)) {
                        broadcastHeavenReadyStatus(player.getServer(), uuid, false);
                    }

                    broadcastChargeCancel(player);
                    return;
                }
            }

            List<ServerPlayerEntity> allPartners = findAllDapPartners(player);
            if (allPartners.size() >= 2) {
                ServerPlayerEntity tp2 = allPartners.get(0);
                ServerPlayerEntity tp3 = allPartners.get(1);
                Long t2 = chargeStartTime.get(tp2.getUuid());
                Long t3 = chargeStartTime.get(tp3.getUuid());
                if (t2 != null && t3 != null) {
                    long maxDiff = Math.max(Math.abs(now - t2), Math.max(Math.abs(now - t3), Math.abs(t2 - t3)));
                    if (maxDiff <= releaseWindowMs() * 2L) {
                        executeTripleDap(player, tp2, tp3);

                        for (ServerPlayerEntity tp : new ServerPlayerEntity[]{player, tp2, tp3}) {
                            UUID tid = tp.getUuid();
                            chargeStartTime.remove(tid);
                            fireLevel.remove(tid);
                            fireStartTime.remove(tid);
                            if (heavenReady.remove(tid)) {
                                broadcastHeavenReadyStatus(tp.getServer(), tid, false);
                            }

                            broadcastChargeCancel(tp);
                        }

                        return;
                    }
                }
            }

            ServerPlayerEntity partner = findAnyDapPartner(player);
            if (partner != null || !DapRunHandler.justFired(player.getUuid())) {
                if (partner == null) {
                    executeWhiff(player);
                    chargeStartTime.remove(uuid);
                    fireLevel.remove(uuid);
                    fireStartTime.remove(uuid);
                    if (heavenReady.remove(uuid)) {
                        broadcastHeavenReadyStatus(player.getServer(), uuid, false);
                    }

                    broadcastChargeCancel(player);
                    long cooldownEnd = now + whiffCooldownMs();
                    cooldowns.put(uuid, cooldownEnd);
                    broadcastWhiffCooldown(player, cooldownEnd);
                    player.sendMessage(Text.literal("§c✗ Whiff! 0.8s cooldown"), true);
                } else {
                    UUID partnerId = partner.getUuid();
                    if (HighFiveHandler.hasHandRaised(partnerId) && !chargeStartTime.containsKey(partnerId) && HighfiveDapHandler.tryStart(player, partner)) {
                        chargeStartTime.remove(uuid);
                        fireLevel.remove(uuid);
                        fireStartTime.remove(uuid);
                        if (heavenReady.remove(uuid)) {
                            broadcastHeavenReadyStatus(player.getServer(), uuid, false);
                        }

                        broadcastChargeCancel(player);
                    } else {
                        if (waitingForPartner.containsKey(partnerId) && waitingForPartner.get(partnerId).equals(uuid)) {
                            long partnerReleaseTime = releaseTime.get(partnerId);
                            float partnerCharge = getChargePercent(partnerId);
                            float partnerFire = fireLevel.getOrDefault(partnerId, 0.0F);
                            executeDap(player, partner, myCharge, partnerCharge, myFire, partnerFire, 1.0F, partnerReleaseTime, now);
                            waitingForPartner.remove(partnerId);
                            releaseTime.remove(partnerId);
                            chargeStartTime.remove(uuid);
                            chargeStartTime.remove(partnerId);
                            fireLevel.remove(uuid);
                            fireLevel.remove(partnerId);
                            fireStartTime.remove(uuid);
                            fireStartTime.remove(partnerId);
                            if (heavenReady.remove(uuid)) {
                                broadcastHeavenReadyStatus(player.getServer(), uuid, false);
                            }

                            if (heavenReady.remove(partnerId)) {
                                broadcastHeavenReadyStatus(partner.getServer(), partnerId, false);
                            }

                            broadcastChargeCancel(player);
                            broadcastChargeCancel(partner);
                        } else {
                            releaseTime.put(uuid, now);
                            waitingForPartner.put(uuid, partnerId);
                            player.getServerWorld()
                                .playSound(
                                    null,
                                    player.getX(),
                                    player.getY(),
                                    player.getZ(),
                                    (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
                                    SoundCategory.PLAYERS,
                                    0.7F,
                                    1.2F
                                );
                        }
                    }
                }
            }
        }
    }

    private static Vec3d dapReachPoint(ServerPlayerEntity player) {
        Vec3d fwd = HighFiveHandler.horizontalForward(player);
        Vec3d pos = player.getPos();
        return new Vec3d(pos.x + fwd.x * 0.7, pos.y + 1.3, pos.z + fwd.z * 0.7);
    }

    private static boolean dapReachPointsOverlap(ServerPlayerEntity a, ServerPlayerEntity b) {
        Vec3d ra = dapReachPoint(a);
        Vec3d rb = dapReachPoint(b);
        return Math.hypot(ra.x - rb.x, ra.z - rb.z) <= 0.7;
    }

    private static ServerPlayerEntity findAnyDapPartner(ServerPlayerEntity player) {
        Box searchBox = player.getBoundingBox().expand(dapRange());

        for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
            if (other != player
                && !isOnCooldown(other.getUuid())
                && !BullyDapHandler.isPrimed(other.getUuid())
                && !BullyDapHandler.isInBullyDap(other.getUuid())) {
                boolean isReady = chargeStartTime.containsKey(other.getUuid()) || HighFiveHandler.hasHandRaised(other.getUuid());
                if (isReady && searchBox.intersects(other.getBoundingBox())) {
                    if (heavenRecMode || dapReachPointsOverlap(player, other)) {
                        return other;
                    }

                    if (DapRunHandler.tryDapRun(player, other)) {
                        return null;
                    }
                }
            }
        }

        return null;
    }

    private static List<ServerPlayerEntity> findAllDapPartners(ServerPlayerEntity player) {
        Box searchBox = player.getBoundingBox().expand(1.6F);
        List<ServerPlayerEntity> result = new ArrayList<>();

        for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
            if (other != player
                && !isOnCooldown(other.getUuid())
                && chargeStartTime.containsKey(other.getUuid())
                && !BullyDapHandler.isPrimed(other.getUuid())
                && !BullyDapHandler.isInBullyDap(other.getUuid())) {
                if (searchBox.intersects(other.getBoundingBox())) {
                    result.add(other);
                }

                if (result.size() >= 2) {
                    break;
                }
            }
        }

        return result;
    }

    private static void executeTripleDap(ServerPlayerEntity p1, ServerPlayerEntity p2, ServerPlayerEntity p3) {
        ServerWorld world = p1.getServerWorld();
        long now = System.currentTimeMillis();
        Vec3d center = p1.getPos().add(p2.getPos()).add(p3.getPos()).multiply(0.3333333333333333);
        ArmorStandEntity stand = new ArmorStandEntity(world, center.x, center.y, center.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        world.spawnEntity(stand);
        double radius = 0.7;
        ServerPlayerEntity[] trio = new ServerPlayerEntity[]{p1, p2, p3};

        for (int i = 0; i < 3; i++) {
            double angle = (Math.PI * 2) * i / 3.0;
            double px = center.x + radius * Math.cos(angle);
            double pz = center.z + radius * Math.sin(angle);
            float yaw = (float)(-Math.toDegrees(Math.atan2(center.x - px, center.z - pz)));
            trio[i].teleport(world, px, p1.getY(), pz, Set.of(), yaw, 0.0F);
            trio[i].setYaw(yaw);
            trio[i].setBodyYaw(yaw);
            trio[i].setHeadYaw(yaw);
            trio[i].prevBodyYaw = yaw;
            trio[i].swingHand(Hand.MAIN_HAND);
        }

        stand.discard();

        for (ServerPlayerEntity p : trio) {
            cooldowns.put(p.getUuid(), now + cooldownMs());
        }

        for (ServerPlayerEntity p : trio) {
            PoseNetworking.broadcastAnimState(p, CoopAnimationHandler.AnimState.DAP_HIT.ordinal());
        }

        Vec3d cTop = center.add(0.0, 1.4, 0.0);
        world.spawnParticles(ParticleTypes.END_ROD, cTop.x, cTop.y, cTop.z, 40, 0.4, 0.4, 0.4, 0.15);
        world.spawnParticles(ParticleTypes.FLASH, cTop.x, cTop.y, cTop.z, 3, 0.1, 0.1, 0.1, 0.0);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, cTop.x, cTop.y, cTop.z, 15, 0.5, 0.5, 0.5, 0.2);
        world.playSound(null, cTop.x, cTop.y, cTop.z, ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 1.3F, 1.1F);
        Text msg = Text.literal("§6§l⚡ TRIPLE DAP!");

        for (ServerPlayerEntity p : trio) {
            p.sendMessage(msg, true);
        }
    }

    private static void executeDap(
        ServerPlayerEntity p1, ServerPlayerEntity p2, float charge1, float charge2, float fire1, float fire2, float syncQuality, long releaseTime1, long releaseTime2
    ) {
        if (!DapHoldHandler.isInDapHold(p1.getUuid()) && !DapHoldHandler.isInDapHold(p2.getUuid())) {
            long now = System.currentTimeMillis();
            cooldowns.remove(p1.getUuid());
            cooldowns.remove(p2.getUuid());
            float avgCharge = (charge1 + charge2) / 2.0F;
            float avgFire = (fire1 + fire2) / 2.0F;
            double speed1 = getMaxRecentSpeed(p1.getUuid());
            double speed2 = getMaxRecentSpeed(p2.getUuid());
            double combinedSpeed = speed1 + speed2;
            long timeDiff = Math.abs(releaseTime1 - releaseTime2);
            boolean perfectHit = timeDiff <= perfectWindowMs();
            boolean bothCharging = chargeStartTime.containsKey(p1.getUuid()) && chargeStartTime.containsKey(p2.getUuid());
            int tier = calculateTier(avgCharge, combinedSpeed, avgFire, fire1, fire2);
            if (tier < 3 || !bothCharging || perfectHit || timeDiff <= releaseWindowMs()) {
                boolean isPerfectDap = tier >= 3 && bothCharging && perfectHit;
                boolean isHighTierDap = tier >= 4;
                if (!isPerfectDap && !isHighTierDap && !arePlayersFacingEachOther(p1, p2)) {
                    p1.sendMessage(Text.literal("§c§lKeep eye contact!"), true);
                    p2.sendMessage(Text.literal("§c§lKeep eye contact!"), true);
                    cooldowns.remove(p1.getUuid());
                    cooldowns.remove(p2.getUuid());
                    broadcastChargeCancel(p1);
                    broadcastChargeCancel(p2);
                    PoseNetworking.broadcastAnimState(p1, 0);
                    PoseNetworking.broadcastAnimState(p2, 0);
                } else {
                    Vec3d pos1 = p1.getPos();
                    Vec3d pos2 = p2.getPos();
                    Vec3d dapPos = pos1.add(pos2).multiply(0.5).add(0.0, 0.5, 0.0);
                    ServerWorld world = p1.getServerWorld();
                    if (NormalFacingDapHandler.isConfirmed(p1.getUuid(), p2.getUuid())) {
                        NormalFacingDapHandler.clearConfirm(p1.getUuid(), p2.getUuid());
                        NormalFacingDapHandler.start(p1, p2);
                    } else {
                        switch (tier) {
                            case 0:
                                executeTier0(world, dapPos, p1, p2);
                                break;
                            case 1:
                                executeTier1(world, dapPos, p1, p2);
                                break;
                            case 2:
                                executeTier2(world, dapPos, p1, p2);
                                break;
                            case 3:
                                executeTier3Great(world, dapPos, p1, p2, perfectHit, bothCharging, timeDiff);
                                break;
                            case 4:
                                executeTier4Legendary(world, dapPos, p1, p2, perfectHit, bothCharging, speed1, speed2);
                                break;
                            case 5:
                                executeTier5FireDap(world, dapPos, p1, p2, perfectHit);
                        }

                        p1.swingHand(Hand.MAIN_HAND, true);
                        p2.swingHand(Hand.MAIN_HAND, true);
                        MahitoTrollHandler.checkForMahitoTroll(p1, p2);
                        speedHistory.remove(p1.getUuid());
                        speedHistory.remove(p2.getUuid());
                        chargeStartTime.remove(p1.getUuid());
                        chargeStartTime.remove(p2.getUuid());
                        broadcastChargeCancel(p1);
                        broadcastChargeCancel(p2);
                        boolean facingActive = FacingDapHandler.isActive(p1.getUuid()) || FacingDapHandler.isActive(p2.getUuid());
                        if (!facingActive) {
                            if (highFivePartners.contains(p1.getUuid())) {
                                PoseNetworking.broadcastAnimState(p1, CoopAnimationHandler.AnimState.HIGHFIVE_HIT.ordinal());
                                PoseNetworking.broadcastAnimState(p2, CoopAnimationHandler.AnimState.DAP_HIT.ordinal());
                                comboCooldown.put(p1.getUuid(), now + 1000L);
                                comboCooldown.put(p2.getUuid(), now + 1000L);
                            } else if (highFivePartners.contains(p2.getUuid())) {
                                PoseNetworking.broadcastAnimState(p2, CoopAnimationHandler.AnimState.HIGHFIVE_HIT.ordinal());
                                PoseNetworking.broadcastAnimState(p1, CoopAnimationHandler.AnimState.DAP_HIT.ordinal());
                                comboCooldown.put(p1.getUuid(), now + 1000L);
                                comboCooldown.put(p2.getUuid(), now + 1000L);
                            } else {
                                int animOrd = tier <= 2
                                    ? CoopAnimationHandler.AnimState.DAP_HIT_BAD.ordinal()
                                    : CoopAnimationHandler.AnimState.DAP_HIT.ordinal();
                                PoseNetworking.broadcastAnimState(p1, animOrd);
                                PoseNetworking.broadcastAnimState(p2, animOrd);
                            }

                            DapPositioning.closeIn(p1, p2);
                            DapPositioning.faceEachOther(p1, p2);
                            DapPositioning.openReleaseWindow(p1, p2, false);
                        }

                        long particleSpawnTime = System.currentTimeMillis() + 250L;
                        scheduledParticles.add(
                            new ChargedDapHandler.ScheduledParticles(world, dapPos.x, dapPos.y, dapPos.z, particleSpawnTime)
                        );
                        ChargedDapHandler.DapResultPayload result = new ChargedDapHandler.DapResultPayload(
                            dapPos.x, dapPos.y, dapPos.z, p1.getUuid(), p2.getUuid(), tier, perfectHit
                        );

                        for (ServerPlayerEntity other : PlayerLookup.all(p1.getServer())) {
                            ServerPlayNetworking.send(other, result);
                        }
                    }
                }
            }
        }
    }

    private static int calculateTier(float avgCharge, double combinedSpeed, float avgFire, float fire1, float fire2) {
        if (fire1 >= 0.9F && fire2 >= 0.9F) {
            return 5;
        } else if (avgCharge >= 0.8F && combinedSpeed >= 25.0) {
            return 4;
        } else {
            float chargeScore = avgCharge * 100.0F;
            float speedBonus = 0.0F;
            if (combinedSpeed >= 8.0) {
                speedBonus = (float)((combinedSpeed - 8.0) / 17.0 * 30.0);
            }

            float finalScore = chargeScore + speedBonus;
            if (finalScore >= 100.0F) {
                return 3;
            } else if (finalScore >= 70.0F) {
                return 2;
            } else {
                return finalScore >= 40.0F ? 1 : 0;
            }
        }
    }

    private static void executeFizzle(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        ServerWorld world = p1.getServerWorld();
        Vec3d pos = p1.getPos().add(p2.getPos()).multiply(0.5).add(0.0, 1.4, 0.0);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.5F);
        world.playSound(
            null, pos.x, pos.y, pos.z, (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.8F, 0.5F
        );
        world.spawnParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 12, 0.4, 0.3, 0.4, 0.03);
        world.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 8, 0.3, 0.3, 0.3, 0.02);
        p1.sendMessage(Text.literal("§7*missed!* timing off..."), true);
        p2.sendMessage(Text.literal("§7*missed!* timing off..."), true);
        chargeStartTime.remove(p1.getUuid());
        chargeStartTime.remove(p2.getUuid());
        fireLevel.remove(p1.getUuid());
        fireLevel.remove(p2.getUuid());
        if (heavenReady.remove(p1.getUuid())) {
            broadcastHeavenReadyStatus(p1.getServer(), p1.getUuid(), false);
        }

        if (heavenReady.remove(p2.getUuid())) {
            broadcastHeavenReadyStatus(p2.getServer(), p2.getUuid(), false);
        }

        broadcastChargeCancel(p1);
        broadcastChargeCancel(p2);
        PoseNetworking.broadcastAnimState(p1, 0);
        PoseNetworking.broadcastAnimState(p2, 0);
        UUID p1Id = p1.getUuid();
        UUID p2Id = p2.getUuid();

        for (ServerPlayerEntity p : p1.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, new ChargedDapHandler.FireDapFirstPersonPayload(p1Id, false));
            ServerPlayNetworking.send(p, new ChargedDapHandler.FireDapFirstPersonPayload(p2Id, false));
        }

        long now = System.currentTimeMillis();
        cooldowns.put(p1.getUuid(), now + 500L);
        cooldowns.put(p2.getUuid(), now + 500L);
    }

    public static void executeWhiff(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d pos = player.getPos().add(0.0, 1.4, 0.0);
        Vec3d look = player.getRotationVector();
        pos = pos.add(look.multiply(0.5));
        if (Math.random() < 0.1) {
            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.DAP_MISS, SoundCategory.PLAYERS, 1.0F, 1.0F);
        } else {
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.6F);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_SAND_BREAK, SoundCategory.PLAYERS, 0.5F, 1.5F);
        }

        world.spawnParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 8, 0.2, 0.2, 0.2, 0.02);
        world.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 5, 0.15, 0.15, 0.15, 0.01);
        setBlockingAnimation(player.getUuid(), 330L);
        PoseNetworking.broadcastAnimState(player, 0);
        UUID playerId = player.getUuid();

        for (ServerPlayerEntity p : player.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, new ChargedDapHandler.FireDapFirstPersonPayload(playerId, false));
        }

        player.sendMessage(Text.literal("§7*whoosh*"), true);
    }

    private static void executeTier0(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        DapSession session = DapSessionManager.createSession(id1, id2, 1.4, DapSession.DapType.NORMAL_DAP);
        if (session == null) {
            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
            spawnPrecisionDapParticles(world, pos, 0);
            p1.sendMessage(Text.literal("§7Weak dap..."), true);
            p2.sendMessage(Text.literal("§7Weak dap..."), true);
        } else {
            session.onComplete(
                () -> {
                    new Thread(
                            () -> {
                                try {
                                    Thread.sleep(710L);
                                } catch (InterruptedException var4x) {
                                }

                                world.getServer()
                                    .execute(
                                        () -> {
                                            Vec3d midpoint = p1.getPos().add(p2.getPos()).multiply(0.5).add(0.0, 1.3, 0.0);
                                            world.playSound(
                                                null,
                                                midpoint.x,
                                                midpoint.y,
                                                midpoint.z,
                                                SoundEvents.ENTITY_PLAYER_ATTACK_WEAK,
                                                SoundCategory.PLAYERS,
                                                0.9F,
                                                1.1F
                                            );
                                            spawnPrecisionDapParticles(world, midpoint, 0);
                                        }
                                    );
                            }
                        )
                        .start();
                    new Thread(() -> {
                        try {
                            Thread.sleep(1667L);
                        } catch (InterruptedException var4x) {
                        }

                        world.getServer().execute(() -> {
                            PoseNetworking.broadcastAnimState(p1, 0);
                            PoseNetworking.broadcastAnimState(p2, 0);
                        });
                    }).start();
                    applyKnockback(p1, p2, pos, 0.1);
                }
            );
        }
    }

    private static void executeTier1(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        DapSession session = DapSessionManager.createSession(id1, id2, 1.4, DapSession.DapType.NORMAL_DAP);
        if (session == null) {
            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
            spawnPrecisionDapParticles(world, pos, 1);
            p1.sendMessage(Text.literal("§e✋ Decent Dap!"), true);
            p2.sendMessage(Text.literal("§e✋ Decent Dap!"), true);
        } else {
            session.onComplete(
                () -> {
                    new Thread(
                            () -> {
                                try {
                                    Thread.sleep(710L);
                                } catch (InterruptedException var4x) {
                                }

                                world.getServer()
                                    .execute(
                                        () -> {
                                            Vec3d midpoint = p1.getPos().add(p2.getPos()).multiply(0.5).add(0.0, 1.3, 0.0);
                                            world.playSound(
                                                null,
                                                midpoint.x,
                                                midpoint.y,
                                                midpoint.z,
                                                SoundEvents.ENTITY_PLAYER_ATTACK_WEAK,
                                                SoundCategory.PLAYERS,
                                                0.9F,
                                                1.1F
                                            );
                                            spawnPrecisionDapParticles(world, midpoint, 1);
                                        }
                                    );
                            }
                        )
                        .start();
                    new Thread(() -> {
                        try {
                            Thread.sleep(1667L);
                        } catch (InterruptedException var4x) {
                        }

                        world.getServer().execute(() -> {
                            PoseNetworking.broadcastAnimState(p1, 0);
                            PoseNetworking.broadcastAnimState(p2, 0);
                        });
                    }).start();
                    applyKnockback(p1, p2, pos, 0.3);
                }
            );
        }
    }

    private static void executeTier2(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        DapSession session = DapSessionManager.createSession(id1, id2, 1.4, DapSession.DapType.NORMAL_DAP);
        if (session == null) {
            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
            spawnPrecisionDapParticles(world, pos, 2);
            p1.sendMessage(Text.literal("§a✋ Good Dap! ✋"), true);
            p2.sendMessage(Text.literal("§a✋ Good Dap! ✋"), true);
        } else {
            session.onComplete(
                () -> {
                    new Thread(
                            () -> {
                                try {
                                    Thread.sleep(710L);
                                } catch (InterruptedException var4x) {
                                }

                                world.getServer()
                                    .execute(
                                        () -> {
                                            Vec3d midpoint = p1.getPos().add(p2.getPos()).multiply(0.5).add(0.0, 1.3, 0.0);
                                            world.playSound(
                                                null,
                                                midpoint.x,
                                                midpoint.y,
                                                midpoint.z,
                                                SoundEvents.ENTITY_PLAYER_ATTACK_WEAK,
                                                SoundCategory.PLAYERS,
                                                0.9F,
                                                1.1F
                                            );
                                            spawnPrecisionDapParticles(world, midpoint, 2);
                                        }
                                    );
                            }
                        )
                        .start();
                    new Thread(() -> {
                        try {
                            Thread.sleep(1667L);
                        } catch (InterruptedException var4x) {
                        }

                        world.getServer().execute(() -> {
                            PoseNetworking.broadcastAnimState(p1, 0);
                            PoseNetworking.broadcastAnimState(p2, 0);
                        });
                    }).start();
                    applyKnockback(p1, p2, pos, 0.6);
                }
            );
        }
    }

    private static void executeTier3Great(
        ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2, boolean perfectHit, boolean bothCharging, long timeDiff
    ) {
        if (bothCharging && !perfectHit && timeDiff <= 200L) {
            executeTier3Mid(world, pos, p1, p2);
        } else {
            if (bothCharging && perfectHit) {
                rotateBothPlayersToFaceEachOther(p1, p2);
                if (FacingDapHandler.areFacingEachOther(p1, p2) && !FacingDapHandler.isActive(p1.getUuid())) {
                    FacingDapHandler.start(p1, p2);
                    return;
                }

                long now = System.currentTimeMillis();
                UUID id1 = p1.getUuid();
                UUID id2 = p2.getUuid();
                DapSession session = DapSessionManager.createSession(id1, id2, 1.5, DapSession.DapType.PERFECT_DAP);
                if (session == null) {
                    executeTier3Normal(world, pos, p1, p2);
                    return;
                }

                perfectDapStartTime.put(id1, now);
                perfectDapStartTime.put(id2, now);
                perfectDapPartner.put(id1, id2);
                perfectDapPartner.put(id2, id1);
                session.onComplete(() -> startPerfectDapTier3Animation(world, pos, p1, p2));
            } else {
                executeTier3Normal(world, pos, p1, p2);
            }
        }
    }

    private static void startPerfectDapTier3Animation(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        long now = System.currentTimeMillis();
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        DapPositioning.faceEachOther(p1, p2);
        DapPositioning.closeIn(p1, p2);
        DapPositioning.openReleaseWindow(p1, p2, true);
        PactHandler.onHandshakeCounted(p1, p2);
        DapFlair.open(world, pos, p1, p2, 0L, 3, false, true);
        p1.swingHand(Hand.MAIN_HAND);
        p2.swingHand(Hand.MAIN_HAND);
        Vec3d p1Hand = p1.getPos().add(0.0, 1.4, 0.0);
        Vec3d p2Hand = p2.getPos().add(0.0, 1.4, 0.0);
        Vec3d handMid = p1Hand.add(p2Hand).multiply(0.5);
        PoseNetworking.broadcastAnimState(p1, CoopAnimationHandler.AnimState.PERFECT_DAP_HIT.ordinal());
        PoseNetworking.broadcastAnimState(p2, CoopAnimationHandler.AnimState.PERFECT_DAP_HIT.ordinal());
        perfectDapFreezeEnd.put(id1, now + 1210L);
        perfectDapFreezeEnd.put(id2, now + 1210L);
        ServerPlayNetworking.send(p1, new ChargedDapHandler.PerfectDapFreezePayload(true));
        ServerPlayNetworking.send(p2, new ChargedDapHandler.PerfectDapFreezePayload(true));
        setBlockingAnimation(id1, 1210L);
        setBlockingAnimation(id2, 1210L);
        long effectTime = now + 150L;
        scheduledPerfectDapEffects.add(new ChargedDapHandler.ScheduledPerfectDapEffect(world, pos, p1, p2, effectTime));
        p1.sendMessage(Text.literal("§6§l✋ PERFECT GREAT DAP! ✋"), true);
        p2.sendMessage(Text.literal("§6§l✋ PERFECT GREAT DAP! ✋"), true);
        DapHearts.perfect(p1);
        DapHearts.perfect(p2);
        DuoPoseHandler.onDapEnded(p1, p2);
        DapSessionManager.removeSession(id1);
    }

    private static void executeTier3Normal(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();
        DapSession session = DapSessionManager.createSession(id1, id2, 1.4, DapSession.DapType.NORMAL_DAP);
        if (session == null) {
            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
            spawnPrecisionDapParticles(world, pos, 3);
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
            createExplosion(world, pos, p1, p2, 3.5, 6.0F);
            applyKnockback(p1, p2, pos, 1.0);
            p1.sendMessage(Text.literal("§6§l✋ GREAT DAP! ✋"), true);
            p2.sendMessage(Text.literal("§6§l✋ GREAT DAP! ✋"), true);
            DapHearts.great(p1);
            DapHearts.great(p2);
        } else {
            session.onComplete(() -> {
                PactHandler.onHandshakeCounted(p1, p2);
                DapFlair.open(world, pos, p1, p2, 0L, 3, false);
                world.playSound(null, pos.x, pos.y, pos.z, ModSounds.DAP_WEAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
                spawnPrecisionDapParticles(world, pos, 3);
                world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
                createExplosion(world, pos, p1, p2, 3.5, 6.0F);
                applyKnockback(p1, p2, pos, 1.0);
                Object[] gflav = DapFlair.flavour(new Random(), false);
                String glabel = (String)gflav[0];
                p1.sendMessage(Text.literal(glabel), true);
                p2.sendMessage(Text.literal(glabel), true);
                DapHearts.great(p1);
                DapHearts.great(p2);
                if ((Boolean)gflav[1]) {
                    DapFlair.playRareEffect(world, pos, glabel);
                }

                if (CoopMovesConfig.get().enableDapCombo) {
                    DapComboChain.startCombo(p1, p2, pos);
                }

                DuoPoseHandler.onDapEnded(p1, p2);
            });
        }
    }

    private static void executeTier3Mid(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        DapSession session = DapSessionManager.createSession(p1.getUuid(), p2.getUuid(), 1.45, DapSession.DapType.NORMAL_DAP);
        Runnable effects = () -> {
            PactHandler.onHandshakeCounted(p1, p2);
            DapFlair.open(world, pos, p1, p2, 210L, 3, true);
            PoseNetworking.broadcastAnimState(p1, 128);
            PoseNetworking.broadcastAnimState(p2, 128);
            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.CLEAN_DAP, SoundCategory.PLAYERS, 1.0F, 1.0F);
            spawnPrecisionDapParticles(world, pos, 5);
            world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 22, 0.35, 0.35, 0.35, 0.14);
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y, pos.z, 16, 0.3, 0.3, 0.3, 0.1);
            world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 10, 0.25, 0.25, 0.25, 0.05);
            MinecraftServer shakeServer = p1.getServer();
            if (shakeServer != null) {
                UUID sId1 = p1.getUuid();
                UUID sId2 = p2.getUuid();
                new Thread(() -> {
                    try {
                        Thread.sleep(210L);
                    } catch (InterruptedException var6x) {
                        return;
                    }

                    shakeServer.execute(() -> {
                        world.playSound(null, pos.x, pos.y, pos.z, ModSounds.CLEAN_DAP_2, SoundCategory.PLAYERS, 1.0F, 1.0F);
                        DapFlair.FlairShakePayload midShake = new DapFlair.FlairShakePayload(1.05F, 490);
                        ServerPlayerEntity a = shakeServer.getPlayerManager().getPlayer(sId1);
                        ServerPlayerEntity b = shakeServer.getPlayerManager().getPlayer(sId2);
                        if (a != null) {
                            ServerPlayNetworking.send(a, midShake);
                        }

                        if (b != null) {
                            ServerPlayNetworking.send(b, midShake);
                        }
                    });
                }).start();
            }

            createExplosion(world, pos, p1, p2, 3.5, 6.0F);
            applyKnockback(p1, p2, pos, 1.1);
            Object[] flav = DapFlair.flavour(new Random(), true);
            String label = (String)flav[0];
            boolean rare = (Boolean)flav[1];
            p1.sendMessage(Text.literal(label), true);
            p2.sendMessage(Text.literal(label), true);
            if (rare) {
                DapFlair.playRareEffect(world, pos, label);
            }

            long midNow = System.currentTimeMillis();
            UUID midId1 = p1.getUuid();
            UUID midId2 = p2.getUuid();
            perfectDapFreezeEnd.put(midId1, midNow + 880L);
            perfectDapFreezeEnd.put(midId2, midNow + 880L);
            ServerPlayNetworking.send(p1, new ChargedDapHandler.PerfectDapFreezePayload(true));
            ServerPlayNetworking.send(p2, new ChargedDapHandler.PerfectDapFreezePayload(true));
            setBlockingAnimation(midId1, 880L);
            setBlockingAnimation(midId2, 880L);
            MinecraftServer midServer = p1.getServer();
            if (midServer != null) {
                new Thread(() -> {
                    try {
                        Thread.sleep(880L);
                    } catch (InterruptedException var4x) {
                    }

                    midServer.execute(() -> {
                        perfectDapFreezeEnd.remove(midId1);
                        perfectDapFreezeEnd.remove(midId2);
                        ServerPlayerEntity a = midServer.getPlayerManager().getPlayer(midId1);
                        ServerPlayerEntity b = midServer.getPlayerManager().getPlayer(midId2);
                        if (a != null) {
                            ServerPlayNetworking.send(a, new ChargedDapHandler.PerfectDapFreezePayload(false));
                        }

                        if (b != null) {
                            ServerPlayNetworking.send(b, new ChargedDapHandler.PerfectDapFreezePayload(false));
                        }
                    });
                }).start();
            }

            DuoPoseHandler.onDapEnded(p1, p2);
        };
        if (session == null) {
            effects.run();
        } else {
            session.onComplete(effects);
        }
    }

    private static void startStage2Extender(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        ServerWorld world = p1.getServerWorld();
        long now = System.currentTimeMillis();
        ServerPlayNetworking.send(p1, new QTEClearPayload(id1));
        ServerPlayNetworking.send(p2, new QTEClearPayload(id2));
        ServerPlayNetworking.send(p1, new ChargedDapHandler.PerfectDapFreezePayload(true));
        ServerPlayNetworking.send(p2, new ChargedDapHandler.PerfectDapFreezePayload(true));
        perfectDapFreezeEnd.put(id1, now + 4500L);
        perfectDapFreezeEnd.put(id2, now + 4500L);
        PoseNetworking.broadcastAnimState(p1, CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND1_P1.ordinal());
        PoseNetworking.broadcastAnimState(p2, CoopAnimationHandler.AnimState.PERFECT_DAP_EXTEND1_P2.ordinal());
        p1.sendMessage(Text.literal("§d§l★ EXTENDER DAP! ★"), true);
        p2.sendMessage(Text.literal("§d§l★ EXTENDER DAP! ★"), true);
        new Thread(() -> {
            try {
                Thread.sleep(4500L);
                world.getServer().execute(() -> {
                    ServerPlayNetworking.send(p1, new ChargedDapHandler.PerfectDapFreezePayload(false));
                    ServerPlayNetworking.send(p2, new ChargedDapHandler.PerfectDapFreezePayload(false));
                    perfectDapFreezeEnd.remove(id1);
                    perfectDapFreezeEnd.remove(id2);
                    PoseNetworking.broadcastAnimState(p1, 0);
                    PoseNetworking.broadcastAnimState(p2, 0);
                    HighFiveHandler.cleanup(id1);
                    HighFiveHandler.cleanup(id2);
                    long cooldownTime = System.currentTimeMillis() + 1000L;
                    comboCooldown.put(id1, cooldownTime);
                    comboCooldown.put(id2, cooldownTime);
                });
            } catch (InterruptedException var6) {
            }
        }).start();
    }

    public static boolean isInQTE(UUID playerId) {
        return QTEManager.isInQTE(playerId) || CoopMovesConfig.get().enableDapCombo && DapComboChain.isInCombo(playerId);
    }

    private static void executeTier4Legendary(
        ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2, boolean perfectHit, boolean bothCharging, double speed1, double speed2
    ) {
        MinecraftServer server = world.getServer();
        UUID p1Id = p1.getUuid();
        UUID p2Id = p2.getUuid();
        boolean bothHeavenReady = heavenReady.contains(p1Id) && heavenReady.contains(p2Id);
        if (bothHeavenReady && perfectHit) {
            startHeavenDap(p1, p2, pos, world);
            new Thread(() -> {
                try {
                    Thread.sleep(500L);
                    world.getServer().execute(() -> {
                        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 255, false, false));
                        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 255, false, false));
                        spawnExpandingLegendarySonicBoom(world, pos);
                        activeHeavenParticles.add(new ChargedDapHandler.HeavenParticleSpawner(world, pos, 30000L));
                        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 20, 5.0, 5.0, 5.0, 0.0);
                        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 300, 10.0, 10.0, 10.0, 0.5);
                        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 200, 8.0, 8.0, 8.0, 0.4);
                        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 10, 0.0, 0.0, 0.0, 0.0);
                        spawnStarBurst(world, pos, 100, 5.0);
                        createMassiveShockwave(world, pos, p1, p2, 50.0, 10.0);
                        activeSaturnRings.add(new ChargedDapHandler.SaturnRing(pos, System.currentTimeMillis()));
                    });
                } catch (InterruptedException var5) {
                    var5.printStackTrace();
                }
            }).start();
        } else if (bothCharging) {
            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 2.0F, 0.5F);
            world.playSound(
                null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.0F, 0.7F
            );
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.PLAYERS, 2.0F, 0.8F);
            spawnStarBurst(world, pos, 40, 1.5);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 3, 1.0, 1.0, 1.0, 0.0);
            world.spawnParticles(ParticleTypes.SOUL, pos.x, pos.y, pos.z, 50, 0.5, 0.5, 0.5, 0.2);
            world.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 100, 1.0, 1.0, 1.0, 0.1);
            world.createExplosion(null, pos.x, pos.y, pos.z, 6.0F, !CoopMovesConfig.get().noGriefMode, ExplosionSourceType.MOB);
            removeTotem(p1);
            removeTotem(p2);
            p1.setHealth(0.0F);
            p2.setHealth(0.0F);
            p1.onDeath(world.getDamageSources().magic());
            p2.onDeath(world.getDamageSources().magic());
            p1.sendMessage(Text.literal("§4§l☠ THE POWER WAS TOO GREAT! ☠"), true);
            p2.sendMessage(Text.literal("§4§l☠ THE POWER WAS TOO GREAT! ☠"), true);

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(
                    Text.literal(
                        "§4§l☠ "
                            + p1.getName().getString()
                            + " §7and §4"
                            + p2.getName().getString()
                            + " §7failed to achieve Perfect Friendship... §c§lTHEY PERISHED!"
                    ),
                    false
                );
            }
        } else {
            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 2.0F, 0.9F);
            world.playSound(
                null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.0F, 1.0F
            );
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 2.0F, 0.9F);
            spawnStarBurst(world, pos, 30, 1.0);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 40, 0.5, 0.5, 0.5, 0.25);
            world.createExplosion(null, pos.x, pos.y, pos.z, 5.0F, !CoopMovesConfig.get().noGriefMode, ExplosionSourceType.MOB);
            applyKnockback(p1, p2, pos, 2.0);
            p1.sendMessage(Text.literal("§d§l⚡ LEGENDARY DAP! ⚡"), true);
            p2.sendMessage(Text.literal("§d§l⚡ LEGENDARY DAP! ⚡"), true);
        }
    }

    private static void removeTotem(ServerPlayerEntity player) {
        if (player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            player.getMainHandStack().setCount(0);
        }

        if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            player.getOffHandStack().setCount(0);
        }
    }

    public static void checkTickSpeedRestore(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (tickSpeedRestoreTime > 0L && now >= tickSpeedRestoreTime) {
            server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(), "tick rate 20");
            tickSpeedRestoreTime = 0L;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(Text.literal("§7Time returns to normal..."), false);
            }
        }

        Iterator<Entry<UUID, Long>> it = perfectFriendshipLevitation.entrySet().iterator();
        Set<UUID> processed = new HashSet<>();

        while (it.hasNext()) {
            Entry<UUID, Long> entry = it.next();
            UUID playerId = entry.getKey();
            long endTime = entry.getValue();
            if (now >= endTime && !processed.contains(playerId)) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                UUID partnerId = perfectFriendshipPartner.get(playerId);
                ServerPlayerEntity partner = partnerId != null ? server.getPlayerManager().getPlayer(partnerId) : null;
                if (player != null) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 600, 0, false, true));
                    processed.add(playerId);
                }

                if (player != null && partner != null && !processed.contains(partnerId)) {
                    partner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 600, 0, false, true));
                    processed.add(partnerId);

                    for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                        p.sendMessage(
                            Text.literal(
                                "§d§l✨ "
                                    + player.getName().getString()
                                    + " §7and §d§l"
                                    + partner.getName().getString()
                                    + " §7have achieved §b§lPERFECT FRIENDSHIP§7! §d§l✨"
                            ),
                            false
                        );
                    }
                }

                it.remove();
            }
        }

        for (UUID id : processed) {
            perfectFriendshipPartner.remove(id);
            perfectFriendshipLevitation.remove(id);
        }
    }

    private static void grantHeavenFlight(ServerPlayerEntity p) {
        if (!p.getAbilities().allowFlying) {
            p.getAbilities().allowFlying = true;
            heavenFlightGranted.add(p.getUuid());
        }

        p.getAbilities().flying = true;
        p.sendAbilitiesUpdate();
    }

    private static void revokeHeavenFlight(ServerPlayerEntity p) {
        if (p != null && heavenFlightGranted.remove(p.getUuid())) {
            if (!p.isCreative() && !p.isSpectator()) {
                p.getAbilities().allowFlying = false;
                p.getAbilities().flying = false;
                p.sendAbilitiesUpdate();
            }
        }
    }

    public static void startHeavenDap(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d midpoint, ServerWorld world) {
        long now = System.currentTimeMillis();
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        ServerPlayNetworking.send(p1, new HeavenDapPayloads.HeavenImpactPayload());
        ServerPlayNetworking.send(p2, new HeavenDapPayloads.HeavenImpactPayload());
        ServerPlayNetworking.send(p1, new ChargedDapHandler.PerfectDapImpactFramePayload(1));
        ServerPlayNetworking.send(p2, new ChargedDapHandler.PerfectDapImpactFramePayload(1));
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z, ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 3.0F, 1.2F);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 2.0F, 1.0F);
        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 255, false, false));
        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 255, false, false));
        Vec3d craterReturn = HeavenCrater.start(world, midpoint, p1, p2, !CoopMovesConfig.get().noGriefMode);
        spawnSonicBoomCircles(world, midpoint);
        world.spawnParticles(ParticleTypes.FLASH, midpoint.x, midpoint.y, midpoint.z, 5, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, midpoint.x, midpoint.y, midpoint.z, 3, 0.5, 0.5, 0.5, 0.0);
        world.spawnParticles(ParticleTypes.END_ROD, midpoint.x, midpoint.y, midpoint.z, 50, 1.0, 1.0, 1.0, 0.3);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, midpoint.x, midpoint.y, midpoint.z, 40, 0.8, 0.8, 0.8, 0.2);
        heavenPlayers.put(id1, new ChargedDapHandler.HeavenDapData(craterReturn, world, now, id2));
        heavenPlayers.put(id2, new ChargedDapHandler.HeavenDapData(craterReturn, world, now, id1));
        new Thread(() -> {
            try {
                Thread.sleep(700L);
                p1.getServer().execute(() -> {
                    if (!p1.isRemoved() && !p2.isRemoved()) {
                        double heavenY = 500.0;
                        Vec3d heavenMid = new Vec3d(midpoint.x, heavenY, midpoint.z);
                        Vec3d dir = p2.getPos().subtract(p1.getPos()).normalize();
                        Vec3d pos1 = heavenMid.add(dir.multiply(-2.5));
                        Vec3d pos2 = heavenMid.add(dir.multiply(2.5));
                        double dx = pos2.x - pos1.x;
                        double dz = pos2.z - pos1.z;
                        float yaw1 = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
                        float yaw2 = yaw1 + 180.0F;
                        p1.teleport(world, pos1.x, pos1.y, pos1.z, yaw1, 0.0F);
                        p2.teleport(world, pos2.x, pos2.y, pos2.z, yaw2, 0.0F);
                        p1.stopFallFlying();
                        p2.stopFallFlying();
                        p1.setVelocity(Vec3d.ZERO);
                        p2.setVelocity(Vec3d.ZERO);
                        p1.velocityModified = true;
                        p2.velocityModified = true;
                        grantHeavenFlight(p1);
                        grantHeavenFlight(p2);
                        PoseNetworking.broadcastAnimState(p1, CoopAnimationHandler.AnimState.HEAVEN_DAP.ordinal());
                        PoseNetworking.broadcastAnimState(p2, CoopAnimationHandler.AnimState.HEAVEN_DAP.ordinal());
                        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 400, 0, false, false));
                        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 400, 0, false, false));
                        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 40, 0, false, false));
                        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 40, 0, false, false));
                        ServerPlayNetworking.send(p1, new HeavenDapPayloads.HeavenDapStartPayload());
                        ServerPlayNetworking.send(p2, new HeavenDapPayloads.HeavenDapStartPayload());
                    }
                });
            } catch (InterruptedException var5) {
                var5.printStackTrace();
            }
        }).start();
    }

    private static void spawnSonicBoomCircles(ServerWorld world, Vec3d pos) {
        new Thread(() -> {
            try {
                for (int circle = 0; circle < 3; circle++) {
                    double baseRadius = 2.0 + circle * 2.0;
                    world.getServer().execute(() -> {
                        for (int i = 0; i < 60; i++) {
                            double angle = Math.toRadians(i * 6);
                            double x = pos.x + Math.cos(angle) * baseRadius;
                            double z = pos.z + Math.sin(angle) * baseRadius;
                            world.spawnParticles(ParticleTypes.WHITE_ASH, x, pos.y + 0.5, z, 3, 0.1, 0.3, 0.1, 0.05);
                            world.spawnParticles(ParticleTypes.CLOUD, x, pos.y + 0.5, z, 2, 0.05, 0.2, 0.05, 0.02);
                        }
                    });
                    Thread.sleep(100L);
                }
            } catch (InterruptedException var6) {
                var6.printStackTrace();
            }
        }).start();
    }

    private static void spawnPerfectDapSonicBoom(ServerWorld world, Vec3d center) {
        spawnExpandingRing(world, center, 0.5, 3.0, 200);
    }

    private static void spawnFireDapSonicBoom(ServerWorld world, Vec3d pos) {
        new Thread(() -> {
            try {
                spawnExpandingFireRing(world, pos, 2.0, 10.0, 250);
                Thread.sleep(100L);
                spawnExpandingFireRing(world, pos, 5.0, 15.0, 350);
            } catch (InterruptedException var3) {
                var3.printStackTrace();
            }
        }).start();
    }

    private static void spawnExpandingFireRing(ServerWorld world, Vec3d center, double startRadius, double endRadius, int durationMs) {
        int steps = 15;
        double radiusStep = (endRadius - startRadius) / steps;
        long stepDuration = durationMs / steps;
        new Thread(() -> {
            try {
                for (int step = 0; step < steps; step++) {
                    double radius = startRadius + radiusStep * step;
                    world.getServer().execute(() -> {
                        int particleCount = (int)(radius * 15.0);

                        for (int i = 0; i < particleCount; i++) {
                            double angle = Math.toRadians(360.0 / particleCount * i);
                            double x = center.x + Math.cos(angle) * radius;
                            double z = center.z + Math.sin(angle) * radius;
                            double y = center.y + 0.5;
                            double vx = Math.cos(angle) * 0.35;
                            double vz = Math.sin(angle) * 0.35;
                            world.spawnParticles(ParticleTypes.FLAME, x, y, z, 0, vx, 0.0, vz, 0.5);
                            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, vx * 0.8, 0.0, vz * 0.8, 0.4);
                            if (i % 3 == 0) {
                                world.spawnParticles(ParticleTypes.LAVA, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                            }
                        }
                    });
                    Thread.sleep(stepDuration);
                }
            } catch (InterruptedException var14) {
                var14.printStackTrace();
            }
        }).start();
    }

    private static void spawnExpandingLegendarySonicBoom(ServerWorld world, Vec3d center) {
        new Thread(() -> {
            try {
                spawnExpandingRing(world, center, 1.0, 6.0, 300);
                Thread.sleep(200L);
                spawnExpandingRing(world, center, 6.0, 15.0, 400);
                Thread.sleep(200L);
                spawnExpandingRing(world, center, 15.0, 30.0, 500);
            } catch (InterruptedException var3) {
                var3.printStackTrace();
            }
        }).start();
    }

    private static void spawnExpandingRing(ServerWorld world, Vec3d center, double startRadius, double endRadius, int durationMs) {
        int steps = 20;
        double radiusStep = (endRadius - startRadius) / steps;
        long stepDuration = durationMs / steps;
        new Thread(() -> {
            try {
                for (int step = 0; step < steps; step++) {
                    double radius = startRadius + radiusStep * step;
                    world.getServer().execute(() -> {
                        int particleCount = (int)(radius * 20.0);

                        for (int i = 0; i < particleCount; i++) {
                            double angle = Math.toRadians(360.0 / particleCount * i);
                            double x = center.x + Math.cos(angle) * radius;
                            double z = center.z + Math.sin(angle) * radius;
                            double y = center.y + 0.5;
                            double vx = Math.cos(angle) * 0.4;
                            double vz = Math.sin(angle) * 0.4;
                            world.spawnParticles(ParticleTypes.CLOUD, x, y, z, 0, vx, 0.0, vz, 0.6);
                            world.spawnParticles(ParticleTypes.WHITE_ASH, x, y, z, 0, vx, 0.0, vz, 0.5);
                            if (i % (particleCount / 24) == 0) {
                                world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                            }
                        }
                    });
                    Thread.sleep(stepDuration);
                }
            } catch (InterruptedException var14) {
                var14.printStackTrace();
            }
        }).start();
    }

    private static void createMassiveShockwave(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2, double radius, double strength) {
        Box shockwaveBox = new Box(
            pos.x - radius,
            pos.y - radius,
            pos.z - radius,
            pos.x + radius,
            pos.y + radius,
            pos.z + radius
        );

        for (Entity entity : world.getOtherEntities(null, shockwaveBox)) {
            if (entity != p1 && entity != p2) {
                double dist = entity.getPos().distanceTo(pos);
                if (!(dist > radius) && !(dist < 0.5)) {
                    double knockbackStrength = (1.0 - dist / radius) * strength;
                    Vec3d knockDir = entity.getPos().subtract(pos).normalize();
                    entity.addVelocity(knockDir.x * knockbackStrength * 2.0, knockbackStrength * 1.5, knockDir.z * knockbackStrength * 2.0);
                    entity.velocityModified = true;
                }
            }
        }

        world.playSound(
            null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 3.0F, 0.5F
        );
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.PLAYERS, 3.0F, 0.6F);
    }

    private static void executeTier5FireDap(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2, boolean perfectHit) {
        UUID p1Id = p1.getUuid();
        UUID p2Id = p2.getUuid();
        boolean bothHeavenReady = heavenReady.contains(p1Id) && heavenReady.contains(p2Id);
        double speed1 = getMaxRecentSpeed(p1Id);
        double speed2 = getMaxRecentSpeed(p2Id);
        boolean bothMovingFast = speed1 >= heavenMinSpeed() && speed2 >= heavenMinSpeed();
        if (bothHeavenReady && bothMovingFast) {
            startHeavenDap(p1, p2, pos, world);
        } else {
            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 2.0F, 1.0F);
            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.FIRE_IMPACT, SoundCategory.PLAYERS, 2.0F, 1.0F);
            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.EXPLOSION_IMPACT, SoundCategory.PLAYERS, 1.5F, 1.0F);
            world.playSound(
                null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.5F, 1.3F
            );
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 2.0F, 0.8F);
            spawnFireDapSonicBoom(world, pos);
            Random rand = new Random();
            int sphereParticles = 24;

            for (int i = 0; i < sphereParticles; i++) {
                double theta = rand.nextDouble() * 2.0 * Math.PI;
                double phi = Math.acos(2.0 * rand.nextDouble() - 1.0);
                double radius = 0.5 + rand.nextDouble() * 0.5;
                double dx = Math.sin(phi) * Math.cos(theta) * radius;
                double dy = Math.cos(phi) * radius;
                double dz = Math.sin(phi) * Math.sin(theta) * radius;
                double speed = 0.15 + rand.nextDouble() * 0.15;
                world.spawnParticles(
                    ParticleTypes.FLAME, pos.x + dx, pos.y + dy + 1.0, pos.z + dz, 1, dx * speed, dy * speed, dz * speed, 0.1
                );
            }

            world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y + 1.0, pos.z, 30, 0.3, 0.3, 0.3, 0.2);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 1.0, pos.z, 15, 0.2, 0.2, 0.2, 0.15);
            world.spawnParticles(ParticleTypes.LAVA, pos.x, pos.y + 1.0, pos.z, 8, 0.3, 0.3, 0.3, 0.0);
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 3, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z, 20, 0.5, 0.5, 0.5, 0.15);
            world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0, pos.z, 25, 0.5, 0.5, 0.5, 0.3);

            for (int i = 0; i < 16; i++) {
                double angle = Math.toRadians(i * 22.5);

                for (double r = 1.0; r <= 2.5; r += 0.5) {
                    double ringX = Math.cos(angle) * r;
                    double ringZ = Math.sin(angle) * r;
                    world.spawnParticles(ParticleTypes.FLAME, pos.x + ringX, pos.y, pos.z + ringZ, 1, 0.05, 0.1, 0.05, 0.02);
                }
            }

            createFireShockwave(world, pos, p1, p2);
            if (perfectHit) {
                world.spawnParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y, pos.z, 40, 0.8, 0.8, 0.8, 0.15);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 50, 0.6, 0.6, 0.6, 0.3);
                p1.sendMessage(Text.literal("§c§l\ud83d\udd25 PERFECT FIRE DAP! \ud83d\udd25"), true);
                p2.sendMessage(Text.literal("§c§l\ud83d\udd25 PERFECT FIRE DAP! \ud83d\udd25"), true);
            } else {
                p1.sendMessage(Text.literal("§c§l\ud83d\udd25 FIRE DAP! \ud83d\udd25"), true);
                p2.sendMessage(Text.literal("§c§l\ud83d\udd25 FIRE DAP! \ud83d\udd25"), true);
            }

            startFireDap(p1, p2, pos);

            for (ServerPlayerEntity nearby : PlayerLookup.around(world, pos, 50.0)) {
                if (nearby != p1 && nearby != p2) {
                    String prefix = perfectHit ? "§c§lPERFECT " : "§c§l";
                    nearby.sendMessage(
                        Text.literal(
                            prefix
                                + "\ud83d\udd25 "
                                + p1.getName().getString()
                                + " §7and §c"
                                + p2.getName().getString()
                                + " §7unleashed a §c§lFIRE DAP§7!"
                        ),
                        false
                    );
                }
            }
        }
    }

    private static void spawnPrecisionDapParticles(ServerWorld world, Vec3d pos, int tier) {
        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        stand.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setCustomNameVisible(false);
        world.spawnEntity(stand);
        Vec3d exactPos = stand.getPos().add(0.0, 1.0, 0.0);
        int particleCount = 15 + tier * 5;
        world.spawnParticles(ParticleTypes.CRIT, exactPos.x, exactPos.y, exactPos.z, particleCount, 0.3, 0.3, 0.3, 0.15);
        if (tier >= 3) {
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT, exactPos.x, exactPos.y, exactPos.z, particleCount / 2, 0.4, 0.4, 0.4, 0.2);
        }

        new Thread(() -> {
            try {
                Thread.sleep(50L);
                world.getServer().execute(() -> stand.discard());
            } catch (InterruptedException var3x) {
                world.getServer().execute(() -> stand.discard());
            }
        }).start();
    }

    private static float calculateYawToFace(ServerPlayerEntity from, ServerPlayerEntity to) {
        Vec3d fromPos = from.getPos();
        Vec3d toPos = to.getPos();
        double dx = toPos.x - fromPos.x;
        double dz = toPos.z - fromPos.z;
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        return (float)angle;
    }

    private static void smoothRotateToFacePartner(ServerPlayerEntity player, ServerPlayerEntity partner) {
        float targetYaw = calculateYawToFace(player, partner);
        float currentYaw = player.getYaw();
        targetYaw = (targetYaw % 360.0F + 540.0F) % 360.0F - 180.0F;
        currentYaw = (currentYaw % 360.0F + 540.0F) % 360.0F - 180.0F;
        float diff = targetYaw - currentYaw;
        if (diff > 180.0F) {
            diff -= 360.0F;
        }

        if (diff < -180.0F) {
            diff += 360.0F;
        }

        float finalDiff = diff;
        int steps = 10;
        long delayPerStep = 50L;
        new Thread(() -> {
            for (int i = 1; i <= 10; i++) {
                int step = i;

                try {
                    Thread.sleep(50L);
                    player.getServer().execute(() -> {
                        float progress = step / 10.0F;
                        float newYaw = currentYaw + finalDiff * progress;
                        player.setYaw(newYaw);
                        player.networkHandler.sendPacket(new EntityPositionS2CPacket(player));
                    });
                } catch (InterruptedException var6x) {
                    break;
                }
            }
        }).start();
    }

    private static void rotateBothPlayersToFaceEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        smoothRotateToFacePartner(p1, p2);
        smoothRotateToFacePartner(p2, p1);
    }

    private static void spawnStarBurst(ServerWorld world, Vec3d pos, int rays, double spread) {
        for (int i = 0; i < rays; i++) {
            double angle = (Math.PI * 2) * i / rays;
            double dx = Math.cos(angle) * spread;
            double dz = Math.sin(angle) * spread;
            world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 2, dx, 0.2, dz, 0.15);
        }
    }

    private static void applyKnockback(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d center, double strength) {
        p1.setVelocity(0.0, 0.0, 0.0);
        p2.setVelocity(0.0, 0.0, 0.0);
        p1.velocityModified = true;
        p2.velocityModified = true;
    }

    public static void applyImpactFreeze(ServerPlayerEntity p1, ServerPlayerEntity p2, int ticks) {
        if (ticks > 0) {
            p1.setVelocity(0.0, 0.0, 0.0);
            p2.setVelocity(0.0, 0.0, 0.0);
            p1.velocityModified = true;
            p2.velocityModified = true;
            impactFreezeTicks.put(p1.getUuid(), ticks);
            impactFreezeTicks.put(p2.getUuid(), ticks);
        }
    }

    private static void createExplosion(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2, double radiusIgnored, float maxDamageIgnored) {
        CoopMovesConfig cfg = CoopMovesConfig.get();
        if (cfg.dapBlastEnabled) {
            double radius = Math.max(0.1, cfg.dapBlastRadius);
            float maxDamage = cfg.dapBlastDamage;
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
                        double knockbackStrength = (1.0 - dist / radius) * cfg.dapBlastKnockback;
                        Vec3d knockDir = entity.getPos().subtract(pos).normalize();
                        entity.addVelocity(knockDir.x * knockbackStrength, knockbackStrength * 0.5, knockDir.z * knockbackStrength);
                        entity.velocityModified = true;
                        if (entity instanceof ServerPlayerEntity target && maxDamage > 0.0F) {
                            float damage = (float)((1.0 - dist / radius) * maxDamage);
                            target.damage(world.getDamageSources().explosion(null, null), damage);
                        }
                    }
                }
            }
        }
    }

    private static void createShockwave(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2, double radius, double strength) {
        Box shockwaveBox = new Box(
            pos.x - radius,
            pos.y - radius,
            pos.z - radius,
            pos.x + radius,
            pos.y + radius,
            pos.z + radius
        );

        for (int i = 0; i < 36; i++) {
            double angle = i / 36.0 * Math.PI * 2.0;

            for (double r = 1.0; r <= radius; r += 2.0) {
                double px = pos.x + Math.cos(angle) * r;
                double pz = pos.z + Math.sin(angle) * r;
                world.spawnParticles(ParticleTypes.CLOUD, px, pos.y, pz, 1, 0.0, 0.1, 0.0, 0.02);
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, px, pos.y + 0.5, pz, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        for (Entity entity : world.getOtherEntities(null, shockwaveBox)) {
            if (entity != p1 && entity != p2) {
                double dist = entity.getPos().distanceTo(pos);
                if (!(dist > radius) && !(dist < 0.5)) {
                    double knockbackStrength = (1.0 - dist / radius) * strength;
                    Vec3d knockDir = entity.getPos().subtract(pos).normalize();
                    entity.addVelocity(knockDir.x * knockbackStrength * 1.5, knockbackStrength * 0.6, knockDir.z * knockbackStrength * 1.5);
                    entity.velocityModified = true;
                    if (entity instanceof ServerPlayerEntity target) {
                        world.playSound(
                            null,
                            target.getX(),
                            target.getY(),
                            target.getZ(),
                            SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                            SoundCategory.PLAYERS,
                            0.8F,
                            0.8F
                        );
                    }
                }
            }
        }

        world.playSound(
            null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.6F, 1.5F
        );
    }

    private static void handleUnderwaterPerfectDap(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (p1.isSubmergedInWater() || p2.isSubmergedInWater()) {
            p1.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 120, 0, false, false));
            p2.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 120, 0, false, false));
            world.spawnParticles(ParticleTypes.SPLASH, pos.x, pos.y, pos.z, 80, 2.0, 2.0, 2.0, 0.4);
            world.spawnParticles(ParticleTypes.BUBBLE_POP, pos.x, pos.y, pos.z, 40, 1.5, 1.5, 1.5, 0.3);
            UUID trackId = UUID.randomUUID();
            underwaterRemovalStart.put(trackId, System.currentTimeMillis());
            underwaterRemovalPos.put(trackId, pos);
            underwaterRemovalWorld.put(trackId, world);
        }
    }

    private static void createLegendaryExplosion(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        double radius = 6.0;
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
                    double knockbackStrength = (1.0 - dist / radius) * 3.0;
                    Vec3d knockDir = entity.getPos().subtract(pos).normalize();
                    entity.addVelocity(knockDir.x * knockbackStrength, knockbackStrength * 0.7, knockDir.z * knockbackStrength);
                    entity.velocityModified = true;
                    float damage;
                    if (entity instanceof ServerPlayerEntity) {
                        damage = (float)((1.0 - dist / radius) * 8.0);
                    } else {
                        damage = (float)((1.0 - dist / radius) * 20.0);
                    }

                    entity.damage(world.getDamageSources().explosion(null, null), damage);
                }
            }
        }
    }

    private static void createFireShockwave(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        CoopMovesConfig cfg = CoopMovesConfig.get();
        double radius = Math.max(0.1, cfg.fireDapKnockbackRadius);
        Box damageBox = new Box(
            pos.x - radius,
            pos.y - radius,
            pos.z - radius,
            pos.x + radius,
            pos.y + radius,
            pos.z + radius
        );
        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 1, false, false, true));
        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 1, false, false, true));

        for (Entity entity : world.getOtherEntities(null, damageBox)) {
            if (entity != p1 && entity != p2) {
                double dist = entity.getPos().distanceTo(pos);
                if (!(dist > radius)) {
                    if (cfg.fireDapKnockbackEnabled) {
                        double knockbackStrength = (1.0 - dist / radius) * cfg.fireDapKnockback;
                        Vec3d knockDir = entity.getPos().subtract(pos).normalize();
                        entity.addVelocity(knockDir.x * knockbackStrength, knockbackStrength * 1.5, knockDir.z * knockbackStrength);
                        entity.velocityModified = true;
                    }

                    if (cfg.fireDapBurnEntities && entity instanceof LivingEntity) {
                        entity.setOnFireFor(cfg.fireDapBurnSeconds);
                    }
                }
            }
        }

        boolean placeFire = cfg.fireDapPlaceFire && !cfg.noGriefMode;

        for (double ringRadius = 3.0; placeFire && ringRadius <= 15.0; ringRadius++) {
            int points = (int)(ringRadius * 8.0);

            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2) * i / points;
                double fireX = pos.x + Math.cos(angle) * ringRadius;
                double fireZ = pos.z + Math.sin(angle) * ringRadius;
                BlockPos groundPos = world.getTopPosition(Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(fireX, pos.y, fireZ));
                BlockPos firePos = groundPos.up();
                if (world.getBlockState(firePos).isAir()) {
                    world.setBlockState(firePos, Blocks.FIRE.getDefaultState());
                    world.scheduleBlockTick(firePos, Blocks.FIRE, 60);
                }
            }
        }

        Random rand = new Random();

        for (int ix = 0; ix < 200; ix++) {
            double particleRadius = rand.nextDouble() * 20.0;
            double particleAngle = rand.nextDouble() * 2.0 * Math.PI;
            double px = pos.x + Math.cos(particleAngle) * particleRadius;
            double pz = pos.z + Math.sin(particleAngle) * particleRadius;
            double py = pos.y + rand.nextDouble() * 3.0;
            world.spawnParticles(ParticleTypes.FLAME, px, py, pz, 1, 0.0, 0.5, 0.0, 0.05);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 1, 0.0, 0.3, 0.0, 0.03);
        }
    }

    public static void broadcastChargeCancel(ServerPlayerEntity player) {
        NormalFacingDapHandler.clearConfirm(player.getUuid(), null);
        if (player != null) {
            ChargedDapHandler.ChargeSyncPayload payload = new ChargedDapHandler.ChargeSyncPayload(player.getUuid(), 0.0F, 0.0F, false);

            for (ServerPlayerEntity other : PlayerLookup.all(player.getServer())) {
                ServerPlayNetworking.send(other, payload);
            }
        }
    }

    public static void broadcastWhiffCooldown(ServerPlayerEntity player, long cooldownEnd) {
        if (player != null) {
            ChargedDapHandler.WhiffCooldownPayload payload = new ChargedDapHandler.WhiffCooldownPayload(whiffCooldownMs());
            ServerPlayNetworking.send(player, payload);
            PoseNetworking.broadcastAnimState(player, 0);
        }
    }

    public static void broadcastImpactFrame(ServerPlayerEntity p1, ServerPlayerEntity p2, int durationMs, boolean grayscale) {
        ChargedDapHandler.ImpactFramePayload payload = new ChargedDapHandler.ImpactFramePayload(durationMs, grayscale);
        if (p1 != null) {
            ServerPlayNetworking.send(p1, payload);
        }

        if (p2 != null) {
            ServerPlayNetworking.send(p2, payload);
        }

        if (p1 != null) {
            for (ServerPlayerEntity nearby : PlayerLookup.around(p1.getServerWorld(), p1.getPos(), 20.0)) {
                if (nearby != p1 && nearby != p2) {
                    ServerPlayNetworking.send(nearby, payload);
                }
            }
        }
    }

    private static boolean arePlayersFacingEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        Vec3d p1Pos = p1.getPos();
        Vec3d p2Pos = p2.getPos();
        Vec3d p1ToP2 = new Vec3d(p2Pos.x - p1Pos.x, 0.0, p2Pos.z - p1Pos.z).normalize();
        Vec3d p2ToP1 = p1ToP2.negate();
        double yaw1Rad = Math.toRadians(p1.getYaw());
        Vec3d look1 = new Vec3d(-Math.sin(yaw1Rad), 0.0, Math.cos(yaw1Rad));
        double yaw2Rad = Math.toRadians(p2.getYaw());
        Vec3d look2 = new Vec3d(-Math.sin(yaw2Rad), 0.0, Math.cos(yaw2Rad));
        double dot1 = look1.dotProduct(p1ToP2);
        double dot2 = look2.dotProduct(p2ToP1);
        return dot1 > -0.3 && dot2 > -0.3;
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

    private static Vec3d getEffectiveVelocity(ServerPlayerEntity player) {
        if (player.hasVehicle()) {
            Entity vehicle = player.getVehicle();
            if (vehicle != null) {
                return vehicle.getVelocity();
            }
        }

        return player.isFallFlying() ? player.getVelocity() : player.getVelocity();
    }

    public static float getChargePercent(UUID playerId) {
        Long startTime = chargeStartTime.get(playerId);
        if (startTime == null) {
            return 0.0F;
        } else {
            long elapsed = System.currentTimeMillis() - startTime;
            return Math.min(1.0F, (float)elapsed / (float)chargeTimeMs());
        }
    }

    public static float getFireLevel(UUID playerId) {
        return fireLevel.getOrDefault(playerId, 0.0F);
    }

    public static boolean isCharging(UUID playerId) {
        return chargeStartTime.containsKey(playerId);
    }

    public static boolean isFullyCharged(UUID playerId) {
        Long startTime = chargeStartTime.get(playerId);
        if (startTime == null) {
            return false;
        } else {
            long chargeTime = System.currentTimeMillis() - startTime;
            float chargePercent = Math.min((float)chargeTime / (float)chargeTimeMs(), 1.0F);
            return chargePercent >= 0.8F;
        }
    }

    public static Long getChargeStartTime(UUID playerId) {
        return chargeStartTime.get(playerId);
    }

    public static void resetChargeStartTime(UUID playerId) {
        chargeStartTime.put(playerId, System.currentTimeMillis());
    }

    private static boolean isOnCooldown(UUID uuid) {
        Long cooldownEnd = cooldowns.get(uuid);
        if (cooldownEnd == null) {
            return false;
        } else {
            long now = System.currentTimeMillis();
            if (cooldownEnd - now > 10000L) {
                cooldowns.remove(uuid);
                return false;
            } else {
                return now < cooldownEnd;
            }
        }
    }

    public static void cleanup(UUID uuid) {
        if (perfectDapFreezeEnd.containsKey(uuid) && serverRef != null) {
            ServerPlayerEntity fp = serverRef.getPlayerManager().getPlayer(uuid);
            if (fp != null) {
                ServerPlayNetworking.send(fp, new ChargedDapHandler.PerfectDapFreezePayload(false));
            }
        }

        chargeStartTime.remove(uuid);
        releaseTime.remove(uuid);
        waitingForPartner.remove(uuid);
        cooldowns.remove(uuid);
        speedHistory.remove(uuid);
        fireStartTime.remove(uuid);
        fireGraceTime.remove(uuid);
        fireLevel.remove(uuid);
        impactFreezeTicks.remove(uuid);
        blockingAnimEndTime.remove(uuid);
        perfectDapStartTime.remove(uuid);
        perfectDapPartner.remove(uuid);
        perfectDapFreezeEnd.remove(uuid);
        perfectDapImpactSent.remove(uuid);
        comboCooldown.remove(uuid);
        ArmorStandEntity pStand = perfectDapArmorStands.remove(uuid);
        if (pStand != null && !pStand.isRemoved()) {
            pStand.discard();
        }

        endFireDapFor(uuid);
        fireDapStartTime.remove(uuid);
        fireDapPartner.remove(uuid);
        inFireDapHit.remove(uuid);
        fireCircleSpawned.remove(uuid);
        fireDapComboRequestTime.remove(uuid);
        fireDapComboFreezeEnd.remove(uuid);
        pendingFireArmImpacts.remove(uuid);
        pendingFireTornadoSpawns.remove(uuid);
        fireComboActive.remove(uuid);
        ArmorStandEntity fStand = fireDapArmorStands.remove(uuid);
        if (fStand != null && !fStand.isRemoved()) {
            fStand.discard();
        }

        if (heavenPlayers.containsKey(uuid) && serverRef != null) {
            ServerPlayerEntity hp = serverRef.getPlayerManager().getPlayer(uuid);
            if (hp != null) {
                revokeHeavenFlight(hp);
                hp.removeStatusEffect(StatusEffects.SLOW_FALLING);
                ServerPlayNetworking.send(hp, new HeavenDapPayloads.HeavenDapEndPayload());
                ServerPlayNetworking.send(hp, new ChargedDapHandler.PerfectDapFreezePayload(false));
            }
        }

        heavenPlayers.remove(uuid);
        heavenReady.remove(uuid);
        fireMaxedStartTime.remove(uuid);
        DapSessionManager.removeSessionForPlayer(uuid);
        DapComboChain.cancelCombo(uuid);
        DapFlair.cleanup(uuid);
        PerfectDapComboHandler.cancelCombo(uuid);
    }

    public static boolean isInBlockingAnimation(UUID uuid) {
        Long endTime = blockingAnimEndTime.get(uuid);
        if (endTime == null) {
            return false;
        } else if (System.currentTimeMillis() >= endTime) {
            blockingAnimEndTime.remove(uuid);
            return false;
        } else {
            return true;
        }
    }

    public static void setBlockingAnimation(UUID uuid, long durationMs) {
        blockingAnimEndTime.put(uuid, System.currentTimeMillis() + durationMs);
    }

    public static boolean isPerfectDapFrozen(UUID playerId) {
        return perfectDapFreezeEnd.containsKey(playerId);
    }

    public static boolean isFireDapFrozen(UUID playerId) {
        return fireDapComboFreezeEnd.containsKey(playerId);
    }

    public static boolean isInFireDapBlockingState(UUID playerId) {
        return inFireDapHit.getOrDefault(playerId, false) || fireDapComboFreezeEnd.containsKey(playerId);
    }

    public static boolean isInComboCooldown(UUID playerId) {
        Long cooldownEnd = comboCooldown.get(playerId);
        if (cooldownEnd == null) {
            return false;
        } else {
            long now = System.currentTimeMillis();
            if (now < cooldownEnd) {
                return true;
            } else {
                comboCooldown.remove(playerId);
                return false;
            }
        }
    }

    public static void startFireDap(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d midpoint) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();
        fireComboActive.put(id1, true);
        fireComboActive.put(id2, true);
        fireDapStartTime.put(id1, now);
        fireDapStartTime.put(id2, now);
        fireCircleSpawned.put(id1, false);
        fireCircleSpawned.put(id2, false);
        fireDapPartner.put(id1, id2);
        fireDapPartner.put(id2, id1);
        inFireDapHit.put(id1, true);
        inFireDapHit.put(id2, true);
        DapSession session = DapSessionManager.createSession(id1, id2, 1.2, DapSession.DapType.FIRE_DAP);
        if (session == null) {
            fireComboActive.remove(id1);
            fireComboActive.remove(id2);
            fireDapStartTime.remove(id1);
            fireDapStartTime.remove(id2);
        } else {
            session.onComplete(() -> startFireDapAnimation(p1, p2, midpoint));
        }
    }

    private static void startFireDapAnimation(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d midpoint) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        ServerWorld world = p1.getServerWorld();
        long now = System.currentTimeMillis();
        fireDapStartTime.put(id1, now);
        fireDapStartTime.put(id2, now);
        p1.swingHand(Hand.MAIN_HAND);
        p2.swingHand(Hand.MAIN_HAND);
        Vec3d p1Hand = p1.getPos().add(0.0, 1.4, 0.0);
        Vec3d p2Hand = p2.getPos().add(0.0, 1.4, 0.0);
        Vec3d handMid = p1Hand.add(p2Hand).multiply(0.5);
        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        stand.setPosition(handMid.x, handMid.y, handMid.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setFireTicks(0);
        world.spawnEntity(stand);
        fireDapArmorStands.put(id1, stand);
        PoseNetworking.broadcastAnimState(p1, CoopAnimationHandler.AnimState.FIRE_DAP_HIT.ordinal());
        PoseNetworking.broadcastAnimState(p2, CoopAnimationHandler.AnimState.FIRE_DAP_HIT.ordinal());

        for (ServerPlayerEntity player : p1.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new ChargedDapHandler.FireDapFirstPersonPayload(id1, true));
            ServerPlayNetworking.send(player, new ChargedDapHandler.FireDapFirstPersonPayload(id2, true));
        }

        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 255, false, false));
        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 255, false, false));
        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 255, false, false));
        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 255, false, false));

        for (ServerPlayerEntity player : p1.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new ChargedDapHandler.FireDapFreezePayload(id1, true));
            ServerPlayNetworking.send(player, new ChargedDapHandler.FireDapFreezePayload(id2, true));
        }

        setBlockingAnimation(id1, 2292L);
        setBlockingAnimation(id2, 2292L);
        fireDapComboFreezeEnd.put(id1, now + 2292L);
        fireDapComboFreezeEnd.put(id2, now + 2292L);
        openFireComboQTE(p1, p2, now);
    }

    private static void openFireComboQTE(ServerPlayerEntity p1, ServerPlayerEntity p2, long animStart) {
        long now = System.currentTimeMillis();
        long opens = animStart + 830L;
        long closes = animStart + 1650L;
        ChargedDapHandler.FireComboQTE q = new ChargedDapHandler.FireComboQTE(p1.getUuid(), p2.getUuid(), opens, closes);
        fireComboQTEs.put(q.a, q);
        fireComboQTEs.put(q.b, q);
        ChargedDapHandler.FireDapWindowPayload armed = new ChargedDapHandler.FireDapWindowPayload(
            true, (int)Math.max(1L, closes - now), (int)Math.max(0L, opens - now)
        );
        ServerPlayNetworking.send(p1, armed);
        if (p2 != p1) {
            ServerPlayNetworking.send(p2, armed);
        }
    }

    private static void closeFireComboQTE(MinecraftServer server, ChargedDapHandler.FireComboQTE q) {
        fireComboQTEs.remove(q.a);
        fireComboQTEs.remove(q.b);
        if (server != null) {
            ChargedDapHandler.FireDapWindowPayload closed = new ChargedDapHandler.FireDapWindowPayload(false, 0, 0);
            ServerPlayerEntity pa = server.getPlayerManager().getPlayer(q.a);
            ServerPlayerEntity pb = server.getPlayerManager().getPlayer(q.b);
            if (pa != null) {
                ServerPlayNetworking.send(pa, closed);
            }

            if (pb != null && pb != pa) {
                ServerPlayNetworking.send(pb, closed);
            }
        }
    }

    private static void releaseFireDapLeftovers(UUID id) {
        fireComboActive.remove(id);
        fireCircleSpawned.remove(id);
        ArmorStandEntity st = fireDapArmorStands.remove(id);
        if (st != null && !st.isRemoved()) {
            st.discard();
        }
    }

    private static void onFireComboPress(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        ChargedDapHandler.FireComboQTE q = fireComboQTEs.get(id);
        if (q != null && !q.done) {
            if (inFireDapHit.getOrDefault(id, false)) {
                long now = System.currentTimeMillis();
                if (now < q.opensAt) {
                    q.burned = true;
                    player.sendMessage(Text.literal("§8too early"), true);
                } else if (now <= q.closesAt) {
                    boolean selfTest = q.a.equals(q.b);
                    boolean isA = id.equals(q.a);
                    boolean already = isA ? q.pressedA : q.pressedB;
                    if (!already && !q.burned) {
                        if (selfTest) {
                            q.pressedA = true;
                            q.pressedB = true;
                        } else if (isA) {
                            q.pressedA = true;
                        } else {
                            q.pressedB = true;
                        }

                        if (q.pressedA && q.pressedB && !q.burned) {
                            q.done = true;
                            MinecraftServer server = player.getServer();
                            closeFireComboQTE(server, q);
                            ServerPlayerEntity pa = server.getPlayerManager().getPlayer(q.a);
                            ServerPlayerEntity pb = server.getPlayerManager().getPlayer(q.b);
                            if (pa != null && pb != null) {
                                executeFireDapCombo(pa, pb);
                            }
                        }
                    } else {
                        q.burned = true;
                        player.sendMessage(Text.literal("§8combo burned"), true);
                    }
                }
            }
        }
    }

    private static void tickFireComboQTEs(MinecraftServer server, long now) {
        if (!fireComboQTEs.isEmpty()) {
            for (ChargedDapHandler.FireComboQTE q : new HashSet<>(fireComboQTEs.values())) {
                if (!q.done && now > q.closesAt) {
                    q.done = true;
                    closeFireComboQTE(server, q);
                    ServerPlayerEntity pa = server.getPlayerManager().getPlayer(q.a);
                    ServerPlayerEntity pb = server.getPlayerManager().getPlayer(q.b);
                    String msg;
                    if (q.burned) {
                        msg = "§8✗ combo burned";
                    } else if (!q.pressedA && !q.pressedB) {
                        msg = "§7✗ no combo";
                    } else {
                        String who = !q.pressedA ? (pa != null ? pa.getName().getString() : "?") : (pb != null ? pb.getName().getString() : "?");
                        msg = "§c✗ " + who + " missed the combo!";
                    }

                    if (pa != null) {
                        pa.sendMessage(Text.literal(msg), true);
                    }

                    if (pb != null && pb != pa) {
                        pb.sendMessage(Text.literal(msg), true);
                    }
                }
            }
        }
    }

    private static void endFireDapFor(UUID uuid) {
        UUID partnerId = fireDapPartner.get(uuid);
        ChargedDapHandler.FireComboQTE q = fireComboQTEs.get(uuid);
        if (q != null) {
            if (partnerId == null) {
                partnerId = q.a.equals(uuid) ? q.b : q.a;
            }

            q.done = true;
            closeFireComboQTE(serverRef, q);
        }

        Set<UUID> ids = new HashSet<>();
        ids.add(uuid);
        if (partnerId != null) {
            ids.add(partnerId);
        }

        for (UUID id : ids) {
            boolean wasInFire = fireDapStartTime.containsKey(id)
                || inFireDapHit.containsKey(id)
                || fireDapComboFreezeEnd.containsKey(id)
                || fireComboActive.containsKey(id);
            fireDapStartTime.remove(id);
            fireDapPartner.remove(id);
            inFireDapHit.remove(id);
            fireCircleSpawned.remove(id);
            fireDapComboRequestTime.remove(id);
            fireDapComboFreezeEnd.remove(id);
            fireComboActive.remove(id);
            blockingAnimEndTime.remove(id);
            ArmorStandEntity st = fireDapArmorStands.remove(id);
            if (st != null && !st.isRemoved()) {
                st.discard();
            }

            if (wasInFire && serverRef != null) {
                for (ServerPlayerEntity p : serverRef.getPlayerManager().getPlayerList()) {
                    ServerPlayNetworking.send(p, new ChargedDapHandler.FireDapFreezePayload(id, false));
                    ServerPlayNetworking.send(p, new ChargedDapHandler.FireDapFirstPersonPayload(id, false));
                }

                ServerPlayerEntity online = serverRef.getPlayerManager().getPlayer(id);
                if (online != null) {
                    PoseNetworking.broadcastAnimState(online, 0);
                    DapSessionManager.removeSessionForPlayer(id);
                }
            }
        }

        pendingFireArmImpacts.entrySet()
            .removeIf(e -> ids.contains(e.getKey()) || ids.contains(e.getValue().p1.getUuid()) || ids.contains(e.getValue().p2.getUuid()));
        pendingFireTornadoSpawns.entrySet()
            .removeIf(e -> ids.contains(e.getKey()) || ids.contains(e.getValue().p1.getUuid()) || ids.contains(e.getValue().p2.getUuid()));
        if (auraBeamsActive && (ids.contains(auraBeamPlayer1) || ids.contains(auraBeamPlayer2))) {
            auraBeamsActive = false;
        }
    }

    private static void executeFireDapCombo(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();
        inFireDapHit.remove(id1);
        inFireDapHit.remove(id2);
        fireDapComboRequestTime.remove(id1);
        fireDapComboRequestTime.remove(id2);
        DapSessionManager.removeSessionForPlayer(id1);
        p1.swingHand(Hand.MAIN_HAND);
        p2.swingHand(Hand.MAIN_HAND);
        fireDapComboFreezeEnd.put(id1, now + 4000L);
        fireDapComboFreezeEnd.put(id2, now + 4000L);
        setBlockingAnimation(id1, 4000L);
        setBlockingAnimation(id2, 4000L);

        for (ServerPlayerEntity player : p1.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new ChargedDapHandler.FireDapFreezePayload(id1, true));
            ServerPlayNetworking.send(player, new ChargedDapHandler.FireDapFreezePayload(id2, true));
        }

        PoseNetworking.broadcastAnimState(p1, CoopAnimationHandler.AnimState.FIRE_DAP_COMBO_P1.ordinal());
        PoseNetworking.broadcastAnimState(p2, CoopAnimationHandler.AnimState.FIRE_DAP_COMBO_P2.ordinal());
        Vec3d midpoint = p1.getPos().add(p2.getPos()).multiply(0.5);
        p1.getServerWorld()
            .playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 2.0F, 0.8F);

        for (ServerPlayerEntity player : p1.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new ChargedDapHandler.FireDapFirstPersonPayload(id1, true));
            ServerPlayNetworking.send(player, new ChargedDapHandler.FireDapFirstPersonPayload(id2, true));
        }

        p1.sendMessage(Text.literal("§c§l\ud83d\udd25 DIVINE FLAME COMBO! \ud83d\udd25"), true);
        p2.sendMessage(Text.literal("§c§l\ud83d\udd25 DIVINE FLAME COMBO! \ud83d\udd25"), true);
        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 100, 255, false, false));
        p1.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 255, false, false));
        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 100, 255, false, false));
        p2.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 255, false, false));
        spawnVerticalFireWalls(p1, p2);
        auraBeamsActive = true;
        auraBeamStartTime = now;
        auraBeamPlayer1 = id1;
        auraBeamPlayer2 = id2;
        pendingFireArmImpacts.put(id1, new ChargedDapHandler.FireDapScheduledEvent(p1, p2, now + 1330L));
        pendingFireTornadoSpawns.put(id1, new ChargedDapHandler.FireDapScheduledEvent(p1, p2, now + 1460L));
    }

    private static void spawnAnimatedAuraBeam(ServerPlayerEntity player, long elapsed) {
        ServerWorld world = player.getServerWorld();
        Vec3d playerPos = player.getPos();
        double phase = elapsed % 1000L / 1000.0;

        for (int y = 0; y < 70; y++) {
            double currentY = playerPos.y + y;
            double coreAngle = (y * 20 + elapsed * 0.5) % 360.0;
            double coreRad = Math.toRadians(coreAngle);
            double coreRadius = 0.3;
            double coreX = playerPos.x + Math.cos(coreRad) * coreRadius;
            double coreZ = playerPos.z + Math.sin(coreRad) * coreRadius;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, coreX, currentY, coreZ, 1, 0.05, 0.05, 0.05, 0.01);
            if (y % 3 == 0) {
                world.spawnParticles(ParticleTypes.ENCHANT, coreX, currentY, coreZ, 2, 0.1, 0.1, 0.1, 0.5);
            }

            double midAngle = (y * 15 - elapsed * 0.3) % 360.0;
            double midRad = Math.toRadians(midAngle);
            double midRadius = 0.6;
            double midX = playerPos.x + Math.cos(midRad) * midRadius;
            double midZ = playerPos.z + Math.sin(midRad) * midRadius;
            world.spawnParticles(ParticleTypes.FLAME, midX, currentY, midZ, 2, 0.1, 0.1, 0.1, 0.02);
            if (y % 2 == 0) {
                double outerAngle = (y * 10 + elapsed * 0.2) % 360.0;

                for (int i = 0; i < 4; i++) {
                    double angle = outerAngle + i * 90;
                    double rad = Math.toRadians(angle);
                    double outerRadius = 0.9 + Math.sin(phase * Math.PI * 2.0) * 0.2;
                    double x = playerPos.x + Math.cos(rad) * outerRadius;
                    double z = playerPos.z + Math.sin(rad) * outerRadius;
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, currentY, z, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }

            if (y % 5 == 0) {
                double waveOffset = Math.sin((y / 70.0 + phase) * Math.PI * 2.0) * 0.5;
                world.spawnParticles(ParticleTypes.END_ROD, playerPos.x + waveOffset, currentY, playerPos.z, 1, 0.1, 0.1, 0.1, 0.02);
            }
        }

        for (double angle = 0.0; angle < 360.0; angle += 10.0) {
            double rad = Math.toRadians(angle + elapsed * 0.5);
            double radius = 3.0;
            double x = playerPos.x + Math.cos(rad) * radius;
            double z = playerPos.z + Math.sin(rad) * radius;
            world.spawnParticles(ParticleTypes.FLAME, x, playerPos.y + 0.1, z, 3, 0.1, 0.1, 0.1, 0.05);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, playerPos.y + 0.1, z, 2, 0.1, 0.1, 0.1, 0.03);
            if (angle % 30.0 == 0.0) {
                world.spawnParticles(ParticleTypes.END_ROD, x, playerPos.y + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void spawnVerticalFireWalls(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        ServerWorld world = p1.getServerWorld();
        Vec3d midpoint = p1.getPos().add(p2.getPos()).multiply(0.5);
        double wallDistance = 5.0;
        int wallHeight = 50;
        int wallLength = 10;

        for (int x = -wallLength / 2; x <= wallLength / 2; x++) {
            for (int y = 0; y < wallHeight; y++) {
                double wx = midpoint.x + x;
                double wy = midpoint.y + y;
                double wz = midpoint.z + wallDistance;
                if (!(Math.abs(x) <= 1.5)) {
                    world.spawnParticles(ParticleTypes.FLAME, wx, wy, wz, 15, 0.3, 0.3, 0.3, 0.08);
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz, 8, 0.2, 0.2, 0.2, 0.05);
                    if (y % 3 == 0) {
                        world.spawnParticles(ParticleTypes.LAVA, wx, wy, wz, 5, 0.2, 0.2, 0.2, 0.03);
                    }
                }
            }
        }

        for (int x = -wallLength / 2; x <= wallLength / 2; x++) {
            for (int yx = 0; yx < wallHeight; yx++) {
                double wx = midpoint.x + x;
                double wy = midpoint.y + yx;
                double wz = midpoint.z - wallDistance;
                if (!(Math.abs(x) <= 1.5)) {
                    world.spawnParticles(ParticleTypes.FLAME, wx, wy, wz, 15, 0.3, 0.3, 0.3, 0.08);
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz, 8, 0.2, 0.2, 0.2, 0.05);
                    if (yx % 3 == 0) {
                        world.spawnParticles(ParticleTypes.LAVA, wx, wy, wz, 5, 0.2, 0.2, 0.2, 0.03);
                    }
                }
            }
        }

        for (int z = -wallLength / 2; z <= wallLength / 2; z++) {
            for (int yxx = 0; yxx < wallHeight; yxx++) {
                double wx = midpoint.x + wallDistance;
                double wy = midpoint.y + yxx;
                double wz = midpoint.z + z;
                if (!(Math.abs(z) <= 1.5)) {
                    world.spawnParticles(ParticleTypes.FLAME, wx, wy, wz, 15, 0.3, 0.3, 0.3, 0.08);
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz, 8, 0.2, 0.2, 0.2, 0.05);
                    if (yxx % 3 == 0) {
                        world.spawnParticles(ParticleTypes.LAVA, wx, wy, wz, 5, 0.2, 0.2, 0.2, 0.03);
                    }
                }
            }
        }

        for (int z = -wallLength / 2; z <= wallLength / 2; z++) {
            for (int yxxx = 0; yxxx < wallHeight; yxxx++) {
                double wx = midpoint.x - wallDistance;
                double wy = midpoint.y + yxxx;
                double wz = midpoint.z + z;
                if (!(Math.abs(z) <= 1.5)) {
                    world.spawnParticles(ParticleTypes.FLAME, wx, wy, wz, 15, 0.3, 0.3, 0.3, 0.08);
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, wx, wy, wz, 8, 0.2, 0.2, 0.2, 0.05);
                    if (yxxx % 3 == 0) {
                        world.spawnParticles(ParticleTypes.LAVA, wx, wy, wz, 5, 0.2, 0.2, 0.2, 0.03);
                    }
                }
            }
        }
    }

    private static void spawnFireCircle(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        ServerWorld world = p1.getServerWorld();
        Vec3d midpoint = p1.getPos().add(p2.getPos()).multiply(0.5);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, midpoint.x, midpoint.y + 1.0, midpoint.z, 3, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.EXPLOSION, midpoint.x, midpoint.y + 1.0, midpoint.z, 20, 2.0, 2.0, 2.0, 0.0);
        int radius = 20;

        for (double angle = 0.0; angle < 360.0; angle += 2.0) {
            double rad = Math.toRadians(angle);
            double x = midpoint.x + radius * Math.cos(rad);
            double z = midpoint.z + radius * Math.sin(rad);
            world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y + 0.1, z, 20, 0.4, 1.2, 0.4, 0.05);
            world.spawnParticles(ParticleTypes.LAVA, x, midpoint.y + 0.1, z, 10, 0.3, 0.6, 0.3, 0.02);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, midpoint.y + 0.1, z, 15, 0.5, 1.8, 0.5, 0.1);
            if (angle % 20.0 == 0.0) {
                for (int h = 0; h < 10; h++) {
                    world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y + h, z, 25, 0.6, 0.6, 0.6, 0.12);
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, midpoint.y + h, z, 12, 0.4, 0.4, 0.4, 0.06);
                }
            }
        }

        for (int height = 0; height < 15; height++) {
            for (double anglex = 0.0; anglex < 360.0; anglex += 10.0) {
                double rad = Math.toRadians(anglex + height * 25);
                double distance = 3.0 + height * 0.4;
                double x = midpoint.x + distance * Math.cos(rad);
                double z = midpoint.z + distance * Math.sin(rad);
                world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y + height, z, 5, 0.3, 0.3, 0.3, 0.04);
                world.spawnParticles(ParticleTypes.DRAGON_BREATH, x, midpoint.y + height, z, 3, 0.2, 0.2, 0.2, 0.02);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, midpoint.y + height, z, 2, 0.15, 0.15, 0.15, 0.01);
            }
        }

        for (int h = 0; h < 20; h++) {
            world.spawnParticles(ParticleTypes.FLAME, midpoint.x, midpoint.y + h, midpoint.z, 40, 2.0, 0.5, 2.0, 0.15);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, midpoint.x, midpoint.y + h, midpoint.z, 20, 1.5, 0.3, 1.5, 0.1);
            world.spawnParticles(ParticleTypes.LAVA, midpoint.x, midpoint.y + h, midpoint.z, 15, 1.0, 0.2, 1.0, 0.05);
        }

        Random random = new Random();
        int fireCount = 0;
        boolean placeGroundFire = CoopMovesConfig.get().fireDapPlaceFire && !CoopMovesConfig.get().noGriefMode;

        for (int i = 0; placeGroundFire && i < 300; i++) {
            double anglex = random.nextDouble() * Math.PI * 2.0;
            double distance = 4.0 + random.nextDouble() * 16.0;
            double x = midpoint.x + Math.cos(anglex) * distance;
            double z = midpoint.z + Math.sin(anglex) * distance;
            BlockPos pos = new BlockPos((int)x, (int)midpoint.y, (int)z);
            BlockPos above = pos.up();
            if (world.getBlockState(pos).isSolidBlock(world, pos) && world.getBlockState(above).isAir()) {
                world.setBlockState(above, Blocks.FIRE.getDefaultState());
                fireCount++;
                world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y + 0.5, z, 20, 0.5, 1.0, 0.5, 0.08);
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, midpoint.y + 0.5, z, 10, 0.4, 0.8, 0.4, 0.06);
                world.spawnParticles(ParticleTypes.LAVA, x, midpoint.y + 0.1, z, 5, 0.3, 0.2, 0.3, 0.02);
            }
        }

        world.playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 2.5F, 0.5F);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 3.5F, 0.6F);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 2.0F, 0.7F);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z, ModSounds.EPIC_DAP, SoundCategory.PLAYERS, 2.0F, 1.2F);
    }

    private static void executeFireArmImpact(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        ServerWorld world = p1.getServerWorld();
        ArmorStandEntity stand = fireDapArmorStands.get(p1.getUuid());
        Vec3d midpoint;
        if (stand != null && !stand.isRemoved()) {
            midpoint = stand.getPos();
        } else {
            midpoint = p1.getPos().add(p2.getPos()).multiply(0.5).add(0.0, 1.2, 0.0);
        }

        world.spawnParticles(ParticleTypes.FLAME, midpoint.x, midpoint.y, midpoint.z, 3, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, midpoint.x, midpoint.y, midpoint.z, 2, 0.2, 0.2, 0.2, 0.08);
        Vec3d underArms = new Vec3d(midpoint.x, midpoint.y - 0.5, midpoint.z);
        world.spawnParticles(ParticleTypes.FLAME, underArms.x, underArms.y, underArms.z, 8, 0.5, 0.2, 0.5, 0.12);
        world.spawnParticles(ParticleTypes.LAVA, underArms.x, underArms.y, underArms.z, 4, 0.4, 0.15, 0.4, 0.05);

        for (int ring = 1; ring <= 15; ring++) {
            double radius = ring * 1.5;

            for (double angle = 0.0; angle < 360.0; angle += 8.0) {
                double rad = Math.toRadians(angle);
                double x = midpoint.x + radius * Math.cos(rad);
                double z = midpoint.z + radius * Math.sin(rad);
                world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y, z, 3, 0.2, 0.5, 0.2, 0.05);
                world.spawnParticles(ParticleTypes.DRAGON_BREATH, x, midpoint.y, z, 2, 0.15, 0.3, 0.15, 0.03);
            }
        }

        double pillarStartY = midpoint.y + 2.0;

        for (int h = 0; h < 30; h++) {
            world.spawnParticles(ParticleTypes.FLAME, midpoint.x, pillarStartY + h, midpoint.z, 25, 1.5, 0.5, 1.5, 0.15);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, midpoint.x, pillarStartY + h, midpoint.z, 15, 1.2, 0.4, 1.2, 0.1);
            world.spawnParticles(ParticleTypes.LAVA, midpoint.x, pillarStartY + h, midpoint.z, 10, 1.0, 0.3, 1.0, 0.08);
            if (h < 5) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, midpoint.x, pillarStartY + h, midpoint.z, 20, 2.0, 0.5, 2.0, 0.12);
            }
        }

        for (double angle = 0.0; angle < 360.0; angle += 3.0) {
            double rad = Math.toRadians(angle);
            double radius = 3.0;
            double x = midpoint.x + Math.cos(rad) * radius;
            double z = midpoint.z + Math.sin(rad) * radius;
            world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y, z, 5, 0.1, 0.1, 0.1, 0.08);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, midpoint.y, z, 3, 0.1, 0.1, 0.1, 0.05);
            world.spawnParticles(ParticleTypes.END_ROD, x, midpoint.y, z, 1, 0.0, 0.0, 0.0, 0.0);
            if (angle % 15.0 == 0.0) {
                for (int hx = 0; hx < 10; hx++) {
                    world.spawnParticles(ParticleTypes.FLAME, x, midpoint.y + hx, z, 2, 0.05, 0.05, 0.05, 0.02);
                }
            }
        }

        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        CoopMovesConfig ccfg = CoopMovesConfig.get();
        double comboR = Math.max(0.1, ccfg.fireComboRadius);
        Box searchBox = new Box(
            midpoint.x - comboR,
            midpoint.y - comboR,
            midpoint.z - comboR,
            midpoint.x + comboR,
            midpoint.y + comboR,
            midpoint.z + comboR
        );

        for (Entity entity : world.getOtherEntities(null, searchBox)) {
            if (!entity.getUuid().equals(id1) && !entity.getUuid().equals(id2)) {
                Vec3d entityPos = entity.getPos();
                double distance = entityPos.distanceTo(midpoint);
                if (distance < comboR && distance > 0.1) {
                    double falloff = (comboR - distance) / comboR;
                    if (ccfg.fireComboKnockbackEnabled) {
                        Vec3d direction = entityPos.subtract(midpoint).normalize();
                        double strength = falloff * ccfg.fireComboKnockback;
                        entity.setVelocity(direction.x * strength, 0.8 + strength * 0.5, direction.z * strength);
                        entity.velocityModified = true;
                    }

                    if (entity instanceof LivingEntity living) {
                        if (ccfg.fireComboDamage > 0.0F) {
                            living.damage(living.getDamageSources().explosion(null, null), (float)(falloff * ccfg.fireComboDamage));
                        }

                        if (ccfg.fireComboBurnEntities) {
                            living.setOnFireFor(ccfg.fireComboBurnSeconds);
                        }
                    }
                }
            }
        }

        world.playSound(null, midpoint.x, midpoint.y, midpoint.z, ModSounds.GALACTIC_DAP, SoundCategory.PLAYERS, 3.0F, 1.0F);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.5F, 0.8F);
    }

    private static void spawnFireTornado(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        ServerWorld world = p1.getServerWorld();
        Vec3d midpoint = p1.getPos().add(p2.getPos()).multiply(0.5);
        tornadoStartTime = System.currentTimeMillis();
        tornadoActive = true;
        tornadoCenter = midpoint;
        tornadoWorld = world;
        activeTornadoSwirls.clear();
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 4.0F, 0.4F);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 3.0F, 0.6F);
    }

    private static void teleportFireDapFacingEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2, double targetDistance) {
        Vec3d p1Pos = p1.getPos();
        Vec3d p2Pos = p2.getPos();
        double distance = p1Pos.distanceTo(p2Pos);
        Vec3d direction = p2Pos.subtract(p1Pos).normalize();
        Vec3d midpoint = p1Pos.add(p2Pos).multiply(0.5);
        double dx = p2Pos.x - p1Pos.x;
        double dz = p2Pos.z - p1Pos.z;
        float yawP1 = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float yawP2 = yawP1 + 180.0F;
        if (Math.abs(distance - targetDistance) > 0.05) {
            Vec3d offset = direction.multiply(targetDistance / 2.0);
            Vec3d targetP1 = midpoint.subtract(offset);
            Vec3d targetP2 = midpoint.add(offset);
            p1.teleport(p1.getServerWorld(), targetP1.x, targetP1.y, targetP1.z, yawP1, 0.0F);
            p2.teleport(p2.getServerWorld(), targetP2.x, targetP2.y, targetP2.z, yawP2, 0.0F);
            p1.setYaw(yawP1);
            p1.setBodyYaw(yawP1);
            p1.setHeadYaw(yawP1);
            p2.setYaw(yawP2);
            p2.setBodyYaw(yawP2);
            p2.setHeadYaw(yawP2);
        } else {
            p1.teleport(p1.getServerWorld(), p1Pos.x, p1Pos.y, p1Pos.z, yawP1, 0.0F);
            p2.teleport(p2.getServerWorld(), p2Pos.x, p2Pos.y, p2Pos.z, yawP2, 0.0F);
            p1.setYaw(yawP1);
            p1.setBodyYaw(yawP1);
            p1.setHeadYaw(yawP1);
            p2.setYaw(yawP2);
            p2.setBodyYaw(yawP2);
            p2.setHeadYaw(yawP2);
        }
    }

    private static void teleportPerfectDapFacingEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2, double targetDistance) {
        Vec3d p1Pos = p1.getPos();
        Vec3d p2Pos = p2.getPos();
        double distance = p1Pos.distanceTo(p2Pos);
        Vec3d direction = p2Pos.subtract(p1Pos).normalize();
        Vec3d midpoint = p1Pos.add(p2Pos).multiply(0.5);
        double dx = p2Pos.x - p1Pos.x;
        double dz = p2Pos.z - p1Pos.z;
        float yawP1 = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float yawP2 = yawP1 + 180.0F;
        if (Math.abs(distance - targetDistance) > 0.05) {
            Vec3d offset = direction.multiply(targetDistance / 2.0);
            Vec3d targetP1 = midpoint.subtract(offset);
            Vec3d targetP2 = midpoint.add(offset);
            p1.teleport(p1.getServerWorld(), targetP1.x, targetP1.y, targetP1.z, yawP1, 0.0F);
            p2.teleport(p2.getServerWorld(), targetP2.x, targetP2.y, targetP2.z, yawP2, 0.0F);
            p1.setYaw(yawP1);
            p1.setBodyYaw(yawP1);
            p1.setHeadYaw(yawP1);
            p2.setYaw(yawP2);
            p2.setBodyYaw(yawP2);
            p2.setHeadYaw(yawP2);
        } else {
            p1.teleport(p1.getServerWorld(), p1Pos.x, p1Pos.y, p1Pos.z, yawP1, 0.0F);
            p2.teleport(p2.getServerWorld(), p2Pos.x, p2Pos.y, p2Pos.z, yawP2, 0.0F);
            p1.setYaw(yawP1);
            p1.setBodyYaw(yawP1);
            p1.setHeadYaw(yawP1);
            p2.setYaw(yawP2);
            p2.setBodyYaw(yawP2);
            p2.setHeadYaw(yawP2);
        }
    }

    private static void smoothDapDescent(ServerPlayerEntity player, ArmorStandEntity stand) {
        if (stand != null && !stand.isRemoved()) {
            if (!fireComboActive.getOrDefault(player.getUuid(), false)) {
                ServerWorld world = player.getServerWorld();
                Vec3d playerPos = player.getPos();
                Vec3d standPos = stand.getPos();
                double distanceToPlayer = standPos.distanceTo(playerPos);
                if (distanceToPlayer < 0.8) {
                    Vec3d direction = standPos.subtract(playerPos).normalize();
                    Vec3d newStandPos = playerPos.add(direction.multiply(1.2));
                    stand.setPosition(newStandPos.x, newStandPos.y, newStandPos.z);
                } else {
                    BlockPos feetPos = BlockPos.ofFloored(playerPos.x, playerPos.y - 0.1, playerPos.z);
                    boolean solidBlockBelow = !world.getBlockState(feetPos).isAir() && world.getBlockState(feetPos).isSolidBlock(world, feetPos);
                    if (!solidBlockBelow) {
                        BlockPos checkPos = BlockPos.ofFloored(playerPos.x, playerPos.y - 1.0, playerPos.z);

                        for (int blocksChecked = 0;
                            blocksChecked < 10 && (world.getBlockState(checkPos).isAir() || !world.getBlockState(checkPos).isSolidBlock(world, checkPos));
                            blocksChecked++
                        ) {
                            checkPos = checkPos.down();
                        }

                        double groundY = checkPos.getY() + 1.0;
                        if (!(playerPos.y <= groundY + 0.1)) {
                            double newY = playerPos.y - 0.12;
                            newY = Math.max(newY, groundY);
                            if (newY < playerPos.y && newY >= groundY) {
                                player.setPosition(playerPos.x, newY, playerPos.z);
                                stand.setPosition(standPos.x, newY + 1.4, standPos.z);
                            }
                        }
                    }
                }
            }
        }
    }

    public record ChargeReleasePayload() implements CustomPayload {
        public static final Id<ChargedDapHandler.ChargeReleasePayload> ID = new Id(ChargedDapHandler.CHARGE_RELEASE_ID);
        public static final PacketCodec<PacketByteBuf, ChargedDapHandler.ChargeReleasePayload> CODEC = PacketCodec.unit(
            new ChargedDapHandler.ChargeReleasePayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ChargeStartPayload() implements CustomPayload {
        public static final Id<ChargedDapHandler.ChargeStartPayload> ID = new Id(ChargedDapHandler.CHARGE_START_ID);
        public static final PacketCodec<PacketByteBuf, ChargedDapHandler.ChargeStartPayload> CODEC = PacketCodec.unit(
            new ChargedDapHandler.ChargeStartPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ChargeSyncPayload(UUID playerId, float chargePercent, float firePercent, boolean isCharging) implements CustomPayload {
        public static final Id<ChargedDapHandler.ChargeSyncPayload> ID = new Id(ChargedDapHandler.CHARGE_SYNC_ID);
        public static final PacketCodec<PacketByteBuf, ChargedDapHandler.ChargeSyncPayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeUuid(payload.playerId);
            buf.writeFloat(payload.chargePercent);
            buf.writeFloat(payload.firePercent);
            buf.writeBoolean(payload.isCharging);
        }, buf -> new ChargedDapHandler.ChargeSyncPayload(buf.readUuid(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record DapResultPayload(double x, double y, double z, UUID player1, UUID player2, int tier, boolean perfectHit) implements CustomPayload {
        public static final Id<ChargedDapHandler.DapResultPayload> ID = new Id(ChargedDapHandler.DAP_RESULT_ID);
        public static final PacketCodec<PacketByteBuf, ChargedDapHandler.DapResultPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeDouble(payload.x);
                buf.writeDouble(payload.y);
                buf.writeDouble(payload.z);
                buf.writeUuid(payload.player1);
                buf.writeUuid(payload.player2);
                buf.writeInt(payload.tier);
                buf.writeBoolean(payload.perfectHit);
            },
            buf -> new ChargedDapHandler.DapResultPayload(
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readUuid(), buf.readUuid(), buf.readInt(), buf.readBoolean()
            )
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FacingDapImpactPayload() implements CustomPayload {
        public static final Id<ChargedDapHandler.FacingDapImpactPayload> ID = new Id(ChargedDapHandler.FACING_DAP_IMPACT_ID);
        public static final PacketCodec<PacketByteBuf, ChargedDapHandler.FacingDapImpactPayload> CODEC = PacketCodec.unit(
            new ChargedDapHandler.FacingDapImpactPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static final class FireComboQTE {
        final UUID a;
        final UUID b;
        final long opensAt;
        final long closesAt;
        boolean pressedA;
        boolean pressedB;
        boolean burned;
        boolean done;

        FireComboQTE(UUID a, UUID b, long opensAt, long closesAt) {
            this.a = a;
            this.b = b;
            this.opensAt = opensAt;
            this.closesAt = closesAt;
        }
    }

    public record FireDapFirstPersonPayload(UUID playerId, boolean showBothHands) implements CustomPayload {
        public static final Id<ChargedDapHandler.FireDapFirstPersonPayload> ID = new Id(ChargedDapHandler.FIRE_DAP_FP_ID);
        public static final PacketCodec<PacketByteBuf, ChargedDapHandler.FireDapFirstPersonPayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeUuid(payload.playerId);
            buf.writeBoolean(payload.showBothHands);
        }, buf -> new ChargedDapHandler.FireDapFirstPersonPayload(buf.readUuid(), buf.readBoolean()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FireDapFreezePayload(UUID playerId, boolean frozen) implements CustomPayload {
        public static final Id<ChargedDapHandler.FireDapFreezePayload> ID = new Id(ChargedDapHandler.FIRE_DAP_FREEZE_ID);
        public static final PacketCodec<PacketByteBuf, ChargedDapHandler.FireDapFreezePayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeUuid(payload.playerId);
            buf.writeBoolean(payload.frozen);
        }, buf -> new ChargedDapHandler.FireDapFreezePayload(buf.readUuid(), buf.readBoolean()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FireDapJPressPayload() implements CustomPayload {
        public static final Id<ChargedDapHandler.FireDapJPressPayload> ID = new Id(ChargedDapHandler.FIRE_DAP_J_PRESS_ID);
        public static final PacketCodec<PacketByteBuf, ChargedDapHandler.FireDapJPressPayload> CODEC = PacketCodec.unit(
            new ChargedDapHandler.FireDapJPressPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static class FireDapScheduledEvent {
        final ServerPlayerEntity p1;
        final ServerPlayerEntity p2;
        final long executeTime;

        FireDapScheduledEvent(ServerPlayerEntity p1, ServerPlayerEntity p2, long executeTime) {
            this.p1 = p1;
            this.p2 = p2;
            this.executeTime = executeTime;
        }
    }

    public record FireDapWindowPayload(boolean open, int armedMs, int promptDelayMs) implements CustomPayload {
        public static final Id<ChargedDapHandler.FireDapWindowPayload> ID = new Id(ChargedDapHandler.FIRE_DAP_WINDOW_ID);
        public static final PacketCodec<PacketByteBuf, ChargedDapHandler.FireDapWindowPayload> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeBoolean(p.open);
            buf.writeInt(p.armedMs);
            buf.writeInt(p.promptDelayMs);
        }, buf -> new ChargedDapHandler.FireDapWindowPayload(buf.readBoolean(), buf.readInt(), buf.readInt()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    static class HeavenDapData {
        Vec3d originalMidpoint;
        ServerWorld world;
        long startTime;
        UUID partnerId;

        HeavenDapData(Vec3d originalMidpoint, ServerWorld world, long startTime, UUID partnerId) {
            this.originalMidpoint = originalMidpoint;
            this.world = world;
            this.startTime = startTime;
            this.partnerId = partnerId;
        }
    }

    private static class HeavenParticleSpawner {
        final ServerWorld world;
        final Vec3d pos;
        final long startTime;
        final long endTime;

        HeavenParticleSpawner(ServerWorld world, Vec3d pos, long durationMs) {
            this.world = world;
            this.pos = pos;
            this.startTime = System.currentTimeMillis();
            this.endTime = this.startTime + durationMs;
        }
    }

    public record HeavenReadyPayload(UUID playerId, boolean ready) implements CustomPayload {
        public static final Id<ChargedDapHandler.HeavenReadyPayload> ID = new Id(new Identifier("cooptest", "heaven_ready"));
        public static final PacketCodec<PacketByteBuf, ChargedDapHandler.HeavenReadyPayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeUuid(payload.playerId);
            buf.writeBoolean(payload.ready);
        }, buf -> new ChargedDapHandler.HeavenReadyPayload(buf.readUuid(), buf.readBoolean()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ImpactFramePayload(int durationMs, boolean grayscale) implements CustomPayload {
        public static final Id<ChargedDapHandler.ImpactFramePayload> ID = new Id(ChargedDapHandler.IMPACT_FRAME_ID);
        public static final PacketCodec<PacketByteBuf, ChargedDapHandler.ImpactFramePayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeInt(payload.durationMs);
            buf.writeBoolean(payload.grayscale);
        }, buf -> new ChargedDapHandler.ImpactFramePayload(buf.readInt(), buf.readBoolean()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PerfectDapFreezePayload(boolean frozen) implements CustomPayload {
        public static final Id<ChargedDapHandler.PerfectDapFreezePayload> ID = new Id(ChargedDapHandler.PERFECT_DAP_FREEZE_ID);
        public static final PacketCodec<PacketByteBuf, ChargedDapHandler.PerfectDapFreezePayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeBoolean(payload.frozen), buf -> new ChargedDapHandler.PerfectDapFreezePayload(buf.readBoolean())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PerfectDapImpactFramePayload(int frameIndex) implements CustomPayload {
        public static final Id<ChargedDapHandler.PerfectDapImpactFramePayload> ID = new Id(ChargedDapHandler.PERFECT_DAP_IMPACT_FRAME_ID);
        public static final PacketCodec<PacketByteBuf, ChargedDapHandler.PerfectDapImpactFramePayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeInt(payload.frameIndex), buf -> new ChargedDapHandler.PerfectDapImpactFramePayload(buf.readInt())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static class SaturnRing {
        final Vec3d center;
        final long startTime;
        final long endTime;

        SaturnRing(Vec3d center, long startTime) {
            this.center = center;
            this.startTime = startTime;
            this.endTime = startTime + 20000L;
        }
    }

    private record ScheduledParticles(ServerWorld world, double x, double y, double z, long spawnTime) {
    }

    private record ScheduledPerfectDapEffect(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2, long effectTime) {
    }

    private static class TornadoSwirlEntity {
        final Vec3d startCenter;
        final ServerWorld world;
        double angle;
        double height;
        double radius;
        int age;

        TornadoSwirlEntity(ServerWorld world, Vec3d center, double startAngle, double startRadius) {
            this.world = world;
            this.startCenter = center;
            this.angle = startAngle;
            this.height = 0.0;
            this.radius = startRadius;
            this.age = 0;
        }

        boolean tick() {
            this.height += 0.2;
            this.angle += 18.0;
            if (this.height < 30.0) {
                this.radius += 0.08;
            } else {
                this.radius -= 0.05;
            }

            double x = this.startCenter.x + Math.cos(Math.toRadians(this.angle)) * this.radius;
            double z = this.startCenter.z + Math.sin(Math.toRadians(this.angle)) * this.radius;
            double y = this.startCenter.y + this.height;
            this.world.spawnParticles(ParticleTypes.FLAME, x, y, z, 8, 0.3, 0.3, 0.3, 0.08);
            this.world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 4, 0.2, 0.2, 0.2, 0.05);
            if (this.height % 5.0 < 0.5) {
                this.world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 6, 0.4, 0.4, 0.4, 0.06);
                this.world.spawnParticles(ParticleTypes.LAVA, x, y, z, 3, 0.2, 0.2, 0.2, 0.02);
            }

            this.age++;
            return this.height < 60.0 && this.age < 60;
        }
    }

    public record WhiffCooldownPayload(long cooldownDurationMs) implements CustomPayload {
        public static final Id<ChargedDapHandler.WhiffCooldownPayload> ID = new Id(ChargedDapHandler.WHIFF_COOLDOWN_ID);
        public static final PacketCodec<PacketByteBuf, ChargedDapHandler.WhiffCooldownPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeLong(payload.cooldownDurationMs), buf -> new ChargedDapHandler.WhiffCooldownPayload(buf.readLong())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
