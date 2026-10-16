package com.cooptest.spin;

import com.cooptest.GrabMechanic;
import com.cooptest.HighFiveHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AfterPlayerChange;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDamage;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.PlayerLookup;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.minecraft.util.Hand;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class HandSpinHandler {
    public static final long HOLD_REQUIRED_MS = 1000L;
    public static final double SPIN_RADIUS = 0.75;
    public static final double SPIN_MAX_ANGULAR_VEL = 0.2;
    public static final double SPIN_ACCEL = 0.0037;
    public static final double SPIN_DECEL_FAST = 0.006;
    public static final double SPIN_DECEL_SLOW = 0.0015;
    public static final double SPIN_RANGE_TO_START = 2.0;
    public static final long SIMULTANEOUS_RELEASE_WINDOW_MS = 350L;
    public static final double STRENGTH_MAX_ANGULAR_VEL = 0.26;
    public static final double STRENGTH_ACCEL = 0.005;
    public static final double STRENGTH_LIFT_BLOCKS = 0.5;
    public static final int STRENGTH_LIFT_TICKS = 18;
    public static final long STRENGTH_AUTO_YEET_MS = 10000L;
    public static final long MONKE_ANIM_MS = 625L;
    public static final double STRENGTH_LAUNCH_STRENGTH = 2.6;
    public static final double STRENGTH_LAUNCH_UP = 0.7;
    public static final int STRENGTH_LAND_GRACE_TICKS = 4;
    public static final int STRENGTH_MAX_FLIGHT_TICKS = 200;
    public static final long MAX_SPIN_MS = 300000L;
    public static final int TERMINAL_STAGE_WATCHDOG_TICKS = 200;
    public static final int SYNC_INTERVAL_TICKS = 3;
    public static final double PUSH_APART_STRENGTH = 0.35;
    public static final double PUSH_APART_SLIDE_BLOCKS = 0.6;
    public static final long PUSH_APART_SLIDE_TICKS = 6L;
    public static final double LAUNCH_STRENGTH_MAX = 1.15;
    public static final double LAUNCH_STRENGTH_MIN = 0.45;
    public static final double LAUNCH_UP = 0.32;
    public static final double LAUNCH_SLIDE_BLOCKS = 1.0;
    public static final long LAUNCH_SLIDE_TICKS = 5L;
    public static final double MAX_REPOSITION_SNAP = 3.0;
    public static final double FACING_DOT_THRESHOLD = 0.0;
    public static final int HAND_SWING_INTERVAL_TICKS = 4;
    public static final float DAMAGE_CANCEL_THRESHOLD = 4.0F;
    public static final double DAMAGE_KNOCKBACK_STRENGTH = 0.75;
    public static final float SPIN_SHAKE_AMOUNT = 0.12F;
    public static final long SPIN_SHAKE_REFRESH_MS = 150L;
    public static final long SPIN_SHAKE_PULSE_MS = 200L;
    public static final int ANIM_NONE = 0;
    public static final int ANIM_HAND_SPIN_START = 120;
    public static final int ANIM_HAND_SPIN_IDLE = 121;
    public static final int ANIM_HAND_SPIN_END = 122;
    public static final int ANIM_MONKE = 123;
    public static final int ANIM_MONKE_IDLE = 124;
    private static final long HAND_SPIN_START_MS = 334L;
    private static final long HAND_SPIN_END_MS = 584L;
    private static final Map<UUID, HandSpinHandler.Session> sessionsByKey = new HashMap<>();
    private static final Map<UUID, UUID> playerToKey = new HashMap<>();
    private static final Map<UUID, Long> readyFHoldStart = new HashMap<>();
    private static final Set<UUID> fHeld = new HashSet<>();

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(HandSpinHandler.HandSpinStartPayload.ID, HandSpinHandler.HandSpinStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HandSpinHandler.HandSpinStopPayload.ID, HandSpinHandler.HandSpinStopPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HandSpinHandler.HandSpinShakePulsePayload.ID, HandSpinHandler.HandSpinShakePulsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HandSpinHandler.HandSpinStrengthPayload.ID, HandSpinHandler.HandSpinStrengthPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HandSpinHandler.HandSpinSyncPayload.ID, HandSpinHandler.HandSpinSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HandSpinHandler.HandSpinObserverPayload.ID, HandSpinHandler.HandSpinObserverPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HandSpinHandler.HandSpinMonkeFlyPayload.ID, HandSpinHandler.HandSpinMonkeFlyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HandSpinHandler.HandSpinFHoldPayload.ID, HandSpinHandler.HandSpinFHoldPayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(HandSpinHandler.HandSpinFHoldPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            boolean holding = payload.holding();
            ctx.server().execute(() -> onFHold(player, holding));
        });
        ServerTickEvents.END_SERVER_TICK.register(HandSpinHandler::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((AllowDamage)(entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity victim) {
                if (amount < 4.0F) {
                    return true;
                } else {
                    UUID id = victim.getUuid();
                    UUID key = playerToKey.get(id);
                    if (key == null) {
                        return true;
                    } else {
                        HandSpinHandler.Session s = sessionsByKey.get(key);
                        if (s != null && !s.branchLocked) {
                            lockBranchDamage(s);
                            return true;
                        } else {
                            return true;
                        }
                    }
                }
            } else {
                return true;
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            if (player != null) {
                cleanup(player.getUuid(), server);
            }
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD
            .register((AfterPlayerChange)(player, origin, destination) -> cleanup(player.getUuid(), player.getServer()));
        ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> clearAll());
    }

    public static void clearAll() {
        sessionsByKey.clear();
        playerToKey.clear();
        readyFHoldStart.clear();
        fHeld.clear();
    }

    private static void onFHold(ServerPlayerEntity player, boolean holding) {
        UUID id = player.getUuid();
        if (holding) {
            fHeld.add(id);
        } else {
            fHeld.remove(id);
        }

        if (!playerToKey.containsKey(id)) {
            PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
            if (pose != PoseState.GRAB_READY) {
                readyFHoldStart.remove(id);
                fHeld.remove(id);
            } else {
                if (holding) {
                    readyFHoldStart.putIfAbsent(id, System.currentTimeMillis());
                } else {
                    readyFHoldStart.remove(id);
                }
            }
        }
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        detectNewSpins(server, now);

        for (HandSpinHandler.Session s : new ArrayList<>(sessionsByKey.values())) {
            tickSession(s, server, now);
        }
    }

    private static void detectNewSpins(MinecraftServer server, long now) {
        if (!readyFHoldStart.isEmpty()) {
            List<UUID> candidates = new ArrayList<>();

            for (UUID id : readyFHoldStart.keySet()) {
                if (!playerToKey.containsKey(id)) {
                    Long start = readyFHoldStart.get(id);
                    if (start != null && now - start >= 1000L && fHeld.contains(id)) {
                        PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
                        if (pose == PoseState.GRAB_READY) {
                            candidates.add(id);
                        }
                    }
                }
            }

            if (candidates.size() >= 2) {
                for (int i = 0; i < candidates.size(); i++) {
                    UUID a = candidates.get(i);
                    if (!playerToKey.containsKey(a)) {
                        ServerPlayerEntity pa = server.getPlayerManager().getPlayer(a);
                        if (pa != null) {
                            for (int j = i + 1; j < candidates.size(); j++) {
                                UUID b = candidates.get(j);
                                if (!playerToKey.containsKey(b)) {
                                    ServerPlayerEntity pb = server.getPlayerManager().getPlayer(b);
                                    if (pb != null
                                        && !(pa.distanceTo(pb) > 2.0)
                                        && facingEachOther(pa, pb)
                                        && !GrabMechanic.isHolding(pa)
                                        && !GrabMechanic.isBeingHeld(pa)
                                        && !GrabMechanic.isHolding(pb)
                                        && !GrabMechanic.isBeingHeld(pb)) {
                                        startSpin(server, pa, pb);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean facingEachOther(ServerPlayerEntity a, ServerPlayerEntity b) {
        Vec3d fwdA = HighFiveHandler.horizontalForward(a);
        Vec3d fwdB = HighFiveHandler.horizontalForward(b);
        Vec3d aToB = b.getPos().subtract(a.getPos());
        double lenAB = aToB.horizontalLength();
        if (lenAB < 1.0E-4) {
            return true;
        } else {
            Vec3d dirAB = new Vec3d(aToB.x / lenAB, 0.0, aToB.z / lenAB);
            Vec3d dirBA = dirAB.multiply(-1.0);
            double dotA = fwdA.x * dirAB.x + fwdA.z * dirAB.z;
            double dotB = fwdB.x * dirBA.x + fwdB.z * dirBA.z;
            return dotA >= 0.0 && dotB >= 0.0;
        }
    }

    private static void startSpin(MinecraftServer server, ServerPlayerEntity a, ServerPlayerEntity b) {
        ServerPlayerEntity p1 = a;
        ServerPlayerEntity p2 = b;
        if (a.getUuid().compareTo(b.getUuid()) > 0) {
            p1 = b;
            p2 = a;
        }

        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        Vec3d pos1 = p1.getPos();
        Vec3d pos2 = p2.getPos();
        Vec3d center = pos1.add(pos2).multiply(0.5);
        double startAngle = Math.atan2(pos1.z - center.z, pos1.x - center.x);
        HandSpinHandler.Session s = new HandSpinHandler.Session(id1, id2, center, startAngle);
        s.continuousYaw1 = p1.getYaw();
        s.continuousYaw2 = p2.getYaw();
        sessionsByKey.put(id1, s);
        playerToKey.put(id1, id1);
        playerToKey.put(id2, id1);
        readyFHoldStart.remove(id1);
        readyFHoldStart.remove(id2);
        PoseNetworking.broadcastAnimState(p1, 120);
        PoseNetworking.broadcastAnimState(p2, 120);
        HandSpinHandler.HandSpinStartPayload startPayload = new HandSpinHandler.HandSpinStartPayload(
            id1, id2, center.x, center.y, center.z
        );
        ServerPlayNetworking.send(p1, startPayload);
        ServerPlayNetworking.send(p2, startPayload);
        ServerWorld world = (ServerWorld)p1.getWorld();
        world.playSound(null, center.x, center.y + 1.0, center.z, SoundEvents.field_14706, SoundCategory.field_15248, 1.0F, 1.3F);
        world.spawnParticles(ParticleTypes.field_11204, center.x, center.y + 1.0, center.z, 14, 0.5, 0.4, 0.5, 0.03);
        world.spawnParticles(ParticleTypes.field_11205, center.x, center.y + 1.0, center.z, 10, 0.4, 0.3, 0.4, 0.05);
    }

    private static void sendSyncIfDue(HandSpinHandler.Session s, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (++s.syncCounter >= 3) {
            s.syncCounter = 0;
            double lift = s.liftTicks <= 0 ? 0.0 : 0.5 * Math.min(1.0, s.liftTicks / 18.0);
            HandSpinHandler.HandSpinObserverPayload op = new HandSpinHandler.HandSpinObserverPayload(
                s.p1,
                s.p2,
                s.center.x,
                s.center.y,
                s.center.z,
                s.angle,
                s.angularVel,
                s.p1.equals(s.weakId) ? lift : 0.0,
                s.p2.equals(s.weakId) ? lift : 0.0
            );
            Set<ServerPlayerEntity> targets = new HashSet<>(PlayerLookup.tracking(p1));
            targets.addAll(PlayerLookup.tracking(p2));
            targets.add(p1);
            targets.add(p2);

            for (ServerPlayerEntity t : targets) {
                ServerPlayNetworking.send(t, op);
            }
        }
    }

    private static void playLoopEffects(HandSpinHandler.Session s, ServerPlayerEntity p1, ServerPlayerEntity p2, long tick) {
        ServerWorld world = (ServerWorld)p1.getWorld();
        double t = Math.min(1.0, s.angularVel / 0.2);
        world.spawnParticles(ParticleTypes.field_11205, s.center.x, s.center.y + 1.0, s.center.z, 2, 0.5, 0.4, 0.5, 0.02);
        long whooshInterval = 14L - Math.round(t * 8.0);
        if (tick % whooshInterval == 0L) {
            world.playSound(
                null,
                s.center.x,
                s.center.y + 1.0,
                s.center.z,
                SoundEvents.field_14706,
                SoundCategory.field_15248,
                0.45F,
                0.9F + (float)t * 0.8F
            );
        }
    }

    private static void tickSession(HandSpinHandler.Session s, MinecraftServer server, long now) {
        if (s.stage == HandSpinHandler.Stage.MONKE_FLY) {
            tickMonkeFly(s, server);
        } else {
            ServerPlayerEntity p1 = server.getPlayerManager().getPlayer(s.p1);
            ServerPlayerEntity p2 = server.getPlayerManager().getPlayer(s.p2);
            if (p1 != null && p2 != null) {
                if (p1.getWorld() != p2.getWorld()) {
                    hardCancel(s, p1, p2, server);
                } else if (!p1.hasVehicle() && !p2.hasVehicle() && !p1.isSpectator() && !p2.isSpectator()) {
                    if (now - s.startedAtMs > 300000L) {
                        hardCancel(s, p1, p2, server);
                    } else {
                        if (s.lastSeenStage != s.stage) {
                            s.lastSeenStage = s.stage;
                            s.stageEnteredTick = server.getTicks();
                        }

                        boolean terminalStage = s.stage == HandSpinHandler.Stage.ENDING_FAST
                            || s.stage == HandSpinHandler.Stage.ENDING_SLOW
                            || s.stage == HandSpinHandler.Stage.PUSH_APART
                            || s.stage == HandSpinHandler.Stage.DAMAGE_CUT
                            || s.stage == HandSpinHandler.Stage.DONE
                            || s.stage == HandSpinHandler.Stage.STRENGTH_YEET;
                        if (terminalStage && s.stageEnteredTick != -1L && server.getTicks() - s.stageEnteredTick > 200L) {
                            hardCancel(s, p1, p2, server);
                        } else if (p1.isAlive() && p2.isAlive()) {
                            switch (s.stage) {
                                case RAMPING_UP:
                                case SPINNING:
                                    if (s.stage == HandSpinHandler.Stage.RAMPING_UP && s.endAnimStartTick == -1L) {
                                        s.endAnimStartTick = server.getTicks();
                                    }

                                    boolean startAnimDone = s.endAnimStartTick != -1L && (server.getTicks() - s.endAnimStartTick) * 50L >= 334L;
                                    if (s.stage == HandSpinHandler.Stage.RAMPING_UP && startAnimDone) {
                                        PoseNetworking.broadcastAnimState(p1, 121);
                                        PoseNetworking.broadcastAnimState(p2, 121);
                                        s.stage = HandSpinHandler.Stage.SPINNING;
                                    }

                                    if (checkInterrupts(s, p1, p2)) {
                                        return;
                                    }

                                    if (!s.strengthMode && !s.branchLocked && s.angularVel >= 0.199999999) {
                                        boolean s1 = hasStrengthTwo(p1);
                                        boolean s2 = hasStrengthTwo(p2);
                                        if (s1 ^ s2) {
                                            s.strengthMode = true;
                                            s.strongId = s1 ? s.p1 : s.p2;
                                            s.weakId = s1 ? s.p2 : s.p1;
                                            s.strengthStartMs = now;
                                            ServerPlayerEntity weak = s1 ? p2 : p1;
                                            PoseNetworking.broadcastAnimState(weak, 123);
                                            HandSpinHandler.HandSpinStrengthPayload sp = new HandSpinHandler.HandSpinStrengthPayload(s.weakId);
                                            ServerPlayNetworking.send(p1, sp);
                                            ServerPlayNetworking.send(p2, sp);
                                        }
                                    }

                                    double cap = s.strengthMode ? 0.26 : 0.2;
                                    double accel = s.strengthMode ? 0.005 : 0.0037;
                                    if (s.strengthMode) {
                                        if (s.liftTicks < 18) {
                                            s.liftTicks++;
                                        }

                                        if (!s.monkeIdleSent && now - s.strengthStartMs >= 625L) {
                                            ServerPlayerEntity weak = server.getPlayerManager().getPlayer(s.weakId);
                                            if (weak != null) {
                                                PoseNetworking.broadcastAnimState(weak, 124);
                                            }

                                            s.monkeIdleSent = true;
                                        }
                                    }

                                    s.angularVel = Math.min(cap, s.angularVel + accel);
                                    sendSyncIfDue(s, p1, p2);
                                    s.angle = s.angle + s.angularVel;
                                    applyOrbitPositions(s, p1, p2);
                                    swingHandsIfDue(s, p1, p2, server);
                                    playLoopEffects(s, p1, p2, server.getTicks());
                                    refreshSpinShake(s, p1, p2);
                                    evaluateReleases(s, p1, p2, now);
                                    break;
                                case ENDING_FAST:
                                    beginTangentLaunch(s, p1, p2, server);
                                    break;
                                case ENDING_SLOW:
                                    sendSyncIfDue(s, p1, p2);
                                    s.angularVel = Math.max(0.0, s.angularVel - 0.0015);
                                    s.angle = s.angle + s.angularVel;
                                    applyOrbitPositions(s, p1, p2);
                                    swingHandsIfDue(s, p1, p2, server);
                                    if (s.angularVel <= 1.0E-4) {
                                        finishToEndAnim(s, p1, p2, server, false);
                                    }
                                    break;
                                case PUSH_APART:
                                    if (s.pushTicksRemaining > 0) {
                                        Vec3d step1 = s.p1RemainingDelta.multiply(1.0 / s.pushTicksRemaining);
                                        Vec3d step2 = s.p2RemainingDelta.multiply(1.0 / s.pushTicksRemaining);
                                        Vec3d np1 = p1.getPos().add(step1);
                                        Vec3d np2 = p2.getPos().add(step2);
                                        p1.requestTeleport(np1.x, np1.y, np1.z);
                                        p2.requestTeleport(np2.x, np2.y, np2.z);
                                        s.p1RemainingDelta = s.p1RemainingDelta.subtract(step1);
                                        s.p2RemainingDelta = s.p2RemainingDelta.subtract(step2);
                                        s.pushTicksRemaining--;
                                    } else if (s.tangentLaunch) {
                                        s.stage = HandSpinHandler.Stage.DONE;
                                    } else {
                                        finishToEndAnim(s, p1, p2, server, true);
                                    }
                                    break;
                                case DAMAGE_CUT:
                                    if (s.endAnimStartTick == -1L) {
                                        beginDamageCutKnockback(s, p1, p2, server);
                                    } else if (server.getTicks() - s.endAnimStartTick >= 2L) {
                                        releaseSession(s, p1, p2);
                                    }
                                    break;
                                case DONE:
                                    if (s.endAnimStartTick != -1L && (server.getTicks() - s.endAnimStartTick) * 50L >= 584L) {
                                        releaseSession(s, p1, p2);
                                    }
                                    break;
                                case STRENGTH_YEET:
                                    beginStrengthYeet(s, p1, p2, server);
                            }
                        } else {
                            hardCancel(s, p1, p2, server);
                        }
                    }
                } else {
                    hardCancel(s, p1, p2, server);
                }
            } else {
                hardCancel(s, p1, p2, server);
            }
        }
    }

    private static boolean checkInterrupts(HandSpinHandler.Session s, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        PoseState pose1 = PoseNetworking.poseStates.getOrDefault(s.p1, PoseState.NONE);
        PoseState pose2 = PoseNetworking.poseStates.getOrDefault(s.p2, PoseState.NONE);
        if (pose1 != PoseState.GRAB_READY) {
            PoseNetworking.poseStates.put(s.p1, PoseState.GRAB_READY);
        }

        if (pose2 != PoseState.GRAB_READY) {
            PoseNetworking.poseStates.put(s.p2, PoseState.GRAB_READY);
        }

        if (p1.distanceTo(p2) > 4.5) {
            if (!s.branchLocked) {
                lockBranchSlow(s);
            }

            return true;
        } else {
            return false;
        }
    }

    private static void evaluateReleases(HandSpinHandler.Session s, ServerPlayerEntity p1, ServerPlayerEntity p2, long now) {
        if (!s.branchLocked) {
            if (!s.strengthMode) {
                boolean p1Holding = fHeld.contains(s.p1);
                boolean p2Holding = fHeld.contains(s.p2);
                if (!p1Holding && s.p1ReleasedAt == null) {
                    s.p1ReleasedAt = now;
                }

                if (!p2Holding && s.p2ReleasedAt == null) {
                    s.p2ReleasedAt = now;
                }

                if (s.p1ReleasedAt != null || s.p2ReleasedAt != null) {
                    if (s.p1ReleasedAt != null && s.p2ReleasedAt != null) {
                        long diff = Math.abs(s.p1ReleasedAt - s.p2ReleasedAt);
                        if (diff <= 350L) {
                            lockBranchFast(s);
                        } else {
                            lockBranchSlow(s);
                        }
                    } else {
                        long firstReleaseAt = s.p1ReleasedAt != null ? s.p1ReleasedAt : s.p2ReleasedAt;
                        if (now - firstReleaseAt > 350L) {
                            lockBranchSlow(s);
                        }
                    }
                }
            } else {
                boolean strongHolding = fHeld.contains(s.strongId);
                if (!strongHolding || now - s.strengthStartMs >= 10000L) {
                    s.branchLocked = true;
                    s.stage = HandSpinHandler.Stage.STRENGTH_YEET;
                }
            }
        }
    }

    private static boolean hasStrengthTwo(ServerPlayerEntity p) {
        StatusEffectInstance effect = p.getStatusEffect(StatusEffects.field_5910);
        return effect != null && effect.getAmplifier() >= 1;
    }

    private static void lockBranchFast(HandSpinHandler.Session s) {
        s.branchLocked = true;
        s.stage = HandSpinHandler.Stage.ENDING_FAST;
    }

    private static void lockBranchSlow(HandSpinHandler.Session s) {
        s.branchLocked = true;
        s.stage = HandSpinHandler.Stage.ENDING_SLOW;
    }

    private static void lockBranchDamage(HandSpinHandler.Session s) {
        s.branchLocked = true;
        s.stage = HandSpinHandler.Stage.DAMAGE_CUT;
    }

    private static void swingHandsIfDue(HandSpinHandler.Session s, ServerPlayerEntity p1, ServerPlayerEntity p2, MinecraftServer server) {
        long tick = server.getTicks();
        if (s.lastHandSwingTick == -1L || tick - s.lastHandSwingTick >= 4L) {
            s.lastHandSwingTick = tick;
            p1.swingHand(Hand.field_5808, true);
            p2.swingHand(Hand.field_5808, true);
        }
    }

    private static void refreshSpinShake(HandSpinHandler.Session s, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        long now = System.currentTimeMillis();
        if (now - s.lastShakePulseMs >= 150L) {
            s.lastShakePulseMs = now;
            HandSpinHandler.HandSpinShakePulsePayload pulse = new HandSpinHandler.HandSpinShakePulsePayload(0.12F, 200L);
            ServerPlayNetworking.send(p1, pulse);
            ServerPlayNetworking.send(p2, pulse);
        }
    }

    private static void beginDamageCutKnockback(HandSpinHandler.Session s, ServerPlayerEntity p1, ServerPlayerEntity p2, MinecraftServer server) {
        Vec3d pos1 = p1.getPos();
        Vec3d pos2 = p2.getPos();
        Vec3d outward1 = pos1.subtract(s.center);
        Vec3d outward2 = pos2.subtract(s.center);
        double len1 = Math.max(0.001, outward1.horizontalLength());
        double len2 = Math.max(0.001, outward2.horizontalLength());
        Vec3d dir1 = new Vec3d(outward1.x / len1, 0.0, outward1.z / len1);
        Vec3d dir2 = new Vec3d(outward2.x / len2, 0.0, outward2.z / len2);
        p1.setVelocity(p1.getVelocity().add(dir1.multiply(0.75)).add(0.0, 0.15, 0.0));
        p2.setVelocity(p2.getVelocity().add(dir2.multiply(0.75)).add(0.0, 0.15, 0.0));
        p1.velocityModified = true;
        p2.velocityModified = true;
        PoseNetworking.broadcastAnimState(p1, 0);
        PoseNetworking.broadcastAnimState(p2, 0);
        ServerWorld world = (ServerWorld)p1.getWorld();
        world.playSound(
            null, s.center.x, s.center.y + 1.0, s.center.z, SoundEvents.field_14840, SoundCategory.field_15248, 1.2F, 0.7F
        );
        s.endAnimStartTick = server.getTicks();
    }

    private static void beginTangentLaunch(HandSpinHandler.Session s, ServerPlayerEntity p1, ServerPlayerEntity p2, MinecraftServer server) {
        double sin = Math.sin(s.angle);
        double cos = Math.cos(s.angle);
        Vec3d tan1 = new Vec3d(-sin, 0.0, cos);
        Vec3d tan2 = tan1.multiply(-1.0);
        double t = Math.min(1.0, s.angularVel / 0.2);
        double strength = 0.45 + 0.7 * t;
        p1.setVelocity(tan1.multiply(strength).add(0.0, 0.32, 0.0));
        p2.setVelocity(tan2.multiply(strength).add(0.0, 0.32, 0.0));
        p1.velocityModified = true;
        p2.velocityModified = true;
        s.p1RemainingDelta = tan1.multiply(1.0 * t);
        s.p2RemainingDelta = tan2.multiply(1.0 * t);
        s.pushTicksRemaining = 5;
        s.tangentLaunch = true;
        ServerPlayNetworking.send(p1, new HandSpinHandler.HandSpinStopPayload(s.p1));
        ServerPlayNetworking.send(p2, new HandSpinHandler.HandSpinStopPayload(s.p2));
        PoseNetworking.broadcastAnimState(p1, 122);
        PoseNetworking.broadcastAnimState(p2, 122);
        s.endAnimStartTick = server.getTicks();
        ServerWorld world = (ServerWorld)p1.getWorld();
        world.spawnParticles(ParticleTypes.field_11204, s.center.x, s.center.y + 1.0, s.center.z, 8, 0.25, 0.15, 0.25, 0.1);
        world.playSound(
            null,
            s.center.x,
            s.center.y + 1.0,
            s.center.z,
            SoundEvents.field_14706,
            SoundCategory.field_15248,
            1.0F,
            0.75F + (float)t * 0.4F
        );
        s.stage = HandSpinHandler.Stage.PUSH_APART;
    }

    private static void beginStrengthYeet(HandSpinHandler.Session s, ServerPlayerEntity p1, ServerPlayerEntity p2, MinecraftServer server) {
        ServerPlayerEntity strong = s.p1.equals(s.strongId) ? p1 : p2;
        ServerPlayerEntity weak = s.p1.equals(s.weakId) ? p1 : p2;
        boolean weakIsP1 = s.p1.equals(s.weakId);
        double sin = Math.sin(s.angle);
        double cos = Math.cos(s.angle);
        Vec3d tanWeak = weakIsP1 ? new Vec3d(-sin, 0.0, cos) : new Vec3d(sin, 0.0, -cos);
        weak.setVelocity(tanWeak.multiply(2.6).add(0.0, 0.7, 0.0));
        weak.velocityModified = true;
        ServerPlayNetworking.send(weak, new HandSpinHandler.HandSpinStopPayload(weak.getUuid()));
        ServerPlayNetworking.send(weak, new HandSpinHandler.HandSpinMonkeFlyPayload(weak.getUuid(), true));
        PoseNetworking.poseStates.put(strong.getUuid(), PoseState.NONE);
        PoseNetworking.broadcastPoseChange(server, strong.getUuid(), PoseState.NONE);
        PoseNetworking.broadcastAnimState(strong, 122);
        ServerPlayNetworking.send(strong, new HandSpinHandler.HandSpinStopPayload(strong.getUuid()));
        fHeld.remove(strong.getUuid());
        ServerWorld world = (ServerWorld)weak.getWorld();
        world.spawnParticles(ParticleTypes.field_11204, s.center.x, s.center.y + 1.0, s.center.z, 8, 0.25, 0.15, 0.25, 0.1);
        world.playSound(
            null, s.center.x, s.center.y + 1.0, s.center.z, SoundEvents.field_14706, SoundCategory.field_15248, 1.0F, 0.6F
        );
        s.flyTicks = 0;
        s.stage = HandSpinHandler.Stage.MONKE_FLY;
    }

    private static void tickMonkeFly(HandSpinHandler.Session s, MinecraftServer server) {
        ServerPlayerEntity weak = server.getPlayerManager().getPlayer(s.weakId);
        if (weak != null && weak.isAlive()) {
            s.flyTicks++;
            boolean landed = s.flyTicks > 4 && (weak.isOnGround() || weak.isTouchingWater());
            if (landed || s.flyTicks > 200) {
                finishMonkeFly(s, server, weak);
            }
        } else {
            finishMonkeFly(s, server, weak);
        }
    }

    private static void finishMonkeFly(HandSpinHandler.Session s, MinecraftServer server, ServerPlayerEntity weak) {
        PoseNetworking.poseStates.put(s.weakId, PoseState.NONE);
        PoseNetworking.broadcastPoseChange(server, s.weakId, PoseState.NONE);
        if (weak != null) {
            PoseNetworking.broadcastAnimState(weak, 0);
            ServerPlayNetworking.send(weak, new HandSpinHandler.HandSpinMonkeFlyPayload(s.weakId, false));
            ServerPlayNetworking.send(weak, new HandSpinHandler.HandSpinStopPayload(s.weakId));
        }

        endInternal(s);
    }

    private static void hardCancel(HandSpinHandler.Session s, ServerPlayerEntity p1, ServerPlayerEntity p2, MinecraftServer server) {
        for (UUID id : new UUID[]{s.p1, s.p2}) {
            PoseNetworking.poseStates.put(id, PoseState.NONE);
            if (server != null) {
                PoseNetworking.broadcastPoseChange(server, id, PoseState.NONE);
            }
        }

        for (ServerPlayerEntity p : new ServerPlayerEntity[]{p1, p2}) {
            if (p != null) {
                UUID idx = p.getUuid();
                PoseNetworking.broadcastAnimState(p, 0);
                ServerPlayNetworking.send(p, new HandSpinHandler.HandSpinStopPayload(idx));
                ServerPlayNetworking.send(p, new HandSpinHandler.HandSpinMonkeFlyPayload(idx, false));
            }
        }

        endInternal(s);
    }

    private static void beginPushApart(HandSpinHandler.Session s, ServerPlayerEntity p1, ServerPlayerEntity p2, MinecraftServer server) {
        Vec3d pos1 = p1.getPos();
        Vec3d pos2 = p2.getPos();
        Vec3d outward1 = pos1.subtract(s.center);
        Vec3d outward2 = pos2.subtract(s.center);
        double len1 = Math.max(0.001, outward1.horizontalLength());
        double len2 = Math.max(0.001, outward2.horizontalLength());
        Vec3d dir1 = new Vec3d(outward1.x / len1, 0.0, outward1.z / len1);
        Vec3d dir2 = new Vec3d(outward2.x / len2, 0.0, outward2.z / len2);
        p1.setVelocity(p1.getVelocity().add(dir1.multiply(0.35)));
        p2.setVelocity(p2.getVelocity().add(dir2.multiply(0.35)));
        p1.velocityModified = true;
        p2.velocityModified = true;
        s.p1RemainingDelta = dir1.multiply(0.6);
        s.p2RemainingDelta = dir2.multiply(0.6);
        s.pushTicksRemaining = 6;
        s.stage = HandSpinHandler.Stage.PUSH_APART;
    }

    private static void finishToEndAnim(HandSpinHandler.Session s, ServerPlayerEntity p1, ServerPlayerEntity p2, MinecraftServer server, boolean fromPush) {
        PoseNetworking.broadcastAnimState(p1, 122);
        PoseNetworking.broadcastAnimState(p2, 122);
        s.endAnimStartTick = server.getTicks();
        s.stage = HandSpinHandler.Stage.DONE;
    }

    private static void releaseSession(HandSpinHandler.Session s, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (p1 != null) {
            PoseNetworking.poseStates.put(s.p1, PoseState.NONE);
            PoseNetworking.broadcastPoseChange(p1.getServer(), s.p1, PoseState.NONE);
            PoseNetworking.broadcastAnimState(p1, 0);
            ServerPlayNetworking.send(p1, new HandSpinHandler.HandSpinStopPayload(s.p1));
        }

        if (p2 != null) {
            PoseNetworking.poseStates.put(s.p2, PoseState.NONE);
            PoseNetworking.broadcastPoseChange(p2.getServer(), s.p2, PoseState.NONE);
            PoseNetworking.broadcastAnimState(p2, 0);
            ServerPlayNetworking.send(p2, new HandSpinHandler.HandSpinStopPayload(s.p2));
        }

        endInternal(s);
    }

    private static void endInternal(HandSpinHandler.Session s) {
        sessionsByKey.remove(s.p1);
        playerToKey.remove(s.p1);
        playerToKey.remove(s.p2);
        fHeld.remove(s.p1);
        fHeld.remove(s.p2);
        readyFHoldStart.remove(s.p1);
        readyFHoldStart.remove(s.p2);
    }

    private static void applyOrbitPositions(HandSpinHandler.Session s, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        double x1 = s.center.x + Math.cos(s.angle) * 0.75;
        double z1 = s.center.z + Math.sin(s.angle) * 0.75;
        double x2 = s.center.x + Math.cos(s.angle + Math.PI) * 0.75;
        double z2 = s.center.z + Math.sin(s.angle + Math.PI) * 0.75;
        double y = s.center.y;
        double lift = s.liftTicks <= 0 ? 0.0 : 0.5 * Math.min(1.0, s.liftTicks / 18.0);
        double y1 = y + (s.p1.equals(s.weakId) ? lift : 0.0);
        double y2 = y + (s.p2.equals(s.weakId) ? lift : 0.0);
        float yaw1 = (float)Math.toDegrees(Math.atan2(x1 - s.center.x, -(z1 - s.center.z)));
        float yaw2 = (float)Math.toDegrees(Math.atan2(x2 - s.center.x, -(z2 - s.center.z)));
        p1.setYaw(yaw1);
        p1.setHeadYaw(yaw1);
        p1.setBodyYaw(yaw1);
        p2.setYaw(yaw2);
        p2.setHeadYaw(yaw2);
        p2.setBodyYaw(yaw2);
        p1.requestTeleport(x1, y1, z1);
        p2.requestTeleport(x2, y2, z2);
    }

    private static float unwrapYaw(Float previousContinuous, float rawTargetYaw) {
        if (previousContinuous == null) {
            return rawTargetYaw;
        } else {
            float delta = MathHelper.wrapDegrees(rawTargetYaw - previousContinuous);
            return previousContinuous + delta;
        }
    }

    public static boolean isInSession(UUID playerId) {
        return playerToKey.containsKey(playerId);
    }

    public static void cleanup(UUID playerId) {
        readyFHoldStart.remove(playerId);
        fHeld.remove(playerId);
        UUID key = playerToKey.get(playerId);
        if (key != null) {
            HandSpinHandler.Session s = sessionsByKey.get(key);
            if (s == null) {
                playerToKey.remove(playerId);
            } else {
                endInternal(s);
            }
        }
    }

    public static void cleanup(UUID playerId, MinecraftServer server) {
        UUID key = playerToKey.get(playerId);
        readyFHoldStart.remove(playerId);
        fHeld.remove(playerId);
        if (key != null) {
            HandSpinHandler.Session s = sessionsByKey.get(key);
            if (s == null) {
                playerToKey.remove(playerId);
            } else {
                UUID otherId = s.p1.equals(playerId) ? s.p2 : s.p1;
                if (server != null) {
                    ServerPlayerEntity self = server.getPlayerManager().getPlayer(playerId);
                    if (self != null) {
                        PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                        PoseNetworking.broadcastPoseChange(server, playerId, PoseState.NONE);
                        PoseNetworking.broadcastAnimState(self, 0);
                        ServerPlayNetworking.send(self, new HandSpinHandler.HandSpinStopPayload(playerId));
                        ServerPlayNetworking.send(self, new HandSpinHandler.HandSpinMonkeFlyPayload(playerId, false));
                    }

                    ServerPlayerEntity other = server.getPlayerManager().getPlayer(otherId);
                    if (other != null) {
                        PoseNetworking.poseStates.put(otherId, PoseState.NONE);
                        PoseNetworking.broadcastPoseChange(server, otherId, PoseState.NONE);
                        PoseNetworking.broadcastAnimState(other, 0);
                        ServerPlayNetworking.send(other, new HandSpinHandler.HandSpinStopPayload(otherId));
                        ServerPlayNetworking.send(other, new HandSpinHandler.HandSpinMonkeFlyPayload(otherId, false));
                    }
                }

                endInternal(s);
            }
        }
    }

    public record HandSpinFHoldPayload(boolean holding) implements CustomPayload {
        public static final Id<HandSpinHandler.HandSpinFHoldPayload> ID = new Id(new Identifier("cooptest", "handspin_fhold"));
        public static final PacketCodec<PacketByteBuf, HandSpinHandler.HandSpinFHoldPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeBoolean(payload.holding), buf -> new HandSpinHandler.HandSpinFHoldPayload(buf.readBoolean())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HandSpinMonkeFlyPayload(UUID playerId, boolean flying) implements CustomPayload {
        public static final Id<HandSpinHandler.HandSpinMonkeFlyPayload> ID = new Id(new Identifier("cooptest", "handspin_monke_fly"));
        public static final PacketCodec<PacketByteBuf, HandSpinHandler.HandSpinMonkeFlyPayload> CODEC = PacketCodec.of((value, buf) -> {
            buf.writeUuid(value.playerId());
            buf.writeBoolean(value.flying());
        }, buf -> new HandSpinHandler.HandSpinMonkeFlyPayload(buf.readUuid(), buf.readBoolean()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HandSpinObserverPayload(
        UUID p1, UUID p2, double centerX, double centerY, double centerZ, double angle, double angularVel, double p1Lift, double p2Lift
    ) implements CustomPayload {
        public static final Id<HandSpinHandler.HandSpinObserverPayload> ID = new Id(new Identifier("cooptest", "handspin_observer"));
        public static final PacketCodec<PacketByteBuf, HandSpinHandler.HandSpinObserverPayload> CODEC = PacketCodec.of(
            (v, buf) -> {
                buf.writeUuid(v.p1());
                buf.writeUuid(v.p2());
                buf.writeDouble(v.centerX());
                buf.writeDouble(v.centerY());
                buf.writeDouble(v.centerZ());
                buf.writeDouble(v.angle());
                buf.writeDouble(v.angularVel());
                buf.writeDouble(v.p1Lift());
                buf.writeDouble(v.p2Lift());
            },
            buf -> new HandSpinHandler.HandSpinObserverPayload(
                buf.readUuid(),
                buf.readUuid(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble()
            )
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HandSpinShakePulsePayload(float amount, long durationMs) implements CustomPayload {
        public static final Id<HandSpinHandler.HandSpinShakePulsePayload> ID = new Id(
            new Identifier("cooptest", "handspin_shake_pulse")
        );
        public static final PacketCodec<PacketByteBuf, HandSpinHandler.HandSpinShakePulsePayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeFloat(payload.amount);
            buf.writeVarLong(payload.durationMs);
        }, buf -> new HandSpinHandler.HandSpinShakePulsePayload(buf.readFloat(), buf.readVarLong()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HandSpinStartPayload(UUID p1, UUID p2, double centerX, double centerY, double centerZ) implements CustomPayload {
        public static final Id<HandSpinHandler.HandSpinStartPayload> ID = new Id(new Identifier("cooptest", "handspin_start"));
        public static final PacketCodec<PacketByteBuf, HandSpinHandler.HandSpinStartPayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeUuid(payload.p1);
            buf.writeUuid(payload.p2);
            buf.writeDouble(payload.centerX);
            buf.writeDouble(payload.centerY);
            buf.writeDouble(payload.centerZ);
        }, buf -> new HandSpinHandler.HandSpinStartPayload(buf.readUuid(), buf.readUuid(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HandSpinStopPayload(UUID playerId) implements CustomPayload {
        public static final Id<HandSpinHandler.HandSpinStopPayload> ID = new Id(new Identifier("cooptest", "handspin_stop"));
        public static final PacketCodec<PacketByteBuf, HandSpinHandler.HandSpinStopPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeUuid(payload.playerId), buf -> new HandSpinHandler.HandSpinStopPayload(buf.readUuid())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HandSpinStrengthPayload(UUID weakId) implements CustomPayload {
        public static final Id<HandSpinHandler.HandSpinStrengthPayload> ID = new Id(new Identifier("cooptest", "handspin_strength"));
        public static final PacketCodec<PacketByteBuf, HandSpinHandler.HandSpinStrengthPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeUuid(value.weakId()), buf -> new HandSpinHandler.HandSpinStrengthPayload(buf.readUuid())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HandSpinSyncPayload(double angle, double angularVel) implements CustomPayload {
        public static final Id<HandSpinHandler.HandSpinSyncPayload> ID = new Id(new Identifier("cooptest", "handspin_sync"));
        public static final PacketCodec<PacketByteBuf, HandSpinHandler.HandSpinSyncPayload> CODEC = PacketCodec.of((value, buf) -> {
            buf.writeDouble(value.angle());
            buf.writeDouble(value.angularVel());
        }, buf -> new HandSpinHandler.HandSpinSyncPayload(buf.readDouble(), buf.readDouble()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static class Session {
        final UUID p1;
        final UUID p2;
        HandSpinHandler.Stage stage = HandSpinHandler.Stage.RAMPING_UP;
        Vec3d center;
        double angle;
        double angularVel = 0.0;
        Long p1ReleasedAt = null;
        Long p2ReleasedAt = null;
        boolean branchLocked = false;
        Vec3d p1RemainingDelta;
        Vec3d p2RemainingDelta;
        int pushTicksRemaining = 0;
        boolean tangentLaunch = false;
        boolean strengthMode = false;
        UUID strongId = null;
        UUID weakId = null;
        long strengthStartMs = 0L;
        boolean monkeIdleSent = false;
        int liftTicks = 0;
        int flyTicks = 0;
        int syncCounter = 0;
        long endAnimStartTick = -1L;
        long lastHandSwingTick = -1L;
        long lastShakePulseMs = 0L;
        final long startedAtMs = System.currentTimeMillis();
        HandSpinHandler.Stage lastSeenStage = null;
        long stageEnteredTick = -1L;
        Float continuousYaw1 = null;
        Float continuousYaw2 = null;

        Session(UUID p1, UUID p2, Vec3d center, double startAngle) {
            this.p1 = p1;
            this.p2 = p2;
            this.center = center;
            this.angle = startAngle;
        }
    }

    private static enum Stage {
        RAMPING_UP,
        SPINNING,
        ENDING_FAST,
        ENDING_SLOW,
        PUSH_APART,
        DAMAGE_CUT,
        DONE,
        STRENGTH_YEET,
        MONKE_FLY;
    }
}
