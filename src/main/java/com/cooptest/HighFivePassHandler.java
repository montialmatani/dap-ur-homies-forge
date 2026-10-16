package com.cooptest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
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

public class HighFivePassHandler {
    public static final long IMPACT_DELAY_MS = 210L;
    public static final long SLOW_START_MS = 290L;
    public static final long ANIM_END_MS = 1958L;
    public static final float FLINCH_STRENGTH = 0.06F;
    public static final boolean ENABLE_FLINCH = false;
    public static final double SLOW_MULTIPLIER = -0.25;
    public static final double MAX_REPOSITION = 1.5;
    public static final long REPOSITION_DURATION_MS = 250L;
    public static final boolean PASS_TP_DEBUG = false;
    private static final Identifier PASS_SLOW_ID = new Identifier("testcoop", "highfive_pass_slow");
    private static final List<HighFivePassHandler.PendingPass> pendingFlinch = new ArrayList<>();
    private static final List<HighFivePassHandler.PendingPass> pendingSlow = new ArrayList<>();
    private static final List<HighFivePassHandler.PendingPass> pendingCleanup = new ArrayList<>();
    private static final List<HighFivePassHandler.PendingReposition> pendingReposition = new ArrayList<>();
    public static final Identifier PASS_START_ID = new Identifier("testcoop", "highfive_pass_start");

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(HighFivePassHandler.HighFivePassStartPayload.ID, HighFivePassHandler.HighFivePassStartPayload.CODEC);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            long now = System.currentTimeMillis();
            Iterator<HighFivePassHandler.PendingReposition> reposIt = pendingReposition.iterator();

            while (reposIt.hasNext()) {
                HighFivePassHandler.PendingReposition r = reposIt.next();
                if (r.remainingTicks <= 0) {
                    reposIt.remove();
                } else {
                    Vec3d step = r.remainingDelta.multiply(1.0 / r.remainingTicks);
                    Vec3d newPos = r.player.getPos().add(step);
                    r.player.requestTeleport(newPos.x, newPos.y, newPos.z);
                    r.remainingDelta = r.remainingDelta.subtract(step);
                    r.remainingTicks--;
                    if (r.remainingTicks <= 0) {
                        reposIt.remove();
                    }
                }
            }

            Iterator<HighFivePassHandler.PendingPass> flinchIt = pendingFlinch.iterator();

            while (flinchIt.hasNext()) {
                HighFivePassHandler.PendingPass pass = flinchIt.next();
                if (now >= pass.fireTime()) {
                    spawnImpactEffects(pass.p1(), pass.p2());
                    flinchIt.remove();
                }
            }

            Iterator<HighFivePassHandler.PendingPass> slowIt = pendingSlow.iterator();

            while (slowIt.hasNext()) {
                HighFivePassHandler.PendingPass pass = slowIt.next();
                if (now >= pass.fireTime()) {
                    applySlow(pass.p1());
                    applySlow(pass.p2());
                    slowIt.remove();
                }
            }

            Iterator<HighFivePassHandler.PendingPass> cleanupIt = pendingCleanup.iterator();

            while (cleanupIt.hasNext()) {
                HighFivePassHandler.PendingPass pass = cleanupIt.next();
                if (now >= pass.fireTime()) {
                    removeSlow(pass.p1());
                    removeSlow(pass.p2());
                    cleanupIt.remove();
                }
            }
        });
    }

    public static void executePassHighFive(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d targetP1, Vec3d targetP2) {
        long now = System.currentTimeMillis();
        repositionForTouch(p1, p2, targetP1, targetP2);
        PoseNetworking.broadcastAnimState(p1, 105);
        PoseNetworking.broadcastAnimState(p2, 105);
        HighFivePassHandler.HighFivePassStartPayload startPayload = new HighFivePassHandler.HighFivePassStartPayload();
        ServerPlayNetworking.send(p1, startPayload);
        ServerPlayNetworking.send(p2, startPayload);
        pendingFlinch.add(new HighFivePassHandler.PendingPass(p1, p2, now + 210L));
        pendingSlow.add(new HighFivePassHandler.PendingPass(p1, p2, now + 290L));
        pendingCleanup.add(new HighFivePassHandler.PendingPass(p1, p2, now + 1958L));
    }

    private static void repositionForTouch(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d targetP1, Vec3d targetP2) {
        Vec3d pos1 = p1.getPos();
        Vec3d pos2 = p2.getPos();
        double dist1 = Math.hypot(targetP1.x - pos1.x, targetP1.z - pos1.z);
        double dist2 = Math.hypot(targetP2.x - pos2.x, targetP2.z - pos2.z);
        boolean skipped = dist1 > 1.5 || dist2 > 1.5;
        if (!skipped) {
            int totalTicks = (int)Math.max(1L, 5L);
            Vec3d delta1 = new Vec3d(targetP1.x - pos1.x, 0.0, targetP1.z - pos1.z);
            Vec3d delta2 = new Vec3d(targetP2.x - pos2.x, 0.0, targetP2.z - pos2.z);
            pendingReposition.add(new HighFivePassHandler.PendingReposition(p1, delta1, totalTicks));
            pendingReposition.add(new HighFivePassHandler.PendingReposition(p2, delta2, totalTicks));
        }
    }

    private static String fmt(Vec3d v) {
        return "(" + String.format("%.2f", v.x) + "," + String.format("%.2f", v.z) + ")";
    }

    private static void spawnImpactEffects(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        ServerWorld world = p1.getServerWorld();
        Vec3d pos = p1.getPos().add(p2.getPos()).multiply(0.5).add(0.0, 1.4, 0.0);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.2F, 1.1F);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8F, 1.8F);
        spawnStarBurst(world, pos, 10, 0.3);
        world.spawnParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 8, 0.1, 0.1, 0.1, 0.08);
        world.spawnParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 6, 0.2, 0.2, 0.2, 0.02);
    }

    private static void spawnStarBurst(ServerWorld world, Vec3d pos, int rays, double spread) {
        for (int i = 0; i < rays; i++) {
            double angle = (Math.PI * 2) * i / rays;
            double dx = Math.cos(angle) * spread;
            double dz = Math.sin(angle) * spread;
            world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 2, dx, 0.2, dz, 0.15);
        }
    }

    private static void applyFlinch(ServerPlayerEntity player) {
        double yawRad = Math.toRadians(player.getYaw());
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        Vec3d current = player.getVelocity();
        player.setVelocity(current.x - forward.x * 0.06F, current.y, current.z - forward.z * 0.06F);
        player.velocityModified = true;
    }

    private static void applySlow(ServerPlayerEntity player) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(PASS_SLOW_ID);
            attr.addTemporaryModifier(new EntityAttributeModifier(PASS_SLOW_ID, -0.25, Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeSlow(ServerPlayerEntity player) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(PASS_SLOW_ID);
        }
    }

    public static void cleanup(UUID playerId) {
    }

    public record HighFivePassStartPayload() implements CustomPayload {
        public static final Id<HighFivePassHandler.HighFivePassStartPayload> ID = new Id(HighFivePassHandler.PASS_START_ID);
        public static final PacketCodec<PacketByteBuf, HighFivePassHandler.HighFivePassStartPayload> CODEC = PacketCodec.unit(
            new HighFivePassHandler.HighFivePassStartPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private record PendingPass(ServerPlayerEntity p1, ServerPlayerEntity p2, long fireTime) {
    }

    private static final class PendingReposition {
        final ServerPlayerEntity player;
        Vec3d remainingDelta;
        int remainingTicks;

        PendingReposition(ServerPlayerEntity player, Vec3d remainingDelta, int remainingTicks) {
            this.player = player;
            this.remainingDelta = remainingDelta;
            this.remainingTicks = remainingTicks;
        }
    }
}
