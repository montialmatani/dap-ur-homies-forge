package com.cooptest.highfive;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDamage;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.player.AttackBlockCallback;
import com.cooptest.fabricshim.fabric.api.event.player.AttackEntityCallback;
import com.cooptest.fabricshim.fabric.api.event.player.UseBlockCallback;
import com.cooptest.fabricshim.fabric.api.event.player.UseEntityCallback;
import com.cooptest.fabricshim.fabric.api.event.player.UseItemCallback;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
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

public class ReadyHugHandler {
    public static final long HOLD_REQUIRED_MS = 1000L;
    public static final double HUG_RANGE = 2.5;
    public static final double FACING_DOT_THRESHOLD = 0.3;
    public static final double HUG_SEPARATION = 0.8;
    public static final double MAX_SEPARATION = 2.0;
    public static final long CANCEL_GRACE_MS = 600L;
    public static final long HOLD_TIMEOUT_MS = 400L;
    public static final long EFFECT_INTERVAL_MS = 1000L;
    private static final int ANIM_NONE = 0;
    private static final int ANIM_HUG_START = 32;
    private static final int ANIM_HUGGING = 33;
    private static final int ANIM_HUGGING2 = 34;
    private static final int ANIM_HUG_END = 35;
    public static final long START_ANIM_MS = 333L;
    public static final long END_ANIM_MS = 542L;
    private static final Map<UUID, ReadyHugHandler.Session> sessions = new HashMap<>();
    private static final Map<UUID, Long> shiftHeldSince = new HashMap<>();
    private static final Map<UUID, Long> shiftLastSeen = new HashMap<>();
    private static final Map<UUID, Long> cancelRequested = new HashMap<>();

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(ReadyHugHandler.HugShiftPayload.ID, ReadyHugHandler.HugShiftPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReadyHugHandler.HugSessionPayload.ID, ReadyHugHandler.HugSessionPayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ReadyHugHandler.HugShiftPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            ctx.server().execute(() -> onShift(player, payload.holding(), payload.rightClick()));
        });
        ServerTickEvents.END_SERVER_TICK.register(ReadyHugHandler::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((AllowDamage)(entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p && isHugging(p.getUuid())) {
                MinecraftServer server = p.getServer();
                if (server != null) {
                    hardCancel(server, p.getUuid());
                }
            }

            return true;
        });
        AttackEntityCallback.EVENT.register((AttackEntityCallback)(player, world, hand, entity, hit) -> blockIfHugging(world.isClient(), player));
        AttackBlockCallback.EVENT.register((AttackBlockCallback)(player, world, hand, pos, dir) -> blockIfHugging(world.isClient(), player));
        UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hit) -> blockIfHugging(world.isClient(), player));
        UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hit) -> blockIfHugging(world.isClient(), player));
        UseItemCallback.EVENT.register((UseItemCallback)(player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (world.isClient()) {
                return TypedActionResult.pass(stack);
            } else {
                return isHugging(player.getUuid()) ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
            }
        });
    }

    private static ActionResult blockIfHugging(boolean isClient, PlayerEntity player) {
        if (isClient) {
            return ActionResult.PASS;
        } else {
            return isHugging(player.getUuid()) ? ActionResult.FAIL : ActionResult.PASS;
        }
    }

    private static void onShift(ServerPlayerEntity player, boolean holding, boolean rightClick) {
        UUID id = player.getUuid();
        long now = System.currentTimeMillis();
        if (rightClick) {
            shiftHeldSince.remove(id);
            shiftLastSeen.remove(id);
        } else if (!holding) {
            shiftHeldSince.remove(id);
            shiftLastSeen.remove(id);
        } else {
            shiftLastSeen.put(id, now);
            ReadyHugHandler.Session s = sessions.get(id);
            if (s != null) {
                if (s.phase == ReadyHugHandler.Phase.HUGGING && now - s.phaseStart > 600L) {
                    cancelRequested.put(id, now);
                }
            } else if (PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE) != PoseState.GRAB_READY) {
                shiftHeldSince.remove(id);
            } else {
                shiftHeldSince.putIfAbsent(id, now);
            }
        }
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        shiftLastSeen.entrySet().removeIf(e -> {
            if (now - e.getValue() > 400L) {
                shiftHeldSince.remove(e.getKey());
                return true;
            } else {
                return false;
            }
        });
        tryPair(server, now);
        tickSessions(server, now);
    }

    private static void tryPair(MinecraftServer server, long now) {
        if (shiftHeldSince.size() >= 2) {
            List<UUID> ids = new ArrayList<>(shiftHeldSince.keySet());

            for (int i = 0; i < ids.size(); i++) {
                for (int j = i + 1; j < ids.size(); j++) {
                    UUID ia = ids.get(i);
                    UUID ib = ids.get(j);
                    if (!sessions.containsKey(ia) && !sessions.containsKey(ib)) {
                        Long ta = shiftHeldSince.get(ia);
                        Long tb = shiftHeldSince.get(ib);
                        if (ta != null && tb != null && now - ta >= 1000L && now - tb >= 1000L) {
                            ServerPlayerEntity pa = server.getPlayerManager().getPlayer(ia);
                            ServerPlayerEntity pb = server.getPlayerManager().getPlayer(ib);
                            if (pa != null && pb != null && pa.getWorld() == pb.getWorld() && !(pa.distanceTo(pb) > 2.5) && facingEachOther(pa, pb)) {
                                start(server, pa, pb);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean facingEachOther(ServerPlayerEntity a, ServerPlayerEntity b) {
        Vec3d toB = b.getPos().subtract(a.getPos());
        if (toB.horizontalLengthSquared() < 1.0E-6) {
            return false;
        } else {
            Vec3d dir = new Vec3d(toB.x, 0.0, toB.z).normalize();
            return forward(a).dotProduct(dir) >= 0.3 && forward(b).dotProduct(dir.multiply(-1.0)) >= 0.3;
        }
    }

    private static Vec3d forward(ServerPlayerEntity p) {
        double yaw = Math.toRadians(p.getYaw());
        return new Vec3d(-Math.sin(yaw), 0.0, Math.cos(yaw)).normalize();
    }

    private static void start(MinecraftServer server, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (p1.getUuid().compareTo(p2.getUuid()) > 0) {
            ServerPlayerEntity t = p1;
            p1 = p2;
            p2 = t;
        }

        UUID ia = p1.getUuid();
        UUID ib = p2.getUuid();
        ReadyHugHandler.Session s = new ReadyHugHandler.Session(ia, ib);
        sessions.put(ia, s);
        sessions.put(ib, s);
        shiftHeldSince.remove(ia);
        shiftHeldSince.remove(ib);
        cancelRequested.remove(ia);
        cancelRequested.remove(ib);
        PoseNetworking.broadcastAnimState(p1, 32);
        PoseNetworking.broadcastAnimState(p2, 32);
        ServerPlayNetworking.send(p1, new ReadyHugHandler.HugSessionPayload(true, ib));
        ServerPlayNetworking.send(p2, new ReadyHugHandler.HugSessionPayload(true, ia));
        pullTogether(p1, p2);
    }

    private static void tickSessions(MinecraftServer server, long now) {
        for (ReadyHugHandler.Session s : new ArrayList<>(new HashSet<>(sessions.values()))) {
            ServerPlayerEntity a = server.getPlayerManager().getPlayer(s.a);
            ServerPlayerEntity b = server.getPlayerManager().getPlayer(s.b);
            if (a != null && b != null && a.isAlive() && b.isAlive()) {
                long elapsed = now - s.phaseStart;
                switch (s.phase) {
                    case STARTING:
                        if (poseOf(s.a) == PoseState.GRAB_READY && poseOf(s.b) == PoseState.GRAB_READY) {
                            if (elapsed >= 333L) {
                                s.phase = ReadyHugHandler.Phase.HUGGING;
                                s.phaseStart = now;
                                boolean swap = new Random().nextBoolean();
                                PoseNetworking.broadcastAnimState(a, swap ? 33 : 34);
                                PoseNetworking.broadcastAnimState(b, swap ? 34 : 33);
                            }
                            break;
                        }

                        endHug(server, s, a, b, now);
                        break;
                    case HUGGING:
                        if (!cancelRequested.containsKey(s.a) && !cancelRequested.containsKey(s.b)) {
                            if (poseOf(s.a) == PoseState.GRAB_READY && poseOf(s.b) == PoseState.GRAB_READY) {
                                if (a.distanceTo(b) > 2.0) {
                                    endHug(server, s, a, b, now);
                                } else {
                                    pullTogether(a, b);
                                    if (now - s.lastEffect >= 1000L) {
                                        s.lastEffect = now;
                                        applyEffects(a, b);
                                    }
                                }
                                break;
                            }

                            endHug(server, s, a, b, now);
                            break;
                        }

                        endHug(server, s, a, b, now);
                        break;
                    case ENDING:
                        if (elapsed >= 542L) {
                            finish(server, s, a, b);
                        }
                }
            } else {
                hardCancel(server, s.a);
            }
        }
    }

    private static void endHug(MinecraftServer server, ReadyHugHandler.Session s, ServerPlayerEntity a, ServerPlayerEntity b, long now) {
        s.phase = ReadyHugHandler.Phase.ENDING;
        s.phaseStart = now;
        cancelRequested.remove(s.a);
        cancelRequested.remove(s.b);
        PoseNetworking.broadcastAnimState(a, 35);
        PoseNetworking.broadcastAnimState(b, 35);
        ServerPlayNetworking.send(a, new ReadyHugHandler.HugSessionPayload(false, s.b));
        ServerPlayNetworking.send(b, new ReadyHugHandler.HugSessionPayload(false, s.a));
    }

    private static void finish(MinecraftServer server, ReadyHugHandler.Session s, ServerPlayerEntity a, ServerPlayerEntity b) {
        sessions.remove(s.a);
        sessions.remove(s.b);

        for (ServerPlayerEntity p : new ServerPlayerEntity[]{a, b}) {
            if (p != null) {
                PoseNetworking.broadcastAnimState(p, 0);
                if (poseOf(p.getUuid()) != PoseState.NONE) {
                    PoseNetworking.poseStates.put(p.getUuid(), PoseState.GRAB_READY);
                    PoseNetworking.broadcastPoseChange(server, p.getUuid(), PoseState.GRAB_READY);
                }
            }
        }
    }

    private static void hardCancel(MinecraftServer server, UUID any) {
        ReadyHugHandler.Session s = sessions.remove(any);
        if (s != null) {
            sessions.remove(s.a);
            sessions.remove(s.b);
            cancelRequested.remove(s.a);
            cancelRequested.remove(s.b);

            for (UUID id : new UUID[]{s.a, s.b}) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                if (p != null) {
                    PoseNetworking.broadcastAnimState(p, 0);
                    PoseNetworking.poseStates.put(id, PoseState.GRAB_READY);
                    PoseNetworking.broadcastPoseChange(server, id, PoseState.GRAB_READY);
                    ServerPlayNetworking.send(p, new ReadyHugHandler.HugSessionPayload(false, id.equals(s.a) ? s.b : s.a));
                }
            }
        }
    }

    private static void pullTogether(ServerPlayerEntity a, ServerPlayerEntity b) {
        double dx = b.getX() - a.getX();
        double dz = b.getZ() - a.getZ();
        double dist = Math.hypot(dx, dz);
        if (!(dist < 1.0E-4)) {
            double over = dist - 0.8;
            if (!(Math.abs(over) < 0.02)) {
                double stepTotal = MathHelper.clamp(over * 0.35, -0.2, 0.2);
                double ux = dx / dist;
                double uz = dz / dist;
                double half = stepTotal / 2.0;
                a.requestTeleport(a.getX() + ux * half, a.getY(), a.getZ() + uz * half);
                b.requestTeleport(b.getX() - ux * half, b.getY(), b.getZ() - uz * half);
            }
        }
    }

    private static void applyEffects(ServerPlayerEntity a, ServerPlayerEntity b) {
        a.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5924, 40, 1, false, false));
        b.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5924, 40, 1, false, false));
        Vec3d pos = a.getPos().add(b.getPos()).multiply(0.5).add(0.0, 1.2, 0.0);
        ServerWorld world = a.getServerWorld();
        world.spawnParticles(ParticleTypes.field_11201, pos.x, pos.y, pos.z, 5, 0.3, 0.3, 0.3, 0.1);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.field_14709, SoundCategory.field_15248, 0.1F, 1.5F);
    }

    private static PoseState poseOf(UUID id) {
        return PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
    }

    public static boolean isHugging(UUID id) {
        ReadyHugHandler.Session s = sessions.get(id);
        return s != null && s.phase != ReadyHugHandler.Phase.ENDING;
    }

    public static boolean isInSession(UUID id) {
        return sessions.containsKey(id);
    }

    public static void cleanup(UUID id) {
        ReadyHugHandler.Session s = sessions.get(id);
        if (s != null) {
            sessions.remove(s.a);
            sessions.remove(s.b);
            cancelRequested.remove(s.a);
            cancelRequested.remove(s.b);
        }

        shiftHeldSince.remove(id);
        shiftLastSeen.remove(id);
        cancelRequested.remove(id);
    }

    public record HugSessionPayload(boolean active, UUID partner) implements CustomPayload {
        public static final Id<ReadyHugHandler.HugSessionPayload> ID = new Id(new Identifier("cooptest", "readyhug_session"));
        public static final PacketCodec<PacketByteBuf, ReadyHugHandler.HugSessionPayload> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeBoolean(p.active);
            buf.writeUuid(p.partner);
        }, buf -> new ReadyHugHandler.HugSessionPayload(buf.readBoolean(), buf.readUuid()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HugShiftPayload(boolean holding, boolean rightClick) implements CustomPayload {
        public static final Id<ReadyHugHandler.HugShiftPayload> ID = new Id(new Identifier("cooptest", "readyhug_shift"));
        public static final PacketCodec<PacketByteBuf, ReadyHugHandler.HugShiftPayload> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeBoolean(p.holding);
            buf.writeBoolean(p.rightClick);
        }, buf -> new ReadyHugHandler.HugShiftPayload(buf.readBoolean(), buf.readBoolean()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static enum Phase {
        STARTING,
        HUGGING,
        ENDING;
    }

    private static final class Session {
        final UUID a;
        final UUID b;
        ReadyHugHandler.Phase phase = ReadyHugHandler.Phase.STARTING;
        long phaseStart = System.currentTimeMillis();
        long lastEffect = 0L;

        Session(UUID a, UUID b) {
            this.a = a;
            this.b = b;
        }
    }
}
