package com.cooptest.highfive;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.cooptest.spin.HandSpinHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.PlayerLookup;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
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

public class ReadyFiveHandler {
    public static final long PAIR_WINDOW_MS = 400L;
    public static final double REACH_FORWARD_OFFSET = 0.7;
    public static final double REACH_RADIUS = 0.9;
    public static final double MAX_REPOSITION = 2.0;
    public static final double MEET_SEPARATION = 1.1;
    public static final double PUSH_BACK = 0.5;
    public static final long SLIDE_START_MS = 170L;
    public static final long IMPACT_MS = 420L;
    public static final long PUSH_BACK_MS = 500L;
    public static final long ANIM_END_MS = 667L;
    public static final int SLIDE_TICKS = 5;
    public static final int PUSH_BACK_TICKS = 4;
    public static final int YAW_LERP_TICKS = 4;
    public static final long COOLDOWN_MS = 300L;
    public static final float SHAKE_GROUND = 0.35F;
    public static final float SHAKE_AIR = 0.55F;
    public static final long SHAKE_MS = 120L;
    public static final int ANIM_JUMP_HIGH = 127;
    private static final int ANIM_NONE = 0;
    private static final Map<UUID, Long> handRaised = new HashMap<>();
    private static final Map<UUID, Long> active = new HashMap<>();
    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final List<ReadyFiveHandler.PendingRepos> pendingRepos = new ArrayList<>();

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(ReadyFiveHandler.ReadyFiveImpactPayload.ID, ReadyFiveHandler.ReadyFiveImpactPayload.CODEC);
    }

    public static boolean onHandRaise(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (active.containsKey(id)) {
            return true;
        } else {
            PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
            if (pose != PoseState.GRAB_READY) {
                return false;
            } else {
                long now = System.currentTimeMillis();
                Long cd = cooldowns.get(id);
                if (cd != null && now < cd) {
                    return true;
                } else if (HandSpinHandler.isInSession(id)) {
                    return true;
                } else {
                    handRaised.put(id, now);
                    return true;
                }
            }
        }
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            drainPendingRepos();
            expireStale();
            tryPair(server);
        });
    }

    private static void expireStale() {
        long now = System.currentTimeMillis();
        handRaised.entrySet().removeIf(e -> now - e.getValue() > 400L);
        cooldowns.entrySet().removeIf(e -> now >= e.getValue());
        active.entrySet().removeIf(e -> now - e.getValue() > 1667L);
    }

    private static void tryPair(MinecraftServer server) {
        if (handRaised.size() >= 2) {
            long now = System.currentTimeMillis();
            List<UUID> ids = new ArrayList<>(handRaised.keySet());

            for (int i = 0; i < ids.size(); i++) {
                for (int j = i + 1; j < ids.size(); j++) {
                    UUID a = ids.get(i);
                    UUID b = ids.get(j);
                    if (!active.containsKey(a) && !active.containsKey(b)) {
                        ServerPlayerEntity p1 = server.getPlayerManager().getPlayer(a);
                        ServerPlayerEntity p2 = server.getPlayerManager().getPlayer(b);
                        if (p1 != null && p2 != null && p1.getWorld() == p2.getWorld()) {
                            Long t1 = handRaised.get(a);
                            Long t2 = handRaised.get(b);
                            if (t1 != null && t2 != null && Math.abs(t1 - t2) <= 400L) {
                                Vec3d r1 = reachPoint(p1);
                                Vec3d r2 = reachPoint(p2);
                                double reachDist = Math.hypot(r1.x - r2.x, r1.z - r2.z);
                                if (!(reachDist > 0.9)) {
                                    handRaised.remove(a);
                                    handRaised.remove(b);
                                    start(p1, p2, now);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void start(ServerPlayerEntity p1, ServerPlayerEntity p2, long now) {
        if (p1.getUuid().compareTo(p2.getUuid()) > 0) {
            ServerPlayerEntity tmp = p1;
            p1 = p2;
            p2 = tmp;
        }

        ServerPlayerEntity a = p1;
        ServerPlayerEntity b = p2;
        UUID ida = a.getUuid();
        UUID idb = b.getUuid();
        MinecraftServer server = a.getServer();
        if (server != null) {
            boolean air = !a.isOnGround() && !b.isOnGround();
            active.put(ida, now);
            active.put(idb, now);
            PoseNetworking.broadcastAnimState(a, 127);
            PoseNetworking.broadcastAnimState(b, 127);
            scheduleYawLerp(a, b, 4);
            scheduleYawLerp(b, a, 4);
            if (air) {
                equalizeArcs(a, b);
            }

            schedule(server, 170L, () -> {
                Vec3d[] deltas = computeMeetDeltas(a, b, air);
                if (deltas != null) {
                    pendingRepos.add(new ReadyFiveHandler.PendingRepos(a, deltas[0], 5));
                    pendingRepos.add(new ReadyFiveHandler.PendingRepos(b, deltas[1], 5));
                }
            });
            schedule(server, 420L, () -> impact(a, b, air));
            schedule(server, 500L, () -> {
                Vec3d dir = horizontalBetween(a, b);
                if (dir != null) {
                    pendingRepos.add(new ReadyFiveHandler.PendingRepos(a, dir.multiply(-0.5), 4));
                    pendingRepos.add(new ReadyFiveHandler.PendingRepos(b, dir.multiply(0.5), 4));
                }
            });
            schedule(server, 667L, () -> {
                restore(a);
                restore(b);
                long end = System.currentTimeMillis();
                active.remove(ida);
                active.remove(idb);
                cooldowns.put(ida, end + 300L);
                cooldowns.put(idb, end + 300L);
            });
        }
    }

    private static void equalizeArcs(ServerPlayerEntity a, ServerPlayerEntity b) {
        double avgY = (a.getY() + b.getY()) / 2.0;
        double avgVY = (a.getVelocity().y + b.getVelocity().y) / 2.0;
        a.requestTeleport(a.getX(), avgY, a.getZ());
        b.requestTeleport(b.getX(), avgY, b.getZ());
        a.setVelocity(a.getVelocity().x, avgVY, a.getVelocity().z);
        b.setVelocity(b.getVelocity().x, avgVY, b.getVelocity().z);
        a.velocityDirty = true;
        b.velocityDirty = true;
    }

    private static Vec3d[] computeMeetDeltas(ServerPlayerEntity a, ServerPlayerEntity b, boolean air) {
        Vec3d dir = horizontalBetween(a, b);
        if (dir == null) {
            return null;
        } else {
            double dist = Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ());
            double step = MathHelper.clamp((dist - 1.1) / 2.0, 0.0, 0.55);
            Vec3d d1 = dir.multiply(step);
            Vec3d d2 = dir.multiply(-step);
            return Math.hypot(d1.x, d1.z) > 2.0 ? null : new Vec3d[]{d1, d2};
        }
    }

    private static void impact(ServerPlayerEntity a, ServerPlayerEntity b, boolean air) {
        ServerWorld world = a.getServerWorld();
        double x = (a.getX() + b.getX()) / 2.0;
        double y = (a.getY() + b.getY()) / 2.0 + 1.5;
        double z = (a.getZ() + b.getZ()) / 2.0;
        if (air) {
            world.playSound(null, x, y, z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.3F, 1.2F);
            world.playSound(null, x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 0.7F, 1.6F);
            world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 24, 0.35, 0.35, 0.35, 0.12);
            world.spawnParticles(ParticleTypes.FIREWORK, x, y, z, 16, 0.25, 0.25, 0.25, 0.08);
            world.spawnParticles(ParticleTypes.CLOUD, x, y, z, 12, 0.3, 0.2, 0.3, 0.04);
        } else {
            world.playSound(null, x, y, z, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.2F, 1.0F);
            world.spawnParticles(ParticleTypes.CRIT, x, y, z, 14, 0.25, 0.25, 0.25, 0.1);
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 10, 0.2, 0.2, 0.2, 0.06);
        }

        ReadyFiveHandler.ReadyFiveImpactPayload payload = new ReadyFiveHandler.ReadyFiveImpactPayload(air ? 0.55F : 0.35F, 120, air);
        ServerPlayNetworking.send(a, payload);
        ServerPlayNetworking.send(b, payload);

        for (ServerPlayerEntity near : PlayerLookup.tracking(a)) {
            if (near != a && near != b) {
                ServerPlayNetworking.send(near, payload);
            }
        }
    }

    private static void restore(ServerPlayerEntity p) {
        if (p != null) {
            MinecraftServer server = p.getServer();
            if (server != null) {
                PoseNetworking.broadcastAnimState(p, 0);
                PoseNetworking.poseStates.put(p.getUuid(), PoseState.GRAB_READY);
                PoseNetworking.broadcastPoseChange(server, p.getUuid(), PoseState.GRAB_READY);
            }
        }
    }

    public static Vec3d horizontalForward(ServerPlayerEntity p) {
        double yaw = Math.toRadians(p.getYaw());
        return new Vec3d(-Math.sin(yaw), 0.0, Math.cos(yaw)).normalize();
    }

    public static Vec3d reachPoint(ServerPlayerEntity p) {
        Vec3d fwd = horizontalForward(p);
        Vec3d pos = p.getPos();
        return new Vec3d(pos.x + fwd.x * 0.7, pos.y + 1.3, pos.z + fwd.z * 0.7);
    }

    private static Vec3d horizontalBetween(ServerPlayerEntity a, ServerPlayerEntity b) {
        double dx = b.getX() - a.getX();
        double dz = b.getZ() - a.getZ();
        double len = Math.hypot(dx, dz);
        return len < 1.0E-4 ? null : new Vec3d(dx / len, 0.0, dz / len);
    }

    private static void scheduleYawLerp(ServerPlayerEntity player, ServerPlayerEntity target, int ticks) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            Vec3d toTarget = target.getPos().subtract(player.getPos());
            float targetYaw = (float)Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
            float startYaw = player.getYaw();
            new Thread(() -> {
                for (int i = 1; i <= ticks; i++) {
                    float t = (float)i / ticks;
                    float newYaw = startYaw + MathHelper.wrapDegrees(targetYaw - startYaw) * t;

                    try {
                        Thread.sleep(50L);
                    } catch (InterruptedException var9) {
                        return;
                    }

                    server.execute(() -> {
                        player.setYaw(newYaw);
                        player.setHeadYaw(newYaw);
                        player.setBodyYaw(newYaw);
                    });
                }
            }).start();
        }
    }

    private static void schedule(MinecraftServer server, long delayMs, Runnable work) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException var5) {
                return;
            }

            server.execute(work);
        }).start();
    }

    private static void drainPendingRepos() {
        Iterator<ReadyFiveHandler.PendingRepos> it = pendingRepos.iterator();

        while (it.hasNext()) {
            ReadyFiveHandler.PendingRepos r = it.next();
            if (r.player != null && !r.player.isRemoved() && r.remainingTicks > 0) {
                Vec3d step = r.remainingDelta.multiply(1.0 / r.remainingTicks);
                Vec3d pos = r.player.getPos();
                r.player.requestTeleport(pos.x + step.x, pos.y, pos.z + step.z);
                r.remainingDelta = r.remainingDelta.subtract(step);
                r.remainingTicks--;
                if (r.remainingTicks <= 0) {
                    it.remove();
                }
            } else {
                it.remove();
            }
        }
    }

    public static boolean isActive(UUID id) {
        return active.containsKey(id);
    }

    public static void cleanup(UUID id) {
        handRaised.remove(id);
        active.remove(id);
        cooldowns.remove(id);
        pendingRepos.removeIf(r -> r.player != null && r.player.getUuid().equals(id));
    }

    private static final class PendingRepos {
        final ServerPlayerEntity player;
        Vec3d remainingDelta;
        int remainingTicks;

        PendingRepos(ServerPlayerEntity player, Vec3d delta, int ticks) {
            this.player = player;
            this.remainingDelta = delta;
            this.remainingTicks = Math.max(1, ticks);
        }
    }

    public record ReadyFiveImpactPayload(float amount, int durationMs, boolean air) implements CustomPayload {
        public static final Id<ReadyFiveHandler.ReadyFiveImpactPayload> ID = new Id(new Identifier("cooptest", "readyfive_impact"));
        public static final PacketCodec<PacketByteBuf, ReadyFiveHandler.ReadyFiveImpactPayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeFloat(payload.amount);
            buf.writeInt(payload.durationMs);
            buf.writeBoolean(payload.air);
        }, buf -> new ReadyFiveHandler.ReadyFiveImpactPayload(buf.readFloat(), buf.readInt(), buf.readBoolean()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
