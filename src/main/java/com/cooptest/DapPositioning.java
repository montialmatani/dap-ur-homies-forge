package com.cooptest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public class DapPositioning {
    public static final double DAP_SEPARATION = 1.35;
    public static final double DAP_RIGHT_OFFSET = 0.0;
    public static final double DAP_DISTANCE_DEADZONE = 0.35;
    public static final long DAP_SLIDE_MS = 200L;
    public static final double WINDUP_BLOCKS = 0.0;
    public static final long WINDUP_BEFORE_MS = 100L;
    public static final long WINDUP_SLIDE_MS = 80L;
    public static final int YAW_LERP_TICKS = 5;
    public static final long IMPACT_AT_MS = 250L;
    public static final long PERFECT_IMPACT_AT_MS = 210L;
    public static final long MOVE_RELEASE_AT_MS = 540L;
    public static final long PERFECT_MOVE_RELEASE_AT_MS = 1210L;
    public static final long RELEASE_WINDOW_MS = 1200L;
    public static final double MAX_SLIDE = 2.0;
    public static final double MAX_FORWARD_PUSH = 0.4;
    public static final int ANIM_DAP_END = 125;
    public static final int ANIM_NONE = 0;
    private static final List<DapPositioning.PendingSlide> pendingSlide = new ArrayList<>();
    private static final Map<UUID, UUID> releasePartner = new HashMap<>();
    private static final Map<UUID, Long> releaseOpensAt = new HashMap<>();
    private static final Map<UUID, Long> releaseExpiry = new HashMap<>();
    private static final List<DapPositioning.YawStep> yawSteps = new ArrayList<>();

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(DapPositioning.DapReleasePayload.ID, DapPositioning.DapReleasePayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(DapPositioning.DapReleasePayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            ctx.server().execute(() -> onRelease(player));
        });
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            drainSlides();
            expireWindows();
        });
    }

    public static void closeIn(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        long now = System.currentTimeMillis();
        double dist = Math.hypot(p2.getPos().x - p1.getPos().x, p2.getPos().z - p1.getPos().z);
        if (!(Math.abs(dist - 1.35) <= 0.35)) {
            Vec3d[] targets = computeTargets(p1, p2, 1.35, 0.0);
            long closeAt = now + 100L;
            queueTo(p1, p2, targets[0], msToTicks(200L), closeAt);
            queueTo(p2, p1, targets[1], msToTicks(200L), closeAt);
        }
    }

    public static void faceEachOther(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        scheduleYawLerp(p1, p2);
        scheduleYawLerp(p2, p1);
    }

    private static void scheduleYawLerp(ServerPlayerEntity player, ServerPlayerEntity target) {
        Vec3d to = target.getPos().subtract(player.getPos());
        float targetYaw = (float)Math.toDegrees(Math.atan2(-to.x, to.z));
        float startYaw = player.getYaw();
        float delta = MathHelper.wrapDegrees(targetYaw - startYaw);

        for (int i = 1; i <= 5; i++) {
            float t = i / 5.0F;
            yawSteps.add(new DapPositioning.YawStep(player, startYaw + delta * t, System.currentTimeMillis() + i * 50L));
        }
    }

    public static void openReleaseWindow(ServerPlayerEntity p1, ServerPlayerEntity p2, boolean perfect) {
        long opensAt = System.currentTimeMillis() + (perfect ? 1210L : 540L);
        long expiry = opensAt + 1200L;

        for (ServerPlayerEntity p : new ServerPlayerEntity[]{p1, p2}) {
            UUID id = p.getUuid();
            releasePartner.put(id, id.equals(p1.getUuid()) ? p2.getUuid() : p1.getUuid());
            releaseOpensAt.put(id, opensAt);
            releaseExpiry.put(id, expiry);
        }
    }

    public static void openReleaseWindow(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        openReleaseWindow(p1, p2, false);
    }

    public static boolean impactHappened(UUID playerId) {
        Long opensAt = releaseOpensAt.get(playerId);
        return opensAt != null && System.currentTimeMillis() >= opensAt;
    }

    private static void onRelease(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        UUID partnerId = releasePartner.get(id);
        if (partnerId != null) {
            long now = System.currentTimeMillis();
            Long opensAt = releaseOpensAt.get(id);
            if (opensAt != null && now >= opensAt) {
                Long expiry = releaseExpiry.get(id);
                if (expiry != null && now <= expiry) {
                    ServerPlayerEntity partner = player.getServer().getPlayerManager().getPlayer(partnerId);
                    PoseNetworking.broadcastAnimState(player, 125);
                    if (partner != null) {
                        PoseNetworking.broadcastAnimState(partner, 125);
                    }

                    clear(id);
                    clear(partnerId);
                } else {
                    clear(id);
                }
            }
        }
    }

    private static void expireWindows() {
        long now = System.currentTimeMillis();
        releaseExpiry.entrySet().removeIf(e -> {
            if (now > e.getValue()) {
                releasePartner.remove(e.getKey());
                return true;
            } else {
                return false;
            }
        });
    }

    private static void clear(UUID id) {
        releasePartner.remove(id);
        releaseOpensAt.remove(id);
        releaseExpiry.remove(id);
    }

    public static void cleanup(UUID playerId) {
        UUID partner = releasePartner.get(playerId);
        clear(playerId);
        if (partner != null) {
            clear(partner);
        }

        pendingSlide.removeIf(s -> s.player.getUuid().equals(playerId));
        yawSteps.removeIf(s -> s.player().getUuid().equals(playerId));
    }

    private static void drainSlides() {
        long now = System.currentTimeMillis();
        Iterator<DapPositioning.PendingSlide> it = pendingSlide.iterator();

        while (it.hasNext()) {
            DapPositioning.PendingSlide s = it.next();
            if (now >= s.startAtMs) {
                if (s.remainingTicks <= 0) {
                    it.remove();
                } else {
                    if (s.partner != null) {
                        Vec3d a = s.player.getPos();
                        Vec3d b = s.partner.getPos();
                        if (Math.hypot(b.x - a.x, b.z - a.z) <= 1.35) {
                            it.remove();
                            continue;
                        }
                    }

                    Vec3d step = s.remainingDelta.multiply(1.0 / s.remainingTicks);
                    Vec3d pos = s.player.getPos();
                    s.player.requestTeleport(pos.x + step.x, pos.y, pos.z + step.z);
                    s.remainingDelta = s.remainingDelta.subtract(step);
                    s.remainingTicks--;
                    if (s.remainingTicks <= 0) {
                        it.remove();
                    }
                }
            }
        }

        Iterator<DapPositioning.YawStep> yit = yawSteps.iterator();

        while (yit.hasNext()) {
            DapPositioning.YawStep y = yit.next();
            if (now >= y.atMs()) {
                float yaw = MathHelper.wrapDegrees(y.yaw());
                y.player().setYaw(yaw);
                y.player().setHeadYaw(yaw);
                y.player().setBodyYaw(yaw);
                yit.remove();
            }
        }
    }

    private static Vec3d between(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        Vec3d d = p2.getPos().subtract(p1.getPos());
        double dist = Math.hypot(d.x, d.z);
        return dist < 0.01 ? HighFiveHandler.horizontalForward(p1) : new Vec3d(d.x / dist, 0.0, d.z / dist);
    }

    private static Vec3d[] computeTargets(ServerPlayerEntity p1, ServerPlayerEntity p2, double separation, double rightOffset) {
        Vec3d pos1 = p1.getPos();
        Vec3d pos2 = p2.getPos();
        Vec3d toP2 = pos2.subtract(pos1);
        double dist = Math.hypot(toP2.x, toP2.z);
        if (dist < 0.01) {
            toP2 = HighFiveHandler.horizontalForward(p1);
            dist = 1.0;
        }

        Vec3d dir12 = new Vec3d(toP2.x / dist, 0.0, toP2.z / dist);
        Vec3d dir21 = dir12.negate();
        double fwdStep = Math.max(0.0, Math.min((dist - separation) / 2.0, 0.4));
        Vec3d right1 = rightOffset == 0.0 ? Vec3d.ZERO : rightOf(HighFiveHandler.horizontalForward(p1));
        Vec3d right2 = rightOffset == 0.0 ? Vec3d.ZERO : rightOf(HighFiveHandler.horizontalForward(p2));
        return new Vec3d[]{
            new Vec3d(
                pos1.x + dir12.x * fwdStep + right1.x * rightOffset,
                pos1.y,
                pos1.z + dir12.z * fwdStep + right1.z * rightOffset
            ),
            new Vec3d(
                pos2.x + dir21.x * fwdStep + right2.x * rightOffset,
                pos2.y,
                pos2.z + dir21.z * fwdStep + right2.z * rightOffset
            )
        };
    }

    private static Vec3d rightOf(Vec3d fwd) {
        return new Vec3d(fwd.z, 0.0, -fwd.x);
    }

    public static void slideBy(ServerPlayerEntity player, Vec3d delta, int ticks) {
        queue(player, null, new Vec3d(delta.x, 0.0, delta.z), ticks, System.currentTimeMillis());
    }

    private static void queueTo(ServerPlayerEntity player, ServerPlayerEntity partner, Vec3d to, int ticks, long startAtMs) {
        Vec3d from = player.getPos();
        queue(player, partner, new Vec3d(to.x - from.x, 0.0, to.z - from.z), ticks, startAtMs);
    }

    private static void queue(ServerPlayerEntity player, Vec3d delta, int ticks, long startAtMs) {
        queue(player, null, delta, ticks, startAtMs);
    }

    private static void queue(ServerPlayerEntity player, ServerPlayerEntity partner, Vec3d delta, int ticks, long startAtMs) {
        if (!(Math.hypot(delta.x, delta.z) > 2.0)) {
            pendingSlide.add(new DapPositioning.PendingSlide(player, partner, delta, ticks, startAtMs));
        }
    }

    private static int msToTicks(long ms) {
        return (int)Math.max(1L, ms / 50L);
    }

    public record DapReleasePayload() implements CustomPayload {
        public static final Id<DapPositioning.DapReleasePayload> ID = new Id(new Identifier("cooptest", "dap_release"));
        public static final PacketCodec<PacketByteBuf, DapPositioning.DapReleasePayload> CODEC = PacketCodec.unit(new DapPositioning.DapReleasePayload());

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static final class PendingSlide {
        final ServerPlayerEntity player;
        final ServerPlayerEntity partner;
        Vec3d remainingDelta;
        int remainingTicks;
        long startAtMs;

        PendingSlide(ServerPlayerEntity player, ServerPlayerEntity partner, Vec3d totalDelta, int ticks, long startAtMs) {
            this.player = player;
            this.partner = partner;
            this.remainingDelta = totalDelta;
            this.remainingTicks = Math.max(1, ticks);
            this.startAtMs = startAtMs;
        }
    }

    private record YawStep(ServerPlayerEntity player, float yaw, long atMs) {
    }
}
