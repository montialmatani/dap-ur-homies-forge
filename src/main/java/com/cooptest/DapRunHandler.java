package com.cooptest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.entity.attribute.EntityAttributes;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class DapRunHandler {
    public static final double DETECT_RANGE = 4.0;
    public static final double SLOT_FORWARD = 0.4;
    public static final double SLOT_HEIGHT = 1.0;
    public static final double SIDE_SIGN = -1.0;
    public static final double SLOT_LEFT = 0.85;
    public static final double SLOT_RADIUS = 1.4;
    public static final long IMPACT_MS = 210L;
    public static final long ANIM_MS = 833L;
    public static final double CATCHUP_MAX = 0.3;
    public static final long CATCHUP_MS = 200L;
    public static final double CATCHUP_SIDE_MIX = 0.55;
    public static final double CATCHUP_SIDE_EXTRA = 0.55;
    public static final double SLOW_MULT_BASE = -0.22;
    public static final double SLOW_MULT_PER_TIER = -0.09;
    public static final long SLOW_DURATION_MS = 260L;
    public static final long COOLDOWN_MS = 600L;
    public static final long RELEASE_SYNC_MS = 400L;
    public static final int ANIM_DAP_RUN = 126;
    private static final Map<UUID, Long> cooldown = new HashMap<>();
    private static final Map<UUID, Long> legBoost = new HashMap<>();
    private static final Map<UUID, Long> justFired = new HashMap<>();
    private static final Map<UUID, Long> armed = new HashMap<>();
    private static final long JUST_FIRED_MS = 300L;
    private static final List<DapRunHandler.Pending> pendingImpact = new ArrayList<>();
    private static final Identifier SLOW_ID = new Identifier("testcoop", "dap_run_slow");
    private static final List<DapRunHandler.PendingSlow> pendingSlow = new ArrayList<>();

    public static boolean justFired(UUID id) {
        Long t = justFired.get(id);
        return t != null && System.currentTimeMillis() - t < 300L;
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(DapRunHandler.DapRunStartPayload.ID, DapRunHandler.DapRunStartPayload.CODEC);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            long now = System.currentTimeMillis();
            cooldown.entrySet().removeIf(e -> now > e.getValue());
            justFired.entrySet().removeIf(e -> now - e.getValue() > 300L);
            armed.entrySet().removeIf(e -> now - e.getValue() > 400L);
            tickLegBoost(server, now);
            Iterator<DapRunHandler.Pending> it = pendingImpact.iterator();

            while (it.hasNext()) {
                DapRunHandler.Pending p = it.next();
                if (now >= p.fireAt()) {
                    doImpact(p.p1(), p.p2(), p.tier());
                    it.remove();
                }
            }

            Iterator<DapRunHandler.PendingSlow> sit = pendingSlow.iterator();

            while (sit.hasNext()) {
                DapRunHandler.PendingSlow ps = sit.next();
                if (now >= ps.removeAt()) {
                    removeImpactSlow(ps.player());
                    sit.remove();
                }
            }
        });
    }

    public static boolean tryDapRun(ServerPlayerEntity a, ServerPlayerEntity b) {
        long now = System.currentTimeMillis();
        if (!isOnCooldown(a) && !isOnCooldown(b)) {
            if (a.getWorld() != b.getWorld()) {
                return false;
            } else {
                Vec3d pa = a.getPos();
                Vec3d pb = b.getPos();
                if (Math.hypot(pb.x - pa.x, pb.z - pa.z) > 4.0) {
                    return false;
                } else {
                    Vec3d sa = leftSlot(a);
                    Vec3d sb = leftSlot(b);
                    if (Math.hypot(sa.x - sb.x, sa.z - sb.z) > 1.4) {
                        return false;
                    } else {
                        Long partnerArmed = armed.get(b.getUuid());
                        if (partnerArmed != null && now - partnerArmed <= 400L) {
                            armed.remove(a.getUuid());
                            armed.remove(b.getUuid());
                            boolean aFast = a.isSprinting();
                            boolean bFast = b.isSprinting();
                            int tier = aFast && bFast ? 3 : (!aFast && !bFast ? 1 : 2);
                            cooldown.put(a.getUuid(), now + 600L);
                            cooldown.put(b.getUuid(), now + 600L);
                            if (tier >= 2) {
                                DapFlair.openDapRun(
                                    (ServerWorld)a.getWorld(), a.getPos().add(b.getPos()).multiply(0.5), a, b, 210L, tier
                                );
                            }

                            justFired.put(a.getUuid(), now);
                            justFired.put(b.getUuid(), now);
                            PoseNetworking.broadcastAnimState(a, 126);
                            PoseNetworking.broadcastAnimState(b, 126);
                            if (aFast && !bFast) {
                                catchUp(b, a);
                            } else if (bFast && !aFast) {
                                catchUp(a, b);
                            } else if (!aFast && !bFast) {
                                catchUp(a, b);
                                catchUp(b, a);
                            }

                            DapRunHandler.DapRunStartPayload payload = new DapRunHandler.DapRunStartPayload(tier);
                            ServerPlayNetworking.send(a, payload);
                            ServerPlayNetworking.send(b, payload);
                            ChargedDapHandler.applyDapCooldown(a);
                            ChargedDapHandler.applyDapCooldown(b);
                            pendingImpact.add(new DapRunHandler.Pending(a, b, tier, now + 210L));
                            return true;
                        } else {
                            armed.put(a.getUuid(), now);
                            justFired.put(a.getUuid(), now);
                            return true;
                        }
                    }
                }
            }
        } else {
            return false;
        }
    }

    public static Vec3d slotOf(Vec3d pos, float yaw) {
        double rad = Math.toRadians(yaw);
        Vec3d fwd = new Vec3d(-Math.sin(rad), 0.0, Math.cos(rad));
        Vec3d left = new Vec3d(Math.cos(rad), 0.0, Math.sin(rad));
        double side = -0.85;
        return new Vec3d(
            pos.x + fwd.x * 0.4 + left.x * side,
            pos.y + 1.0,
            pos.z + fwd.z * 0.4 + left.z * side
        );
    }

    private static Vec3d leftSlot(ServerPlayerEntity p) {
        return slotOf(p.getPos(), p.getYaw());
    }

    private static void catchUp(ServerPlayerEntity mover, ServerPlayerEntity target) {
        double rad = Math.toRadians(target.getYaw());
        Vec3d tLeft = new Vec3d(Math.cos(rad), 0.0, Math.sin(rad));
        Vec3d to = leftSlot(target).add(tLeft.multiply(-0.55));
        Vec3d from = mover.getPos();
        Vec3d delta = new Vec3d(to.x - from.x, 0.0, to.z - from.z);
        double len = Math.hypot(delta.x, delta.z);
        if (!(len < 0.05)) {
            Vec3d toward = new Vec3d(target.getPos().x - from.x, 0.0, target.getPos().z - from.z);
            double tl = Math.hypot(toward.x, toward.z);
            if (tl > 0.001) {
                toward = toward.multiply(1.0 / tl);
                double along = delta.x * toward.x + delta.z * toward.z;
                Vec3d alongVec = toward.multiply(along);
                Vec3d sideVec = delta.subtract(alongVec).multiply(0.55);
                delta = alongVec.add(sideVec);
                len = Math.hypot(delta.x, delta.z);
                if (len < 0.05) {
                    return;
                }
            }

            if (len > 0.3) {
                delta = delta.multiply(0.3 / len);
            }

            DapPositioning.slideBy(mover, delta, (int)Math.max(1L, 4L));
        }
    }

    public static void onRunFlair(ServerPlayerEntity p, boolean perfectRun) {
        if (p != null && p.isAlive()) {
            CoopMovesConfig c = CoopMovesConfig.get();
            int secs = Math.max(1, c.dapRunFlairBurstSec);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5904, secs * 20, Math.max(0, c.dapRunFlairBurstAmp), false, true));
            legBoost.put(p.getUuid(), System.currentTimeMillis() + secs * 1000L);
            if (p.getWorld() instanceof ServerWorld w) {
                double var15 = p.getX();
                double y = p.getY();
                double z = p.getZ();
                Vec3d look = p.getRotationVector();
                w.spawnParticles(
                    ParticleTypes.field_38908, var15 + look.x * 1.5, y + 1.0 + look.y * 0.5, z + look.z * 1.5, 1, 0.0, 0.0, 0.0, 0.0
                );
                w.playSound(null, var15, y, z, SoundEvents.field_38830, SoundCategory.field_15248, perfectRun ? 1.2F : 0.8F, perfectRun ? 1.4F : 1.7F);
                w.playSound(null, var15, y, z, SoundEvents.field_14706, SoundCategory.field_15248, 1.0F, 0.5F);

                for (int i = 0; i < 16; i++) {
                    double a = (Math.PI * 2) * i / 16.0;
                    w.spawnParticles(ParticleTypes.field_11204, var15 + Math.cos(a) * 0.6, y + 0.05, z + Math.sin(a) * 0.6, 1, 0.0, 0.02, 0.0, 0.02);
                }
            }
        }
    }

    private static void tickLegBoost(MinecraftServer server, long now) {
        if (!legBoost.isEmpty()) {
            Iterator<Entry<UUID, Long>> lit = legBoost.entrySet().iterator();

            while (lit.hasNext()) {
                Entry<UUID, Long> e = lit.next();
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p != null && p.isAlive() && now <= e.getValue()) {
                    if (p.getWorld() instanceof ServerWorld w) {
                        double yaw = Math.toRadians(p.getYaw());
                        double rx = -Math.cos(yaw);
                        double rz = -Math.sin(yaw);

                        for (byte side = -1; side <= 1; side += 2) {
                            double fx = p.getX() + rx * 0.18 * side;
                            double fz = p.getZ() + rz * 0.18 * side;
                            w.spawnParticles(ParticleTypes.field_29644, fx, p.getY() + 0.12, fz, 1, 0.03, 0.02, 0.03, 0.01);
                            w.spawnParticles(ParticleTypes.field_11204, fx, p.getY() + 0.05, fz, 1, 0.05, 0.0, 0.05, 0.005);
                        }
                    } else {
                        lit.remove();
                    }
                } else {
                    lit.remove();
                }
            }
        }
    }

    public static void clearLegBoost(UUID id) {
        legBoost.remove(id);
    }

    private static void doImpact(ServerPlayerEntity a, ServerPlayerEntity b, int tier) {
        if (a.isAlive() && b.isAlive()) {
            ServerWorld world = (ServerWorld)a.getWorld();
            Vec3d mid = a.getPos().add(b.getPos()).multiply(0.5).add(0.0, 1.4, 0.0);
            float pitch = 1.0F + tier * 0.12F;
            world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.field_15016, SoundCategory.field_15248, 0.7F + tier * 0.2F, pitch);
            if (tier >= 2) {
                world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.field_14627, SoundCategory.field_15248, 0.6F, 1.8F);
            }

            applyImpactSlow(a, tier);
            applyImpactSlow(b, tier);
            CoopMovesConfig cfg = CoopMovesConfig.get();
            DapFlair.FlairShakePayload shake = new DapFlair.FlairShakePayload(cfg.dapRunShake * tier / 2.0F, cfg.dapRunShakeMs);
            ServerPlayNetworking.send(a, shake);
            ServerPlayNetworking.send(b, shake);
            if (tier >= 2) {
                DapHearts.add(a, cfg.dapRunHearts);
                DapHearts.add(b, cfg.dapRunHearts);
            }

            int count = 4 + tier * 4;
            world.spawnParticles(ParticleTypes.field_11205, mid.x, mid.y, mid.z, count, 0.15, 0.15, 0.15, 0.12);
            if (tier >= 2) {
                world.spawnParticles(ParticleTypes.field_11248, mid.x, mid.y, mid.z, tier * 3, 0.1, 0.1, 0.1, 0.06);
            }
        }
    }

    private static void applyImpactSlow(ServerPlayerEntity p, int tier) {
        EntityAttributeInstance attr = p.getAttributeInstance(EntityAttributes.field_23719);
        if (attr != null) {
            double mult = -0.22 + -0.09 * (tier - 1);
            attr.removeModifier(SLOW_ID);
            attr.addTemporaryModifier(new EntityAttributeModifier(SLOW_ID, mult, Operation.MULTIPLY_TOTAL));
            pendingSlow.add(new DapRunHandler.PendingSlow(p, System.currentTimeMillis() + 260L));
        }
    }

    private static void removeImpactSlow(ServerPlayerEntity p) {
        EntityAttributeInstance attr = p.getAttributeInstance(EntityAttributes.field_23719);
        if (attr != null) {
            attr.removeModifier(SLOW_ID);
        }
    }

    private static boolean isOnCooldown(ServerPlayerEntity p) {
        Long until = cooldown.get(p.getUuid());
        return until != null && System.currentTimeMillis() < until;
    }

    public static void cleanup(UUID playerId) {
        cooldown.remove(playerId);
        justFired.remove(playerId);
        armed.remove(playerId);
        pendingImpact.removeIf(p -> p.p1().getUuid().equals(playerId) || p.p2().getUuid().equals(playerId));
        pendingSlow.removeIf(p -> {
            if (!p.player().getUuid().equals(playerId)) {
                return false;
            } else {
                removeImpactSlow(p.player());
                return true;
            }
        });
    }

    public record DapRunStartPayload(int tier) implements CustomPayload {
        public static final Id<DapRunHandler.DapRunStartPayload> ID = new Id(new Identifier("cooptest", "dap_run_start"));
        public static final PacketCodec<PacketByteBuf, DapRunHandler.DapRunStartPayload> CODEC = PacketCodec.of(
            (v, buf) -> buf.writeVarInt(v.tier()), buf -> new DapRunHandler.DapRunStartPayload(buf.readVarInt())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private record Pending(ServerPlayerEntity p1, ServerPlayerEntity p2, int tier, long fireAt) {
    }

    private record PendingSlow(ServerPlayerEntity player, long removeAt) {
    }
}
