package com.cooptest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.player.UseItemCallback;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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

public class StrongSlapHandler {
    private static final double RANGE = 2.5;
    private static final double AIM_DOT = 0.78;
    private static final double BACK_DOT = 0.65;
    private static final double SNAP_DIST = 0.8;
    private static final int TP_TICKS = 5;
    private static final long START_ANIM_MS = 958L;
    private static final long CHARGE_LOOP_MS = 5708L;
    private static final long STAGE_GAP_MS = 250L;
    private static final long TOTAL_STAGES = 6L;
    private static final long CHARGE_SWING_1_MS = 830L;
    private static final long CHARGE_SWING_2_MS = 3790L;
    private static final long FIRE_BEGIN_MS = 2667L;
    private static final long EXPLOSION_MS = 4083L;
    private static final long IMPACT_MS = 4200L;
    private static final long GREEN_START = 600L;
    private static final long GREEN_END = 1050L;
    private static final long NECK_BROKEN_MS = 180000L;
    public static final int ANIM_STRONG_START = 91;
    public static final int ANIM_STRONG_CHARGE = 92;
    public static final int ANIM_STRONG_HIT = 93;
    public static final int ANIM_NONE = 0;
    private static final Map<UUID, StrongSlapHandler.SlapSession> sessions = new HashMap<>();
    private static final Map<UUID, UUID> victimToAtk = new HashMap<>();
    private static final Map<UUID, Long> neckBroken = new HashMap<>();
    private static final Map<UUID, Long> lastTortureMs = new HashMap<>();
    private static final Map<UUID, Long> lastResistDamageMs = new HashMap<>();
    private static final Random TORTURE_RNG = new Random();
    private static final long TORTURE_INTERVAL_MS = 2000L;
    private static final long RESIST_DAMAGE_COOLDOWN_MS = 600L;

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(StrongSlapHandler.NeckBrokenPayload.ID, StrongSlapHandler.NeckBrokenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StrongSlapHandler.MoveFreezePayload.ID, StrongSlapHandler.MoveFreezePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StrongSlapHandler.TortureTickPayload.ID, StrongSlapHandler.TortureTickPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StrongSlapHandler.LookLockPayload.ID, StrongSlapHandler.LookLockPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StrongSlapHandler.NeckResistPayload.ID, StrongSlapHandler.NeckResistPayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(StrongSlapHandler.NeckResistPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity victim = ctx.player();
            UUID id = victim.getUuid();
            if (neckBroken.containsKey(id)) {
                long now = System.currentTimeMillis();
                if (now - lastResistDamageMs.getOrDefault(id, 0L) >= 600L) {
                    lastResistDamageMs.put(id, now);
                    victim.damage(victim.getServerWorld().getDamageSources().generic(), 1.0F);
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(StrongSlapHandler::tick);
        ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> {
            UUID id = handler.getPlayer().getUuid();
            Long expiry = neckBroken.get(id);
            if (expiry != null) {
                long remaining = expiry - System.currentTimeMillis();
                if (remaining > 0L) {
                    ServerPlayNetworking.send(handler.getPlayer(), new StrongSlapHandler.NeckBrokenPayload(id, remaining));
                } else {
                    neckBroken.remove(id);
                }
            }
        });
        UseItemCallback.EVENT.register((UseItemCallback)(player, world, hand) -> {
            if (world.isClient) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            } else {
                ItemStack stack = player.getStackInHand(hand);
                boolean isGoldenApple = stack.isOf(Items.field_8463) || stack.isOf(Items.field_8367);
                if (isGoldenApple && neckBroken.containsKey(player.getUuid())) {
                    cureNeckBroken((ServerPlayerEntity)player);
                }

                return TypedActionResult.pass(player.getStackInHand(hand));
            }
        });
    }

    public static boolean tryStrongSlap(ServerPlayerEntity attacker, long chargeDurationMs) {
        if (chargeDurationMs < 300L) {
            return false;
        } else if (sessions.containsKey(attacker.getUuid())) {
            return false;
        } else {
            ServerPlayerEntity victim = findBackVictim(attacker);
            if (victim == null) {
                return false;
            } else if (victimToAtk.containsKey(victim.getUuid())) {
                return false;
            } else {
                Vec3d victimFwd = victim.getRotationVec(1.0F);
                Vec3d victimFwdH = new Vec3d(victimFwd.x, 0.0, victimFwd.z);
                if (victimFwdH.lengthSquared() > 0.001) {
                    victimFwdH = victimFwdH.normalize();
                } else {
                    victimFwdH = new Vec3d(1.0, 0.0, 0.0);
                }

                Vec3d tpTarget = victim.getPos().subtract(victimFwdH.multiply(0.8));
                StrongSlapHandler.SlapSession session = new StrongSlapHandler.SlapSession(
                    attacker.getUuid(), victim.getUuid(), attacker.getPos(), tpTarget
                );
                sessions.put(attacker.getUuid(), session);
                victimToAtk.put(victim.getUuid(), attacker.getUuid());
                attacker.swingHand(Hand.field_5808, true);
                sendFreeze(attacker, true);
                if (!CoopMovesConfig.get().enableStrongSlapSmoothTp) {
                    session.tpTick = 5;
                    Vec3d diff = victim.getPos().subtract(attacker.getPos());
                    float yaw = (float)Math.toDegrees(Math.atan2(-diff.x, diff.z));
                    attacker.setYaw(yaw);
                    attacker.setBodyYaw(yaw);
                    attacker.setHeadYaw(yaw);
                    sendLookLock(attacker, true);
                    sendLookLock(victim, true);
                    PoseNetworking.broadcastAnimState(attacker, 91);
                    new Thread(() -> {
                        try {
                            Thread.sleep(958L);
                        } catch (InterruptedException var3x) {
                        }

                        attacker.getServer().execute(() -> {
                            if (!session.cancelled) {
                                ServerPlayerEntity a2 = attacker.getServer().getPlayerManager().getPlayer(session.attackerId);
                                if (a2 != null) {
                                    session.chargeAnimStartMs = System.currentTimeMillis();
                                    PoseNetworking.broadcastAnimState(a2, 92);
                                    openQTEStage(a2, session);
                                    scheduleChargeSounds(attacker.getServer(), session);
                                }
                            }
                        });
                    }).start();
                }

                return true;
            }
        }
    }

    public static boolean onButtonPress(ServerPlayerEntity presser, String button) {
        StrongSlapHandler.SlapSession session = sessions.get(presser.getUuid());
        if (session != null && session.qteOpen) {
            session.qteOpen = false;
            if (!button.equals("G")) {
                cancelSession(presser.getServer(), session, "§c✗ Wrong button — slap cancelled!");
                return true;
            } else {
                session.currentStage++;
                if (session.currentStage >= 6L) {
                    launchHitAnim(presser, session);
                } else {
                    new Thread(() -> {
                        try {
                            Thread.sleep(250L);
                        } catch (InterruptedException var3) {
                        }

                        presser.getServer().execute(() -> {
                            if (!session.cancelled) {
                                ServerPlayerEntity atk = presser.getServer().getPlayerManager().getPlayer(session.attackerId);
                                if (atk != null) {
                                    openQTEStage(atk, session);
                                } else {
                                    cancelSession(presser.getServer(), session, null);
                                }
                            }
                        });
                    }).start();
                }

                return true;
            }
        } else {
            return false;
        }
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        List<UUID> toRemove = new ArrayList<>();

        for (Entry<UUID, StrongSlapHandler.SlapSession> entry : sessions.entrySet()) {
            StrongSlapHandler.SlapSession s = entry.getValue();
            if (s.cancelled) {
                toRemove.add(entry.getKey());
            } else {
                ServerPlayerEntity atk = server.getPlayerManager().getPlayer(s.attackerId);
                ServerPlayerEntity vic = server.getPlayerManager().getPlayer(s.victimId);
                if (atk != null && vic != null) {
                    atk.setVelocity(Vec3d.ZERO);
                    atk.velocityModified = true;
                    if (s.tpTick < 5) {
                        s.tpTick++;
                        float frac = s.tpTick / 5.0F;
                        Vec3d cur = s.tpStart;
                        Vec3d tgt = s.tpTarget;
                        double nx = cur.x + (tgt.x - cur.x) * frac;
                        double ny = cur.y + (tgt.y - cur.y) * frac;
                        double nz = cur.z + (tgt.z - cur.z) * frac;
                        atk.requestTeleport(nx, ny, nz);
                        if (s.tpTick == 5) {
                            Vec3d diff = vic.getPos().subtract(atk.getPos());
                            float yaw = (float)Math.toDegrees(Math.atan2(-diff.x, diff.z));
                            atk.setYaw(yaw);
                            atk.setBodyYaw(yaw);
                            atk.setHeadYaw(yaw);
                            sendLookLock(atk, true);
                            sendLookLock(vic, true);
                            PoseNetworking.broadcastAnimState(atk, 91);
                            new Thread(() -> {
                                try {
                                    Thread.sleep(958L);
                                } catch (InterruptedException var3x) {
                                }

                                server.execute(() -> {
                                    if (!s.cancelled) {
                                        ServerPlayerEntity a2 = server.getPlayerManager().getPlayer(s.attackerId);
                                        if (a2 != null) {
                                            s.chargeAnimStartMs = System.currentTimeMillis();
                                            PoseNetworking.broadcastAnimState(a2, 92);
                                            openQTEStage(a2, s);
                                            scheduleChargeSounds(server, s);
                                        }
                                    }
                                });
                            }).start();
                        }
                    }

                    if (s.qteOpen && now - s.stageStart > 2600L) {
                        cancelSession(server, s, "§c✗ QTE timed out!");
                        toRemove.add(entry.getKey());
                    }
                } else {
                    if (s.victimId != null) {
                        victimToAtk.remove(s.victimId);
                    }

                    toRemove.add(entry.getKey());
                }
            }
        }

        toRemove.forEach(idx -> {
            StrongSlapHandler.SlapSession s = sessions.remove(idx);
            if (s != null) {
                victimToAtk.remove(s.victimId);
            }
        });
        List<UUID> cured = new ArrayList<>();

        for (Entry<UUID, Long> entryx : neckBroken.entrySet()) {
            UUID id = entryx.getKey();
            if (now >= entryx.getValue()) {
                cured.add(id);
            } else {
                Long last = lastTortureMs.getOrDefault(id, 0L);
                if (now - last >= 2000L) {
                    lastTortureMs.put(id, now);
                    ServerPlayerEntity victim = server.getPlayerManager().getPlayer(id);
                    if (victim != null) {
                        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5916, 60, 1, false, false));
                        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5909, 50, 3, false, false));
                        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.field_38092, 60, 0, false, false));
                        int r = 100 + TORTURE_RNG.nextInt(155);
                        int g = TORTURE_RNG.nextInt(200);
                        int b = 100 + TORTURE_RNG.nextInt(155);
                        int color = r << 16 | g << 8 | b;
                        ServerPlayNetworking.send(victim, new StrongSlapHandler.TortureTickPayload(id, color));
                    }
                }
            }
        }

        cured.forEach(idx -> {
            neckBroken.remove(idx);
            lastTortureMs.remove(idx);
            lastResistDamageMs.remove(idx);
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(idx);
            if (p != null) {
                sendLookLock(p, false);
                ServerPlayNetworking.send(p, new StrongSlapHandler.NeckBrokenPayload(idx, 0L));
            }
        });
    }

    private static void openQTEStage(ServerPlayerEntity attacker, StrongSlapHandler.SlapSession session) {
        session.qteOpen = true;
        session.stageStart = System.currentTimeMillis();
        DapFusionHandler.FusionQTEPayload qte = new DapFusionHandler.FusionQTEPayload(attacker.getUuid(), "G", session.currentStage, 600L, 1050L, true, 1);
        ServerPlayNetworking.send(attacker, qte);
    }

    private static void launchHitAnim(ServerPlayerEntity attacker, StrongSlapHandler.SlapSession session) {
        session.hitAnimStarted = true;
        long elapsed = System.currentTimeMillis() - session.chargeAnimStartMs;
        long posInCycle = elapsed % 5708L;
        long waitMs = 5708L - posInCycle;
        UUID atkId = attacker.getUuid();
        UUID vicId = session.victimId;
        new Thread(
                () -> {
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException var7) {
                    }

                    attacker.getServer()
                        .execute(
                            () -> {
                                if (!session.cancelled) {
                                    ServerPlayerEntity atk = attacker.getServer().getPlayerManager().getPlayer(atkId);
                                    if (atk != null) {
                                        PoseNetworking.broadcastAnimState(atk, 93);
                                        new Thread(() -> {
                                            try {
                                                Thread.sleep(2667L);
                                            } catch (InterruptedException var4x) {
                                            }

                                            if (!session.cancelled) {
                                                atk.getServer().execute(() -> spawnFireBuildup(atk, vicId));
                                            }
                                        }).start();
                                        new Thread(
                                                () -> {
                                                    try {
                                                        Thread.sleep(4083L);
                                                    } catch (InterruptedException var4x) {
                                                    }

                                                    if (!session.cancelled) {
                                                        atk.getServer()
                                                            .execute(
                                                                () -> {
                                                                    spawnBigExplosion(atk, vicId);
                                                                    ServerPlayerEntity victim = atk.getServer().getPlayerManager().getPlayer(vicId);
                                                                    Vec3d pos = victim != null
                                                                        ? victim.getPos().add(0.0, 1.5, 0.0)
                                                                        : atk.getPos();
                                                                    atk.getServerWorld()
                                                                        .playSound(
                                                                            null,
                                                                            pos.x,
                                                                            pos.y,
                                                                            pos.z,
                                                                            ModSounds.FAHH,
                                                                            SoundCategory.field_15248,
                                                                            4.0F,
                                                                            1.0F
                                                                        );
                                                                }
                                                            );
                                                    }
                                                }
                                            )
                                            .start();
                                        new Thread(() -> {
                                            try {
                                                Thread.sleep(4200L);
                                            } catch (InterruptedException var5) {
                                            }

                                            if (!session.cancelled) {
                                                atk.getServer().execute(() -> applyVictimEffects(atk.getServer(), atkId, vicId));
                                            }
                                        }).start();
                                    }
                                }
                            }
                        );
                }
            )
            .start();
    }

    private static void scheduleChargeSounds(MinecraftServer server, StrongSlapHandler.SlapSession session) {
        new Thread(() -> {
            while (!session.hitAnimStarted && !session.cancelled) {
                try {
                    Thread.sleep(830L);
                } catch (InterruptedException var5) {
                    return;
                }

                if (!session.hitAnimStarted && !session.cancelled) {
                    server.execute(() -> playSwingSound(server, session));

                    try {
                        Thread.sleep(2960L);
                    } catch (InterruptedException var4) {
                        return;
                    }

                    if (!session.hitAnimStarted && !session.cancelled) {
                        server.execute(() -> playSwingSound(server, session));

                        try {
                            Thread.sleep(1918L);
                            continue;
                        } catch (InterruptedException var3) {
                            return;
                        }
                    }

                    return;
                }

                return;
            }
        }).start();
    }

    private static void playSwingSound(MinecraftServer server, StrongSlapHandler.SlapSession session) {
        if (!session.cancelled) {
            ServerPlayerEntity atk = server.getPlayerManager().getPlayer(session.attackerId);
            if (atk != null) {
                Vec3d pos = atk.getPos();
                atk.getServerWorld()
                    .playSound(null, pos.x, pos.y, pos.z, SoundEvents.field_14840, SoundCategory.field_15248, 1.0F, 1.0F);
            }
        }
    }

    private static void spawnFireBuildup(ServerPlayerEntity attacker, UUID vicId) {
        ServerWorld world = attacker.getServerWorld();
        Vec3d look = attacker.getRotationVec(1.0F);
        Vec3d right = new Vec3d(-look.z, 0.0, look.x).normalize();

        for (int i = 0; i < 5; i++) {
            int burst = i;
            new Thread(
                    () -> {
                        try {
                            Thread.sleep(burst * 280L);
                        } catch (InterruptedException var4x) {
                        }

                        attacker.getServer()
                            .execute(
                                () -> {
                                    Vec3d l = attacker.getRotationVec(1.0F);
                                    Vec3d r = new Vec3d(-l.z, 0.0, l.x).normalize();
                                    Vec3d tip = attacker.getEyePos().add(l.multiply(0.7)).add(r.multiply(0.45));
                                    world.spawnParticles(ParticleTypes.field_11240, tip.x, tip.y, tip.z, 6, 0.1, 0.1, 0.1, 0.04);
                                    world.spawnParticles(ParticleTypes.field_11239, tip.x, tip.y, tip.z, 2, 0.05, 0.05, 0.05, 0.0);
                                    world.playSound(
                                        null,
                                        tip.x,
                                        tip.y,
                                        tip.z,
                                        SoundEvents.field_14970,
                                        SoundCategory.field_15248,
                                        0.4F,
                                        1.4F + burst * 0.1F
                                    );
                                }
                            );
                    }
                )
                .start();
        }
    }

    private static void spawnBigExplosion(ServerPlayerEntity attacker, UUID vicId) {
        ServerWorld world = attacker.getServerWorld();
        ServerPlayerEntity victim = attacker.getServer().getPlayerManager().getPlayer(vicId);
        if (victim != null) {
            Vec3d hitPos = victim.getPos().add(0.0, 1.6, 0.0);
            world.spawnParticles(ParticleTypes.field_11221, hitPos.x, hitPos.y, hitPos.z, 3, 0.2, 0.2, 0.2, 0.0);
            world.spawnParticles(ParticleTypes.field_11240, hitPos.x, hitPos.y, hitPos.z, 40, 0.3, 0.3, 0.3, 0.12);
            world.spawnParticles(ParticleTypes.field_11239, hitPos.x, hitPos.y, hitPos.z, 20, 0.2, 0.2, 0.2, 0.0);
            world.spawnParticles(ParticleTypes.field_11205, hitPos.x, hitPos.y, hitPos.z, 25, 0.3, 0.3, 0.3, 0.2);
            world.spawnParticles(ParticleTypes.field_11208, hitPos.x, hitPos.y, hitPos.z, 20, 0.2, 0.2, 0.2, 0.1);
            world.spawnParticles(ParticleTypes.field_11227, hitPos.x, hitPos.y, hitPos.z, 8, 0.15, 0.1, 0.15, 0.05);
            world.spawnParticles(ParticleTypes.field_22246, hitPos.x, hitPos.y, hitPos.z, 15, 0.25, 0.25, 0.25, 0.08);
            world.playSound(null, hitPos.x, hitPos.y, hitPos.z, ModSounds.SLAP, SoundCategory.field_15248, 2.0F, 0.5F);
            world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.field_15152, SoundCategory.field_15248, 1.5F, 0.8F);
            world.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.field_15016, SoundCategory.field_15248, 1.2F, 0.4F);
        }
    }

    private static void applyVictimEffects(MinecraftServer server, UUID atkId, UUID vicId) {
        ServerPlayerEntity victim = server.getPlayerManager().getPlayer(vicId);
        ServerPlayerEntity attacker = server.getPlayerManager().getPlayer(atkId);
        if (victim != null) {
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5909, 100, 7, false, false));
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5919, 60, 0, false, false));
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5916, 3600, 1, false, false));
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.field_38092, 100, 0, false, false));
            ServerPlayNetworking.send(victim, new SlapHandler.ScreenClosePayload(vicId));
            long expiry = System.currentTimeMillis() + 180000L;
            neckBroken.put(vicId, expiry);
            ServerPlayNetworking.send(victim, new StrongSlapHandler.NeckBrokenPayload(vicId, 180000L));
            new Thread(() -> {
                try {
                    Thread.sleep(800L);
                } catch (InterruptedException var3x) {
                }

                server.execute(() -> {
                    ServerPlayerEntity a = server.getPlayerManager().getPlayer(atkId);
                    if (a != null) {
                        sendFreeze(a, false);
                        sendLookLock(a, false);
                        PoseNetworking.broadcastAnimState(a, 0);
                        StrongSlapCommand.disarm(atkId);
                    }
                });
            }).start();
            sessions.remove(atkId);
            victimToAtk.remove(vicId);
        }
    }

    private static void cancelSession(MinecraftServer server, StrongSlapHandler.SlapSession session, String reason) {
        if (!session.cancelled) {
            session.cancelled = true;
            ServerPlayerEntity atk = server.getPlayerManager().getPlayer(session.attackerId);
            ServerPlayerEntity vic = server.getPlayerManager().getPlayer(session.victimId);
            if (atk != null) {
                PoseNetworking.broadcastAnimState(atk, 0);
                sendFreeze(atk, false);
                sendLookLock(atk, false);
                if (reason != null) {
                    atk.sendMessage(Text.literal(reason), true);
                }
            }

            if (vic != null) {
                sendFreeze(vic, false);
                sendLookLock(vic, false);
            }

            sessions.remove(session.attackerId);
            victimToAtk.remove(session.victimId);
        }
    }

    private static void cureNeckBroken(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        neckBroken.remove(id);
        lastTortureMs.remove(id);
        lastResistDamageMs.remove(id);
        sendFreeze(player, false);
        sendLookLock(player, false);
        ServerPlayNetworking.send(player, new StrongSlapHandler.NeckBrokenPayload(id, 0L));
    }

    public static void onPlayerLeave(UUID id) {
        StrongSlapHandler.SlapSession s = sessions.remove(id);
        if (s != null) {
            victimToAtk.remove(s.victimId);
            s.cancelled = true;
        }

        UUID atkId = victimToAtk.remove(id);
        if (atkId != null) {
            StrongSlapHandler.SlapSession as = sessions.remove(atkId);
            if (as != null) {
                as.cancelled = true;
            }
        }

        lastResistDamageMs.remove(id);
    }

    private static void sendFreeze(ServerPlayerEntity player, boolean frozen) {
        ServerPlayNetworking.send(player, new StrongSlapHandler.MoveFreezePayload(player.getUuid(), frozen));
    }

    private static void sendLookLock(ServerPlayerEntity player, boolean locked) {
        float yaw = locked ? player.getYaw() : 0.0F;
        ServerPlayNetworking.send(player, new StrongSlapHandler.LookLockPayload(player.getUuid(), locked, yaw));
    }

    public static void sendLookLockPublic(ServerPlayerEntity player, boolean locked) {
        sendLookLock(player, locked);
    }

    public static void sendLookLockAtYaw(ServerPlayerEntity player, float targetYaw) {
        ServerPlayNetworking.send(player, new StrongSlapHandler.LookLockPayload(player.getUuid(), true, targetYaw));
    }

    private static ServerPlayerEntity findBackVictim(ServerPlayerEntity attacker) {
        Vec3d eye = attacker.getEyePos();
        Vec3d look = attacker.getRotationVec(1.0F);
        ServerPlayerEntity best = null;
        double closest = 2.501;

        for (ServerPlayerEntity p : attacker.getServerWorld().getPlayers()) {
            if (!p.equals(attacker)) {
                double dist = attacker.getPos().distanceTo(p.getPos());
                if (!(dist >= closest)) {
                    double lookDot = look.dotProduct(p.getRotationVec(1.0F));
                    if (!(lookDot < 0.65)) {
                        Vec3d toHead = p.getPos().add(0.0, 1.6, 0.0).subtract(eye);
                        if (!(toHead.length() < 0.01) && !(toHead.normalize().dotProduct(look) < 0.78)) {
                            best = p;
                            closest = dist;
                        }
                    }
                }
            }
        }

        return best;
    }

    public static boolean isAttackerInSession(UUID id) {
        return sessions.containsKey(id);
    }

    public static boolean isVictimInSession(UUID id) {
        return victimToAtk.containsKey(id);
    }

    public record LookLockPayload(UUID playerId, boolean locked, float targetYaw) implements CustomPayload {
        public static final Id<StrongSlapHandler.LookLockPayload> ID = new Id(new Identifier("testcoop", "look_lock"));
        public static final PacketCodec<PacketByteBuf, StrongSlapHandler.LookLockPayload> CODEC = PacketCodec.of((v, buf) -> {
            buf.writeUuid(v.playerId());
            buf.writeBoolean(v.locked());
            buf.writeFloat(v.targetYaw());
        }, buf -> new StrongSlapHandler.LookLockPayload(buf.readUuid(), buf.readBoolean(), buf.readFloat()));

        public Id<StrongSlapHandler.LookLockPayload> getId() {
            return ID;
        }
    }

    public record MoveFreezePayload(UUID playerId, boolean frozen) implements CustomPayload {
        public static final Id<StrongSlapHandler.MoveFreezePayload> ID = new Id(new Identifier("testcoop", "strong_slap_freeze"));
        public static final PacketCodec<PacketByteBuf, StrongSlapHandler.MoveFreezePayload> CODEC = PacketCodec.of((v, buf) -> {
            buf.writeUuid(v.playerId());
            buf.writeBoolean(v.frozen());
        }, buf -> new StrongSlapHandler.MoveFreezePayload(buf.readUuid(), buf.readBoolean()));

        public Id<StrongSlapHandler.MoveFreezePayload> getId() {
            return ID;
        }
    }

    public record NeckBrokenPayload(UUID playerId, long durationMs) implements CustomPayload {
        public static final Id<StrongSlapHandler.NeckBrokenPayload> ID = new Id(new Identifier("testcoop", "neck_broken"));
        public static final PacketCodec<PacketByteBuf, StrongSlapHandler.NeckBrokenPayload> CODEC = PacketCodec.of((v, buf) -> {
            buf.writeUuid(v.playerId());
            buf.writeLong(v.durationMs());
        }, buf -> new StrongSlapHandler.NeckBrokenPayload(buf.readUuid(), buf.readLong()));

        public Id<StrongSlapHandler.NeckBrokenPayload> getId() {
            return ID;
        }
    }

    public record NeckResistPayload(UUID playerId) implements CustomPayload {
        public static final Id<StrongSlapHandler.NeckResistPayload> ID = new Id(new Identifier("testcoop", "neck_resist"));
        public static final PacketCodec<PacketByteBuf, StrongSlapHandler.NeckResistPayload> CODEC = PacketCodec.of(
            (v, buf) -> buf.writeUuid(v.playerId()), buf -> new StrongSlapHandler.NeckResistPayload(buf.readUuid())
        );

        public Id<StrongSlapHandler.NeckResistPayload> getId() {
            return ID;
        }
    }

    private static class SlapSession {
        final UUID attackerId;
        final UUID victimId;
        Vec3d tpStart;
        Vec3d tpTarget;
        int tpTick = 0;
        int currentStage = 0;
        boolean qteOpen = false;
        long stageStart = 0L;
        long chargeAnimStartMs = 0L;
        boolean hitAnimStarted = false;
        boolean cancelled = false;

        SlapSession(UUID atk, UUID vic, Vec3d tpStart, Vec3d tpTarget) {
            this.attackerId = atk;
            this.victimId = vic;
            this.tpStart = tpStart;
            this.tpTarget = tpTarget;
        }
    }

    public record TortureTickPayload(UUID playerId, int randomColor) implements CustomPayload {
        public static final Id<StrongSlapHandler.TortureTickPayload> ID = new Id(new Identifier("testcoop", "torture_tick"));
        public static final PacketCodec<PacketByteBuf, StrongSlapHandler.TortureTickPayload> CODEC = PacketCodec.of((v, buf) -> {
            buf.writeUuid(v.playerId());
            buf.writeInt(v.randomColor());
        }, buf -> new StrongSlapHandler.TortureTickPayload(buf.readUuid(), buf.readInt()));

        public Id<StrongSlapHandler.TortureTickPayload> getId() {
            return ID;
        }
    }
}
