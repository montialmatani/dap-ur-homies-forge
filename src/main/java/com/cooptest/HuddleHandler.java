package com.cooptest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Hand;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class HuddleHandler {
    private static final double HUDDLE_RANGE = 2.0;
    private static final double HUDDLE_RADIUS = 1.0;
    private static final long HOLD_REQUIRED_MS = 1000L;
    private static final long F_RELEASE_GRACE_MS = 1200L;
    private static final long QTE_WINDOW_MS = 1400L;
    private static final long QTE_ANIM_PRE_MS = 400L;
    private static final long HUDDLE_MAX_MS = 20000L;
    private static final long COOLDOWN_MS = 8000L;
    private static final int ANIM_HUDDLE_START = 70;
    private static final int ANIM_HUDDLE_IDLE = 71;
    private static final int ANIM_HUDDLE_QTE1 = 72;
    private static final int ANIM_HUDDLE_END = 73;
    private static final int ANIM_HUDDLE_QTE2 = 77;
    private static final int ANIM_HUDDLE_QTE3 = 78;
    private static final int ANIM_NONE = 0;
    public static final double ARM_CHARGE_MIN = 0.95;
    public static final long ARM_SHIFT_HOLD_MS = 1000L;
    public static final double TAP_FILL = 0.055;
    public static final double BAR_DRAIN_PER_TICK = 0.016;
    public static final long BAR_DRAIN_GRACE_MS = 220L;
    public static final double TAP_SCALE_PER_PLAYER = 0.55;
    public static final double STAGE2_AT = 0.34;
    public static final double STAGE3_AT = 0.68;
    public static final float SHAKE_MIN = 0.1F;
    public static final float SHAKE_MAX = 0.85F;
    public static final long SHAKE_REFRESH_MS = 160L;
    public static final int MAX_SCALE_PLAYERS = 8;
    public static final long CANCEL_GRACE_MS = 700L;
    public static final long QTE_AUTO_START_MS = 900L;
    public static final long ACTIVE_WINDOW_MS = 700L;
    public static final long END_MOVE_RELEASE_MS = 960L;
    public static final double SYNC_BONUS_FILL = 0.05;
    public static final long SYNC_BONUS_COOLDOWN_MS = 400L;
    private static final long HUDDLE_START_MS = 542L;
    private static final Random RANDOM = new Random();
    private static final Map<String, HuddleHandler.HuddleSession> sessions = new HashMap<>();
    private static final Map<UUID, String> playerSession = new HashMap<>();
    private static final Map<UUID, Long> fHoldStart = new HashMap<>();
    private static final Map<String, Long> cooldowns = new HashMap<>();
    private static final Map<UUID, Long> joinerEnterMs = new HashMap<>();
    public static final long CHARGE_GRACE_MS = 1200L;
    private static final Map<UUID, Long> lastFullCharge = new HashMap<>();

    private static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(HuddleHandler.HuddleFHoldPayload.ID, HuddleHandler.HuddleFHoldPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HuddleHandler.HuddleShiftPayload.ID, HuddleHandler.HuddleShiftPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HuddleHandler.HuddleTapPayload.ID, HuddleHandler.HuddleTapPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HuddleHandler.HuddleCancelPayload.ID, HuddleHandler.HuddleCancelPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HuddleHandler.HuddleEndPayload.ID, HuddleHandler.HuddleEndPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HuddleHandler.HuddleBarPayload.ID, HuddleHandler.HuddleBarPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HuddleHandler.HuddleShakePayload.ID, HuddleHandler.HuddleShakePayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
            HuddleHandler.HuddleFHoldPayload.ID, (payload, ctx) -> ctx.server().execute(() -> DuoPoseHandler.onFHold(ctx.player(), payload.holding()))
        );
        ServerPlayNetworking.registerGlobalReceiver(
            HuddleHandler.HuddleShiftPayload.ID, (payload, ctx) -> ctx.server().execute(() -> onShiftHold(ctx.player(), payload.holding()))
        );
        ServerPlayNetworking.registerGlobalReceiver(HuddleHandler.HuddleTapPayload.ID, (payload, ctx) -> ctx.server().execute(() -> onTap(ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(HuddleHandler.HuddleCancelPayload.ID, (payload, ctx) -> ctx.server().execute(() -> onCancel(ctx.player())));
        ServerTickEvents.END_SERVER_TICK.register(HuddleHandler::tick);
    }

    private static void onShiftHold(ServerPlayerEntity player, boolean holding) {
        UUID id = player.getUuid();
        String sk = playerSession.get(id);
        if (sk != null) {
            HuddleHandler.HuddleSession s = sessions.get(sk);
            if (s != null) {
                if (holding && System.currentTimeMillis() - s.huddleStart > 700L) {
                    onCancel(player);
                }

                return;
            }
        }

        if (!holding || !HighFiveHandler.isInBlockingState(id)) {
            if (!holding || sk != null || !DuoPoseHandler.isBlockingHuddle(id)) {
                if (sk != null) {
                    HuddleHandler.HuddleSession s = sessions.get(sk);
                    if (s != null) {
                        if (holding) {
                            s.holdsF.add(id);
                        } else {
                            s.holdsF.remove(id);
                        }

                        if (s.stage == HuddleHandler.HuddleStage.IDLE && !holding && s.firstRelease == null) {
                            s.firstRelease = System.currentTimeMillis();
                        }

                        return;
                    }
                }

                PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
                if (pose != PoseState.GRAB_READY
                    && pose != PoseState.GRAB_HOLDING
                    && pose != PoseState.GRABBED
                    && pose != PoseState.PUSH_IDLE
                    && pose != PoseState.PUSH_ACTION) {
                    if (holding) {
                        fHoldStart.putIfAbsent(id, System.currentTimeMillis());
                    } else {
                        fHoldStart.remove(id);
                    }
                }
            }
        }
    }

    private static void noteCharges() {
        long now = System.currentTimeMillis();
        long need = 237L;

        for (Entry<UUID, Long> e : ChargedDapHandler.chargeStartTime.entrySet()) {
            if (now - e.getValue() >= need) {
                lastFullCharge.put(e.getKey(), now);
            }
        }
    }

    private static boolean atFullCharge(UUID id) {
        Long when = lastFullCharge.get(id);
        return when != null && System.currentTimeMillis() - when <= 1200L;
    }

    private static void consumeCharges(Collection<UUID> ids) {
        for (UUID id : ids) {
            ChargedDapHandler.chargeStartTime.remove(id);
            lastFullCharge.remove(id);
        }
    }

    private static void onTap(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        String sk = playerSession.get(id);
        if (sk != null) {
            HuddleHandler.HuddleSession s = sessions.get(sk);
            if (s != null && s.stage == HuddleHandler.HuddleStage.QTE) {
                long now = System.currentTimeMillis();
                s.lastTapByPlayer.put(id, now);
                int active = 0;

                for (UUID u : s.players) {
                    Long t = s.lastTapByPlayer.get(u);
                    if (t != null && now - t <= 700L) {
                        active++;
                    }
                }

                int total = Math.max(1, s.players.size());
                double participation = (double)active / total;
                double divisor = 1.0 + 0.55 * Math.max(0, total - 2);
                double gain = 0.055 / divisor * participation;
                if (active == total && total >= 2 && now - s.lastSyncBonus >= 400L) {
                    s.lastSyncBonus = now;
                    gain += 0.05;
                    ServerWorld w = (ServerWorld)player.getWorld();
                    Vec3d c = s.center.add(0.0, 1.2, 0.0);
                    w.spawnParticles(ParticleTypes.field_11220, c.x, c.y, c.z, 14, 0.4, 0.4, 0.4, 0.25);
                    w.playSound(
                        null, c.x, c.y, c.z, (SoundEvent)SoundEvents.field_14725.comp_349(), SoundCategory.field_15248, 0.9F, 1.8F
                    );
                    MinecraftServer srv = w.getServer();
                    if (srv != null) {
                        for (UUID ux : s.players) {
                            ServerPlayerEntity lp = srv.getPlayerManager().getPlayer(ux);
                            if (lp != null) {
                                ServerPlayNetworking.send(lp, new HuddleHandler.HuddleShakePayload(0.5F, 140));
                                lp.sendMessage(Text.literal("§6§lIN SYNC"), true);
                            }
                        }
                    }
                }

                s.bar = Math.min(1.0, s.bar + gain);
                s.lastTapMs = now;
            }
        }
    }

    private static void onCancel(ServerPlayerEntity player) {
        String sk = playerSession.get(player.getUuid());
        if (sk != null) {
            HuddleHandler.HuddleSession s = sessions.get(sk);
            if (s != null) {
                MinecraftServer server = player.getServer();
                if (server != null) {
                    UUID id = player.getUuid();
                    player.sendMessage(Text.literal("§7left the huddle"), true);
                    s.players.remove(id);
                    s.holdsF.remove(id);
                    s.cancelArmed.remove(id);
                    playerSession.remove(id);
                    PoseNetworking.broadcastAnimState(player, 0);
                    ServerPlayNetworking.send(player, new HuddleHandler.HuddleBarPayload(0.0F, 0, false));
                    if (s.players.isEmpty()) {
                        failHuddle(s, server);
                    } else {
                        smoothRepositionAll(s, server);
                    }
                }
            }
        }
    }

    public static boolean onButtonPress(ServerPlayerEntity player, String button) {
        UUID id = player.getUuid();
        String sk = playerSession.get(id);
        if (sk == null) {
            return false;
        } else {
            HuddleHandler.HuddleSession s = sessions.get(sk);
            if (s == null || s.stage != HuddleHandler.HuddleStage.QTE || !s.qteOpen) {
                return false;
            } else if ("FAIL".equals(button)) {
                failHuddle(s, player.getServer());
                return true;
            } else if (!"G".equals(button) && !"H".equals(button)) {
                return false;
            } else {
                String expected = s.expectedButton();
                if (!button.equals(expected)) {
                    failHuddle(s, player.getServer());
                    return true;
                } else {
                    if (id.equals(s.p1)) {
                        s.qteHits.add(s.p1);
                    } else if (s.players.contains(id)) {
                        s.qteHits.add(id);
                    }

                    return true;
                }
            }
        }
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (server.getTicks() % 4 == 0) {
            detectNewHuddles(server, now);
        }

        joinerEnterMs.entrySet().removeIf(entry -> {
            if (now - entry.getValue() >= 542L) {
                ServerPlayerEntity jp = server.getPlayerManager().getPlayer(entry.getKey());
                if (jp != null) {
                    PoseNetworking.broadcastAnimState(jp, 71);
                }

                return true;
            } else {
                return false;
            }
        });
        Set<HuddleHandler.HuddleSession> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        for (HuddleHandler.HuddleSession s : new ArrayList<>(sessions.values())) {
            if (seen.add(s)) {
                tickSession(s, server, now);
            }
        }

        cooldowns.entrySet().removeIf(e -> now - e.getValue() > 8000L);
    }

    private static void detectNewHuddles(MinecraftServer server, long now) {
        noteCharges();
        List<UUID> holders = new ArrayList<>(fHoldStart.keySet());

        for (UUID joiner : holders) {
            if (!playerSession.containsKey(joiner)) {
                Long startJ = fHoldStart.get(joiner);
                if (startJ != null && now - startJ >= 1000L) {
                    final ServerPlayerEntity pj = server.getPlayerManager().getPlayer(joiner);
                    if (pj != null) {
                        for (HuddleHandler.HuddleSession s : new ArrayList<>(sessions.values())) {
                            if (s.stage == HuddleHandler.HuddleStage.IDLE
                                && !s.players.contains(joiner)
                                && s.center != null
                                && !(pj.squaredDistanceTo(s.center) > 9.0)) {
                                s.players.add(joiner);
                                consumeCharges(List.of(joiner));
                                s.holdsF.add(joiner);
                                playerSession.put(joiner, key(s.p1, s.p2));
                                fHoldStart.remove(joiner);
                                smoothRepositionAll(s, server);
                                PoseNetworking.broadcastAnimState(pj, 70);
                                joinerEnterMs.put(joiner, now);

                                for (UUID uid : s.players) {
                                    ServerPlayerEntity pp = server.getPlayerManager().getPlayer(uid);
                                    if (pp != null) {
                                        pp.swingHand(Hand.field_5808, true);
                                    }
                                }

                                final List<UUID> allNow = new ArrayList<>(s.players);
                                new Timer().schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        server.execute(() -> {
                                            for (UUID uidx : allNow) {
                                                ServerPlayerEntity pp = server.getPlayerManager().getPlayer(uidx);
                                                if (pp != null) {
                                                    pp.swingHand(Hand.field_5808, true);
                                                }
                                            }
                                        });
                                    }
                                }, 120L);
                                new Timer().schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        server.execute(() -> {
                                            for (UUID uid : allNow) {
                                                ServerPlayerEntity pp = server.getPlayerManager().getPlayer(uid);
                                                if (pp != null) {
                                                    pp.swingHand(Hand.field_5808, true);
                                                }
                                            }
                                        });
                                    }
                                }, 260L);
                                new Timer()
                                    .schedule(
                                        new TimerTask() {
                                            @Override
                                            public void run() {
                                                server.execute(
                                                    () -> {
                                                        if (pj.isAlive()) {
                                                            Vec3d arm = pj.getPos()
                                                                .add(0.0, 1.4, 0.0)
                                                                .add(pj.getRotationVec(1.0F).multiply(0.4));
                                                            ((ServerWorld)pj.getWorld())
                                                                .spawnParticles(
                                                                    ParticleTypes.field_11205,
                                                                    arm.x,
                                                                    arm.y,
                                                                    arm.z,
                                                                    6,
                                                                    0.08,
                                                                    0.08,
                                                                    0.08,
                                                                    0.06
                                                                );
                                                            ((ServerWorld)pj.getWorld())
                                                                .spawnParticles(
                                                                    ParticleTypes.field_11208,
                                                                    arm.x,
                                                                    arm.y,
                                                                    arm.z,
                                                                    4,
                                                                    0.06,
                                                                    0.06,
                                                                    0.06,
                                                                    0.04
                                                                );
                                                            ((ServerWorld)pj.getWorld())
                                                                .playSound(
                                                                    null,
                                                                    arm.x,
                                                                    arm.y,
                                                                    arm.z,
                                                                    (SoundEvent)SoundEvents.field_14793.comp_349(),
                                                                    SoundCategory.field_15248,
                                                                    0.8F,
                                                                    2.0F
                                                                );
                                                        }
                                                    }
                                                );
                                            }
                                        },
                                        420L
                                    );
                                ((ServerWorld)pj.getWorld())
                                    .playSound(
                                        null,
                                        s.center.x,
                                        s.center.y,
                                        s.center.z,
                                        (SoundEvent)SoundEvents.field_14793.comp_349(),
                                        SoundCategory.field_15248,
                                        1.0F,
                                        1.7F
                                    );
                                pj.sendMessage(Text.literal("§aYou joined the huddle!"), true);

                                for (UUID pid : s.players) {
                                    if (!pid.equals(joiner)) {
                                        ServerPlayerEntity pp = server.getPlayerManager().getPlayer(pid);
                                        if (pp != null) {
                                            pp.sendMessage(Text.literal("§a" + pj.getName().getString() + " joined the huddle!"), true);
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }

        for (int i = 0; i < holders.size(); i++) {
            UUID a = holders.get(i);
            if (!playerSession.containsKey(a)) {
                Long startA = fHoldStart.get(a);
                if (startA != null && now - startA >= 1000L && atFullCharge(a)) {
                    ServerPlayerEntity pa = server.getPlayerManager().getPlayer(a);
                    if (pa != null) {
                        for (int j = i + 1; j < holders.size(); j++) {
                            UUID b = holders.get(j);
                            if (!playerSession.containsKey(b)) {
                                Long startB = fHoldStart.get(b);
                                if (startB != null && now - startB >= 1000L && atFullCharge(b)) {
                                    ServerPlayerEntity pb = server.getPlayerManager().getPlayer(b);
                                    if (pb != null && !(pa.distanceTo(pb) > 2.0)) {
                                        String k = key(a, b);
                                        if (!cooldowns.containsKey(k)) {
                                            List<UUID> initPlayers = new ArrayList<>(List.of(a, b));
                                            HuddleHandler.HuddleSession sx = new HuddleHandler.HuddleSession(initPlayers);
                                            sessions.put(k, sx);
                                            playerSession.put(a, k);
                                            playerSession.put(b, k);
                                            fHoldStart.remove(a);
                                            fHoldStart.remove(b);
                                            startHuddle(sx, pa, pb, server);
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
    }

    private static void startHuddle(HuddleHandler.HuddleSession s, ServerPlayerEntity pa, ServerPlayerEntity pb, MinecraftServer server) {
        consumeCharges(s.players);
        s.center = pa.getPos().add(pb.getPos()).multiply(0.5);
        Vec3d toPa = pa.getPos().subtract(s.center);
        s.baseAngle = toPa.horizontalLengthSquared() > 0.001 ? Math.atan2(toPa.z, toPa.x) : 0.0;
        ServerWorld world = (ServerWorld)pa.getWorld();
        smoothRepositionAll(s, server);

        for (UUID uid : s.players) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uid);
            if (p != null) {
                PoseNetworking.broadcastAnimState(p, 70);
                p.swingHand(Hand.field_5808, true);
            }
        }

        final List<UUID> foundersSnap = new ArrayList<>(s.players);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                pa.getServer().execute(() -> {
                    for (UUID uidx : foundersSnap) {
                        ServerPlayerEntity p = pa.getServer().getPlayerManager().getPlayer(uidx);
                        if (p != null) {
                            p.swingHand(Hand.field_5808, true);
                        }
                    }
                });
            }
        }, 120L);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                pa.getServer().execute(() -> {
                    for (UUID uid : foundersSnap) {
                        ServerPlayerEntity p = pa.getServer().getPlayerManager().getPlayer(uid);
                        if (p != null) {
                            p.swingHand(Hand.field_5808, true);
                        }
                    }
                });
            }
        }, 260L);
        world.playSound(
            null,
            s.center.x,
            s.center.y,
            s.center.z,
            (SoundEvent)SoundEvents.field_14793.comp_349(),
            SoundCategory.field_15248,
            1.0F,
            1.5F
        );
        world.spawnParticles(ParticleTypes.field_11208, s.center.x, s.center.y + 1.0, s.center.z, 12, 0.4, 0.4, 0.4, 0.06);
    }

    private static void smoothRepositionAll(HuddleHandler.HuddleSession s, MinecraftServer server) {
        if (s.center != null) {
            int n = s.players.size();

            for (int ci = 0; ci < n; ci++) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(s.players.get(ci));
                if (p != null) {
                    double angle = s.baseAngle + (Math.PI * 2) * ci / n;
                    double targetX = s.center.x + 1.0 * Math.cos(angle);
                    double targetZ = s.center.z + 1.0 * Math.sin(angle);
                    float targetYaw = (float)(-Math.toDegrees(Math.atan2(s.center.x - targetX, s.center.z - targetZ)));
                    Vec3d startPos = p.getPos();

                    for (int step = 1; step <= 4; step++) {
                        final double frac = step / 4.0;
                        final double stepX = startPos.x + (targetX - startPos.x) * frac;
                        final double stepZ = startPos.z + (targetZ - startPos.z) * frac;
                        final float fYaw = targetYaw;
                        final ServerPlayerEntity fp = p;
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                server.execute(() -> {
                                    if (fp.isAlive()) {
                                        fp.teleport((ServerWorld)fp.getWorld(), stepX, fp.getY(), stepZ, fYaw, 0.0F);
                                        if (frac >= 1.0) {
                                            fp.setYaw(fYaw);
                                            fp.setBodyYaw(fYaw);
                                        }
                                    }
                                });
                            }
                        }, step * 50L);
                    }
                }
            }
        }
    }

    private static void repositionCircle(HuddleHandler.HuddleSession s, MinecraftServer server) {
        if (s.center != null) {
            int n = s.players.size();
            double[] slotAngle = new double[n];

            for (int i = 0; i < n; i++) {
                slotAngle[i] = s.baseAngle + (Math.PI * 2) * i / n;
            }

            int[] assigned = new int[n];
            boolean[] taken = new boolean[n];
            Arrays.fill(assigned, -1);

            for (int pi = 0; pi < n; pi++) {
                ServerPlayerEntity pp = server.getPlayerManager().getPlayer(s.players.get(pi));
                if (pp != null) {
                    Vec3d off = pp.getPos().subtract(s.center);
                    double cur = Math.atan2(off.z, off.x);
                    int best = -1;
                    double bestDelta = Double.MAX_VALUE;

                    for (int si = 0; si < n; si++) {
                        if (!taken[si]) {
                            double d = Math.abs(MathHelper.wrapDegrees(Math.toDegrees(slotAngle[si] - cur)));
                            if (d < bestDelta) {
                                bestDelta = d;
                                best = si;
                            }
                        }
                    }

                    if (best >= 0) {
                        assigned[pi] = best;
                        taken[best] = true;
                    }
                }
            }

            for (int pix = 0; pix < n; pix++) {
                if (assigned[pix] == -1) {
                    for (int six = 0; six < n; six++) {
                        if (!taken[six]) {
                            assigned[pix] = six;
                            taken[six] = true;
                            break;
                        }
                    }
                }
            }

            for (int ci = 0; ci < n; ci++) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(s.players.get(ci));
                if (p != null) {
                    double angle = slotAngle[assigned[ci] >= 0 ? assigned[ci] : ci];
                    double px = s.center.x + 1.0 * Math.cos(angle);
                    double pz = s.center.z + 1.0 * Math.sin(angle);
                    float yaw = (float)(-Math.toDegrees(Math.atan2(s.center.x - px, s.center.z - pz)));
                    p.teleport((ServerWorld)p.getWorld(), px, p.getY(), pz, yaw, 0.0F);
                    p.setYaw(yaw);
                    p.setBodyYaw(yaw);
                }
            }
        }
    }

    private static void tickSession(HuddleHandler.HuddleSession s, MinecraftServer server, long now) {
        ServerPlayerEntity p1 = server.getPlayerManager().getPlayer(s.p1);
        ServerPlayerEntity p2 = server.getPlayerManager().getPlayer(s.p2);
        if (p1 != null && p2 != null) {
            List<ServerPlayerEntity> live = new ArrayList<>();

            for (UUID uid : s.players) {
                ServerPlayerEntity lp = server.getPlayerManager().getPlayer(uid);
                if (lp == null) {
                    failHuddle(s, server);
                    return;
                }

                live.add(lp);
            }

            if (now - s.huddleStart > 20000L) {
                failHuddle(s, server);
            } else {
                switch (s.stage) {
                    case ENTERING:
                        if (s.elapsed() >= 542L) {
                            s.stage = HuddleHandler.HuddleStage.IDLE;
                            s.stageStart = now;
                            PoseNetworking.broadcastAnimState(p1, 71);
                            PoseNetworking.broadcastAnimState(p2, 71);
                        }
                        break;
                    case IDLE:
                        if (now - s.lastAuraTick >= 80L) {
                            s.lastAuraTick = now;
                            s.auraAngle += 0.35;
                            Vec3d center = s.center != null ? s.center : p1.getPos().add(p2.getPos()).multiply(0.5);
                            double r = 1.4;

                            for (int i = 0; i < 4; i++) {
                                double a = s.auraAngle + (Math.PI / 2) * i;
                                double px = center.x + r * Math.cos(a);
                                double pz = center.z + r * Math.sin(a);
                                ((ServerWorld)p1.getWorld()).spawnParticles(ParticleTypes.field_11207, px, center.y + 0.05, pz, 1, 0.0, 0.0, 0.0, 0.01);
                            }

                            double r2 = 0.7;

                            for (int i = 0; i < 3; i++) {
                                double a = -s.auraAngle * 1.5 + (Math.PI * 2.0 / 3.0) * i;
                                double px = center.x + r2 * Math.cos(a);
                                double pz = center.z + r2 * Math.sin(a);
                                ((ServerWorld)p1.getWorld()).spawnParticles(ParticleTypes.field_11208, px, center.y + 0.3, pz, 1, 0.0, 0.0, 0.0, 0.005);
                            }
                        }

                        if (s.elapsed() >= 900L) {
                            Vec3d midx = s.center != null
                                ? s.center.add(0.0, 1.0, 0.0)
                                : p1.getPos().add(p2.getPos()).multiply(0.5).add(0.0, 1.0, 0.0);
                            ServerWorld w0 = (ServerWorld)p1.getWorld();
                            w0.playSound(null, midx.x, midx.y, midx.z, SoundEvents.field_15016, SoundCategory.field_15248, 1.0F, 1.3F);
                            w0.playSound(null, midx.x, midx.y, midx.z, ModSounds.DAP_HIT, SoundCategory.field_15248, 0.9F, 1.4F);
                            w0.spawnParticles(ParticleTypes.field_11205, midx.x, midx.y, midx.z, 12, 0.25, 0.25, 0.25, 0.1);
                            w0.spawnParticles(ParticleTypes.field_11208, midx.x, midx.y, midx.z, 6, 0.2, 0.2, 0.2, 0.06);

                            for (UUID uid : s.players) {
                                ServerPlayerEntity lpx = server.getPlayerManager().getPlayer(uid);
                                if (lpx != null) {
                                    ServerPlayNetworking.send(lpx, new HuddleHandler.HuddleShakePayload(0.9F, 220));
                                }
                            }

                            startQTEStep(s, server, p1, p2, now);
                        }
                    case RELEASING:
                    default:
                        break;
                    case QTE:
                        if (!s.qteAnimStarted) {
                            return;
                        }

                        if (now - s.lastTapMs > 220L) {
                            s.bar = Math.max(0.0, s.bar - 0.016);
                        }

                        int wantStage = s.bar >= 0.68 ? 3 : (s.bar >= 0.34 ? 2 : 1);
                        if (wantStage != s.barAnimStage) {
                            s.barAnimStage = wantStage;

                            int anim = switch (wantStage) {
                                case 2 -> 77;
                                case 3 -> 78;
                                default -> 72;
                            };

                            for (ServerPlayerEntity lp : live) {
                                PoseNetworking.broadcastAnimState(lp, anim);
                            }

                            Vec3d sc = s.center.add(0.0, 1.1, 0.0);
                            ServerWorld sw = (ServerWorld)p1.getWorld();
                            switch (wantStage) {
                                case 2:
                                    sw.spawnParticles(ParticleTypes.field_11208, sc.x, sc.y, sc.z, 26, 0.5, 0.4, 0.5, 0.22);
                                    sw.spawnParticles(ParticleTypes.field_29643, sc.x, sc.y, sc.z, 18, 0.4, 0.4, 0.4, 0.16);
                                    break;
                                case 3:
                                    sw.spawnParticles(ParticleTypes.field_22246, sc.x, sc.y, sc.z, 30, 0.5, 0.5, 0.5, 0.2);
                                    sw.spawnParticles(ParticleTypes.field_28479, sc.x, sc.y, sc.z, 26, 0.45, 0.45, 0.45, 0.18);
                                    sw.spawnParticles(ParticleTypes.field_11207, sc.x, sc.y, sc.z, 20, 0.3, 0.6, 0.3, 0.26);
                                    break;
                                default:
                                    sw.spawnParticles(ParticleTypes.field_11205, sc.x, sc.y, sc.z, 20, 0.4, 0.35, 0.4, 0.18);
                                    sw.spawnParticles(ParticleTypes.field_29644, sc.x, sc.y, sc.z, 16, 0.35, 0.35, 0.35, 0.15);
                            }

                            for (ServerPlayerEntity lp : live) {
                                ServerPlayNetworking.send(lp, new HuddleHandler.HuddleShakePayload(0.55F + wantStage * 0.3F, 200));
                            }

                            Vec3d c = s.center;
                            ((ServerWorld)p1.getWorld())
                                .playSound(
                                    null,
                                    c.x,
                                    c.y,
                                    c.z,
                                    (SoundEvent)SoundEvents.field_14793.comp_349(),
                                    SoundCategory.field_15248,
                                    1.0F,
                                    0.9F + wantStage * 0.25F
                                );
                        }

                        float shake = (float)(0.1F + 0.75 * s.bar);
                        boolean pulse = now - s.lastShakeMs >= 160L;
                        if (pulse) {
                            s.lastShakeMs = now;
                        }

                        for (ServerPlayerEntity lp : live) {
                            ServerPlayNetworking.send(lp, new HuddleHandler.HuddleBarPayload((float)s.bar, s.players.size(), true));
                            if (pulse && s.bar > 0.05) {
                                ServerPlayNetworking.send(lp, new HuddleHandler.HuddleShakePayload(shake, 220));
                            }
                        }

                        Vec3d mid = s.center.add(0.0, 1.0, 0.0);
                        ServerWorld w = (ServerWorld)p1.getWorld();
                        int swirl = (int)Math.round(2.0 + s.bar * 10.0);
                        w.spawnParticles(ParticleTypes.field_29644, mid.x, mid.y, mid.z, swirl, 0.35, 0.35, 0.35, 0.1 + s.bar * 0.25);
                        if (s.bar > 0.5) {
                            w.spawnParticles(ParticleTypes.field_11207, mid.x, mid.y, mid.z, (int)(s.bar * 8.0), 0.25, 0.45, 0.25, 0.18);
                        }

                        if (s.bar >= 1.0) {
                            s.resetQTE();

                            for (ServerPlayerEntity lpx : live) {
                                PoseNetworking.broadcastAnimState(lpx, 73);
                                ServerPlayNetworking.send(lpx, new HuddleHandler.HuddleBarPayload(1.0F, s.players.size(), false));
                            }

                            finale(w, s.center, live.size());
                            RallyBeaconHandler.light(w, s.center, new HashSet<>(s.players));

                            for (ServerPlayerEntity lpx : live) {
                                int tier = Math.min(8, live.size());
                                lpx.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5904, 400, Math.min(2, tier / 3), false, true));
                                lpx.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5910, 400, Math.min(2, tier / 4), false, true));
                            }

                            successHuddle(s, server, live, now);
                            return;
                        }

                        if (s.bar <= 0.0 && s.elapsed() > 1500L) {
                            failHuddle(s, server);
                        }
                        break;
                    case ENDING:
                        if (!s.endMoveReleased && s.elapsed() >= 960L) {
                            s.endMoveReleased = true;

                            for (ServerPlayerEntity lpx : live) {
                                ServerPlayNetworking.send(lpx, new ChargedDapHandler.PerfectDapFreezePayload(false));
                                ServerPlayNetworking.send(lpx, new HuddleHandler.HuddleBarPayload(0.0F, 0, false));
                            }
                        }

                        if (s.elapsed() >= 1375L) {
                            for (ServerPlayerEntity lpx : live) {
                                ServerPlayNetworking.send(lpx, new ChargedDapHandler.PerfectDapFreezePayload(false));
                            }

                            cleanupSession(s, server);
                        }
                        break;
                    case DONE:
                        cleanupSession(s, server);
                }
            }
        } else {
            failHuddle(s, server);
        }
    }

    private static void finale(ServerWorld w, Vec3d center, int playerCount) {
        int tier = Math.min(8, Math.max(2, playerCount));
        double m = tier / 2.0;
        Vec3d hands = center.add(0.0, 1.1, 0.0);
        w.spawnParticles(ParticleTypes.field_11248, hands.x, hands.y, hands.z, (int)(30.0 * m), 0.5, 0.5, 0.5, 0.3 * m);
        w.spawnParticles(ParticleTypes.field_11220, hands.x, hands.y, hands.z, (int)(24.0 * m), 0.5, 0.5, 0.5, 0.28 * m);
        w.spawnParticles(ParticleTypes.field_11207, hands.x, hands.y, hands.z, (int)(20.0 * m), 0.4, 0.4, 0.4, 0.35 * m);
        w.spawnParticles(ParticleTypes.field_11236, hands.x, hands.y, hands.z, (int)(2.0 * m), 0.5, 0.5, 0.5, 0.0);
        w.playSound(null, center.x, center.y, center.z, SoundEvents.field_15188, SoundCategory.field_15248, 1.4F, 1.3F - tier * 0.04F);
        w.playSound(null, center.x, center.y, center.z, ModSounds.TRUE_FRIENDSHIP, SoundCategory.field_15248, 1.0F, 1.0F);
        MinecraftServer srv = w.getServer();
        if (srv != null) {
            int height = (int)(6.0 * m);
            new Thread(() -> {
                for (int step = 0; step <= height; step++) {
                    double y = hands.y + step * 0.55;
                    boolean top = step == height;
                    srv.execute(() -> {
                        w.spawnParticles(ParticleTypes.field_11207, hands.x, y, hands.z, (int)(6.0 * m), 0.14, 0.06, 0.14, 0.05);
                        w.spawnParticles(ParticleTypes.field_11248, hands.x, y, hands.z, (int)(4.0 * m), 0.1, 0.05, 0.1, 0.04);
                        if (top) {
                            w.spawnParticles(ParticleTypes.field_11221, hands.x, y, hands.z, (int)Math.max(1.0, m), 0.6, 0.3, 0.6, 0.0);
                            w.spawnParticles(ParticleTypes.field_11248, hands.x, y, hands.z, (int)(50.0 * m), 1.4, 1.0, 1.4, 0.55);
                            w.spawnParticles(ParticleTypes.field_11220, hands.x, y, hands.z, (int)(36.0 * m), 1.2, 0.9, 1.2, 0.45);
                            w.playSound(null, hands.x, y, hands.z, SoundEvents.field_14800, SoundCategory.field_15248, 1.6F, 1.0F);
                            w.playSound(null, hands.x, y, hands.z, ModSounds.EXPLOSION_IMPACT, SoundCategory.field_15248, 1.3F, 1.1F);
                        }
                    });

                    try {
                        Thread.sleep(35L);
                    } catch (InterruptedException var11) {
                        return;
                    }
                }
            }).start();
        }
    }

    private static void startQTEStep(HuddleHandler.HuddleSession s, MinecraftServer server, ServerPlayerEntity p1, ServerPlayerEntity p2, long now) {
        s.qteStep = 1;
        s.stage = HuddleHandler.HuddleStage.QTE;
        s.stageStart = now;
        s.resetQTE();
        s.qteAnimStarted = true;
        s.qteAnimStart = now;
        s.bar = 0.0;
        s.barAnimStage = 1;
        s.lastTapMs = now;

        for (UUID uid : s.players) {
            ServerPlayerEntity lp = server.getPlayerManager().getPlayer(uid);
            if (lp != null) {
                PoseNetworking.broadcastAnimState(lp, 72);
            }
        }
    }

    private static void successHuddle(HuddleHandler.HuddleSession s, MinecraftServer server, List<ServerPlayerEntity> live, long now) {
        s.stage = HuddleHandler.HuddleStage.ENDING;
        s.stageStart = now;
        cooldowns.put(key(s.p1, s.p2), now);

        for (ServerPlayerEntity lp : live) {
            ServerPlayNetworking.send(lp, new DapFusionHandler.FusionQTEPayload(lp.getUuid(), "G", 0, 0L, 0L, false, 0));
        }

        for (ServerPlayerEntity lp : live) {
            lp.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5924, 200, 2));
            lp.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5904, 400, 2));
            lp.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5910, 400, 1));
            lp.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5907, 200, 0));
            lp.experienceLevel += 2;
            lp.sendMessage(Text.literal("§d§l✦ HUDDLE! ✦ §7+Regen III, Speed III, Strength II, Resistance I"), true);
        }

        ServerPlayerEntity p1 = live.get(0);
        ServerPlayerEntity p2 = live.get(1);
        Vec3d center = s.center != null ? s.center : p1.getPos().add(p2.getPos()).multiply(0.5);
        ServerWorld flashWorld = (ServerWorld)p1.getWorld();
        flashWorld.playSound(null, center.x, center.y, center.z, SoundEvents.field_15057, SoundCategory.field_15248, 2.0F, 0.6F);
        flashWorld.playSound(null, center.x, center.y, center.z, SoundEvents.field_14709, SoundCategory.field_15248, 2.0F, 0.8F);
        flashWorld.playSound(null, center.x, center.y, center.z, SoundEvents.field_15195, SoundCategory.field_15248, 1.5F, 1.0F);
        final List<ServerPlayerEntity> liveFinal = new ArrayList<>(live);
        new Timer()
            .schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        server.execute(
                            () -> {
                                if (!liveFinal.stream().noneMatch(LivingEntity::isAlive)) {
                                    Vec3d sum = Vec3d.ZERO;

                                    for (ServerPlayerEntity lp : liveFinal) {
                                        sum = sum.add(lp.getPos());
                                    }

                                    Vec3d mid = sum.multiply(1.0 / liveFinal.size()).add(0.0, 1.0, 0.0);
                                    ServerWorld world = (ServerWorld)liveFinal.get(0).getWorld();
                                    world.spawnParticles(ParticleTypes.field_11211, mid.x, mid.y, mid.z, 80, 0.8, 0.8, 0.8, 0.5);
                                    world.spawnParticles(ParticleTypes.field_17741, mid.x, mid.y, mid.z, 60, 0.6, 0.6, 0.6, 0.4);
                                    world.spawnParticles(ParticleTypes.field_11220, mid.x, mid.y, mid.z, 40, 0.6, 0.6, 0.6, 0.3);
                                    world.spawnParticles(ParticleTypes.field_17909, mid.x, mid.y, mid.z, 3, 0.1, 0.1, 0.1, 0.0);
                                    world.spawnParticles(ParticleTypes.field_11248, mid.x, mid.y, mid.z, 40, 0.5, 0.6, 0.5, 0.25);
                                    world.spawnParticles(ParticleTypes.field_11205, mid.x, mid.y - 0.5, mid.z, 20, 1.0, 0.0, 1.0, 0.05);
                                    world.spawnParticles(ParticleTypes.field_11201, mid.x, mid.y, mid.z, 15, 0.6, 0.4, 0.6, 0.1);

                                    for (double a = 0.0; a < Math.PI * 2; a += 0.4) {
                                        world.spawnParticles(
                                            ParticleTypes.field_11211,
                                            mid.x + Math.cos(a) * 1.5,
                                            mid.y - 0.9,
                                            mid.z + Math.sin(a) * 1.5,
                                            2,
                                            0.0,
                                            0.1,
                                            0.0,
                                            0.05
                                        );
                                    }

                                    world.playSound(
                                        null, mid.x, mid.y, mid.z, SoundEvents.field_14709, SoundCategory.field_15248, 2.0F, 1.2F
                                    );
                                    world.playSound(
                                        null, mid.x, mid.y, mid.z, SoundEvents.field_15195, SoundCategory.field_15248, 1.5F, 1.2F
                                    );
                                    world.playSound(
                                        null,
                                        mid.x,
                                        mid.y,
                                        mid.z,
                                        (SoundEvent)SoundEvents.field_14793.comp_349(),
                                        SoundCategory.field_15248,
                                        1.5F,
                                        2.0F
                                    );
                                    ItemStack fw = new ItemStack(Items.field_8639);

                                    for (int ri = 0; ri < 5; ri++) {
                                        double ox = (HuddleHandler.RANDOM.nextDouble() - 0.5) * 1.2;
                                        double oz = (HuddleHandler.RANDOM.nextDouble() - 0.5) * 1.2;
                                        world.spawnEntity(new FireworkRocketEntity(world, mid.x + ox, mid.y + 0.5, mid.z + oz, fw));
                                    }
                                }
                            }
                        );
                    }
                },
                500L
            );
        new Timer()
            .schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        server.execute(
                            () -> {
                                if (!liveFinal.isEmpty() && liveFinal.get(0).isAlive()) {
                                    Vec3d sum2 = Vec3d.ZERO;

                                    for (ServerPlayerEntity lp : liveFinal) {
                                        sum2 = sum2.add(lp.getPos());
                                    }

                                    final Vec3d mid = sum2.multiply(1.0 / liveFinal.size()).add(0.0, 1.0, 0.0);
                                    final ServerWorld world = (ServerWorld)liveFinal.get(0).getWorld();

                                    for (int pulse = 0; pulse < 4; pulse++) {
                                        final int pp = pulse;
                                        new Timer()
                                            .schedule(
                                                new TimerTask() {
                                                    @Override
                                                    public void run() {
                                                        server.execute(
                                                            () -> {
                                                                float fade = 1.0F - pp * 0.25F;
                                                                int cnt = (int)(20.0F * fade);
                                                                world.spawnParticles(
                                                                    ParticleTypes.field_11211,
                                                                    mid.x,
                                                                    mid.y,
                                                                    mid.z,
                                                                    cnt,
                                                                    0.9 * fade,
                                                                    0.7 * fade,
                                                                    0.9 * fade,
                                                                    0.15
                                                                );
                                                                world.spawnParticles(
                                                                    ParticleTypes.field_17741,
                                                                    mid.x,
                                                                    mid.y,
                                                                    mid.z,
                                                                    cnt / 2,
                                                                    0.7 * fade,
                                                                    0.6 * fade,
                                                                    0.7 * fade,
                                                                    0.1
                                                                );
                                                            }
                                                        );
                                                    }
                                                },
                                                pp * 250
                                            );
                                    }
                                }
                            }
                        );
                    }
                },
                1875L
            );

        for (ServerPlayerEntity lp : live) {
            ServerPlayNetworking.send(lp, new HuddleHandler.HuddleEndPayload(s.p1, s.p2, true));
        }
    }

    private static void failHuddle(HuddleHandler.HuddleSession s, MinecraftServer server) {
        cooldowns.put(key(s.p1, s.p2), System.currentTimeMillis());
        if (server == null) {
            cleanupSession(s, server);
        } else {
            List<ServerPlayerEntity> live = new ArrayList<>();

            for (UUID uid : s.players) {
                ServerPlayerEntity lp = server.getPlayerManager().getPlayer(uid);
                if (lp != null) {
                    live.add(lp);
                }
            }

            for (ServerPlayerEntity lp : live) {
                ServerPlayNetworking.send(lp, new DapFusionHandler.FusionQTEPayload(lp.getUuid(), "G", 0, 0L, 0L, false, 0));
                PoseNetworking.broadcastAnimState(lp, 0);
                lp.removeStatusEffect(StatusEffects.field_5904);
                lp.removeStatusEffect(StatusEffects.field_5910);
                lp.sendMessage(Text.literal("§c✗ Huddle failed!"), true);
                ServerPlayNetworking.send(lp, new HuddleHandler.HuddleEndPayload(s.p1, s.p2, false));
            }

            if (live.size() >= 2) {
                Vec3d center = s.center != null ? s.center : live.get(0).getPos().add(live.get(1).getPos()).multiply(0.5);

                for (ServerPlayerEntity lp : live) {
                    Vec3d dir = lp.getPos().subtract(center).normalize();
                    lp.addVelocity(dir.x * 0.6, 0.4, dir.z * 0.6);
                    lp.velocityModified = true;
                }

                ((ServerWorld)live.get(0).getWorld())
                    .spawnParticles(ParticleTypes.field_11231, center.x, center.y + 1.0, center.z, 8, 0.3, 0.3, 0.3, 0.05);
                ((ServerWorld)live.get(0).getWorld())
                    .playSound(null, center.x, center.y, center.z, SoundEvents.field_14986, SoundCategory.field_15248, 0.7F, 1.5F);
            }

            cleanupSession(s, server);
        }
    }

    private static void cleanupSession(HuddleHandler.HuddleSession s, MinecraftServer server) {
        String k = key(s.p1, s.p2);
        sessions.remove(k);

        for (UUID uid : s.players) {
            playerSession.remove(uid);
            joinerEnterMs.remove(uid);
            if (server != null) {
                ServerPlayerEntity lp = server.getPlayerManager().getPlayer(uid);
                if (lp != null) {
                    ServerPlayNetworking.send(lp, new ChargedDapHandler.PerfectDapFreezePayload(false));
                }
            }
        }
    }

    public static boolean isInHuddle(UUID id) {
        return playerSession.containsKey(id);
    }

    public static void cleanup(UUID id, MinecraftServer server) {
        fHoldStart.remove(id);
        String k = playerSession.get(id);
        HuddleHandler.HuddleSession s = k != null ? sessions.get(k) : null;
        if (s != null && server != null) {
            if (!id.equals(s.p1) && !id.equals(s.p2) && s.players.size() > 2) {
                s.players.remove(id);
                s.holdsF.remove(id);
                s.cancelArmed.remove(id);
                playerSession.remove(id);
                joinerEnterMs.remove(id);
                ServerPlayerEntity leaver = server.getPlayerManager().getPlayer(id);
                if (leaver != null) {
                    PoseNetworking.broadcastAnimState(leaver, 0);
                    ServerPlayNetworking.send(leaver, new HuddleHandler.HuddleBarPayload(0.0F, 0, false));
                    ServerPlayNetworking.send(leaver, new HuddleHandler.HuddleEndPayload(s.p1, s.p2, false));
                    ServerPlayNetworking.send(leaver, new ChargedDapHandler.PerfectDapFreezePayload(false));
                }

                smoothRepositionAll(s, server);
            } else {
                failHuddle(s, server);
            }
        } else {
            cleanup(id);
        }
    }

    public static void cleanup(UUID id) {
        fHoldStart.remove(id);
        String k = playerSession.remove(id);
        if (k != null) {
            HuddleHandler.HuddleSession s = sessions.remove(k);
            if (s != null) {
                playerSession.remove(s.p1.equals(id) ? s.p2 : s.p1);
            }
        }
    }

    public record HuddleBarPayload(float fill, int players, boolean active) implements CustomPayload {
        public static final Id<HuddleHandler.HuddleBarPayload> ID = new Id(new Identifier("testcoop", "huddle_bar"));
        public static final PacketCodec<PacketByteBuf, HuddleHandler.HuddleBarPayload> CODEC = PacketCodec.of((v, buf) -> {
            buf.writeFloat(v.fill());
            buf.writeInt(v.players());
            buf.writeBoolean(v.active());
        }, buf -> new HuddleHandler.HuddleBarPayload(buf.readFloat(), buf.readInt(), buf.readBoolean()));

        public Id<HuddleHandler.HuddleBarPayload> getId() {
            return ID;
        }
    }

    public record HuddleCancelPayload() implements CustomPayload {
        public static final Id<HuddleHandler.HuddleCancelPayload> ID = new Id(new Identifier("testcoop", "huddle_cancel"));
        public static final PacketCodec<PacketByteBuf, HuddleHandler.HuddleCancelPayload> CODEC = PacketCodec.unit(new HuddleHandler.HuddleCancelPayload());

        public Id<HuddleHandler.HuddleCancelPayload> getId() {
            return ID;
        }
    }

    public record HuddleEndPayload(UUID p1, UUID p2, boolean success) implements CustomPayload {
        public static final Id<HuddleHandler.HuddleEndPayload> ID = new Id(new Identifier("testcoop", "huddle_end_result"));
        public static final PacketCodec<PacketByteBuf, HuddleHandler.HuddleEndPayload> CODEC = PacketCodec.of((val, buf) -> {
            buf.writeUuid(val.p1());
            buf.writeUuid(val.p2());
            buf.writeBoolean(val.success());
        }, buf -> new HuddleHandler.HuddleEndPayload(buf.readUuid(), buf.readUuid(), buf.readBoolean()));

        public Id<HuddleHandler.HuddleEndPayload> getId() {
            return ID;
        }
    }

    public record HuddleFHoldPayload(boolean holding) implements CustomPayload {
        public static final Id<HuddleHandler.HuddleFHoldPayload> ID = new Id(new Identifier("testcoop", "huddle_f_hold"));
        public static final PacketCodec<PacketByteBuf, HuddleHandler.HuddleFHoldPayload> CODEC = PacketCodec.of(
            (val, buf) -> buf.writeBoolean(val.holding()), buf -> new HuddleHandler.HuddleFHoldPayload(buf.readBoolean())
        );

        public Id<HuddleHandler.HuddleFHoldPayload> getId() {
            return ID;
        }
    }

    private static class HuddleSession {
        final List<UUID> players;
        final UUID p1;
        final UUID p2;
        HuddleHandler.HuddleStage stage = HuddleHandler.HuddleStage.ENTERING;
        long stageStart = System.currentTimeMillis();
        long huddleStart = System.currentTimeMillis();
        Vec3d center;
        double baseAngle = 0.0;
        Set<UUID> holdsF;
        Long firstRelease = null;
        int qteStep = 0;
        String qteBtn1 = "G";
        String qteBtn2 = "H";
        String qteBtn3 = "G";
        Set<UUID> qteHits = new HashSet<>();
        boolean qteOpen = false;
        boolean qteAnimStarted = false;
        long qteAnimStart = 0L;
        double bar = 0.0;
        long lastTapMs = 0L;
        int barAnimStage = 1;
        long lastShakeMs = 0L;
        long lastSyncBonus = 0L;
        boolean endMoveReleased = false;
        Map<UUID, Long> lastTapByPlayer = new HashMap<>();
        Set<UUID> cancelArmed = new HashSet<>();
        long lastAuraTick = 0L;
        double auraAngle = 0.0;
        int stepsHit = 0;

        HuddleSession(List<UUID> playerList) {
            this.players = new ArrayList<>(playerList);
            this.p1 = playerList.get(0);
            this.p2 = playerList.get(1);
            this.holdsF = new HashSet<>(playerList);
            this.qteBtn1 = HuddleHandler.RANDOM.nextBoolean() ? "G" : "H";
            this.qteBtn2 = this.qteBtn1.equals("G") ? "H" : "G";
            this.qteBtn3 = HuddleHandler.RANDOM.nextBoolean() ? "G" : "H";
        }

        void resetQTE() {
            this.qteHits.clear();
            this.qteOpen = false;
            this.qteAnimStarted = false;
        }

        boolean allHit() {
            return this.qteHits.containsAll(this.players);
        }

        boolean allHoldF() {
            return this.holdsF.containsAll(this.players);
        }

        boolean anyReleasedF() {
            return !this.holdsF.containsAll(this.players);
        }

        long elapsed() {
            return System.currentTimeMillis() - this.stageStart;
        }

        String expectedButton() {
            return switch (this.qteStep) {
                case 1 -> this.qteBtn1;
                case 2 -> this.qteBtn2;
                case 3 -> this.qteBtn3;
                default -> "G";
            };
        }
    }

    public record HuddleShakePayload(float amount, int durationMs) implements CustomPayload {
        public static final Id<HuddleHandler.HuddleShakePayload> ID = new Id(new Identifier("testcoop", "huddle_shake"));
        public static final PacketCodec<PacketByteBuf, HuddleHandler.HuddleShakePayload> CODEC = PacketCodec.of((v, buf) -> {
            buf.writeFloat(v.amount());
            buf.writeInt(v.durationMs());
        }, buf -> new HuddleHandler.HuddleShakePayload(buf.readFloat(), buf.readInt()));

        public Id<HuddleHandler.HuddleShakePayload> getId() {
            return ID;
        }
    }

    public record HuddleShiftPayload(boolean holding) implements CustomPayload {
        public static final Id<HuddleHandler.HuddleShiftPayload> ID = new Id(new Identifier("testcoop", "huddle_shift"));
        public static final PacketCodec<PacketByteBuf, HuddleHandler.HuddleShiftPayload> CODEC = PacketCodec.of(
            (v, buf) -> buf.writeBoolean(v.holding()), buf -> new HuddleHandler.HuddleShiftPayload(buf.readBoolean())
        );

        public Id<HuddleHandler.HuddleShiftPayload> getId() {
            return ID;
        }
    }

    private static enum HuddleStage {
        ENTERING,
        IDLE,
        RELEASING,
        QTE,
        ENDING,
        DONE;
    }

    public record HuddleTapPayload() implements CustomPayload {
        public static final Id<HuddleHandler.HuddleTapPayload> ID = new Id(new Identifier("testcoop", "huddle_tap"));
        public static final PacketCodec<PacketByteBuf, HuddleHandler.HuddleTapPayload> CODEC = PacketCodec.unit(new HuddleHandler.HuddleTapPayload());

        public Id<HuddleHandler.HuddleTapPayload> getId() {
            return ID;
        }
    }
}
