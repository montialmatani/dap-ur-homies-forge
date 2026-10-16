package com.cooptest;

import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Hand;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CodEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
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

public class SlapHandler {
    private static final double SLAP_RANGE = 1.2;
    private static final double SNAP_DISTANCE = 0.9;
    private static final double BACK_THRESHOLD = 0.7;
    private static final double FRONT_THRESHOLD = -0.5;
    private static final double AIM_THRESHOLD = 0.8;
    private static final long IMPACT_DELAY_MS = 130L;
    private static final long FRONT_IMPACT_MS = 250L;
    private static final int ANIM_SLAP = 67;
    private static final int ANIM_SLAP_FRONT = 82;

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(SlapHandler.CameraFlickPayload.ID, SlapHandler.CameraFlickPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SlapHandler.CameraYawFlickPayload.ID, SlapHandler.CameraYawFlickPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SlapHandler.ScreenClosePayload.ID, SlapHandler.ScreenClosePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SlapHandler.FishSlapPayload.ID, SlapHandler.FishSlapPayload.CODEC);
    }

    public static void register() {
    }

    public static boolean checkSlapOnRelease(ServerPlayerEntity attacker, long chargeDurationMs) {
        if (StrongSlapCommand.isArmed(attacker.getUuid()) && StrongSlapHandler.tryStrongSlap(attacker, chargeDurationMs)) {
            return true;
        } else {
            ItemStack held = attacker.getMainHandStack();
            boolean holdingFish = held.isOf(Items.field_8429)
                || held.isOf(Items.field_8209)
                || held.isOf(Items.field_8323)
                || held.isOf(Items.field_8846);
            Vec3d aEye = attacker.getPos().add(0.0, attacker.getEyeHeight(attacker.getPose()), 0.0);
            Vec3d aLook = attacker.getRotationVec(1.0F);
            ServerPlayerEntity victim = null;
            double closest = 1.2009999999999998;

            for (ServerPlayerEntity candidate : attacker.getServerWorld().getPlayers()) {
                if (candidate != attacker) {
                    double dist = attacker.getPos().distanceTo(candidate.getPos());
                    if (!(dist >= closest)) {
                        Vec3d victimLook = candidate.getRotationVec(1.0F);
                        double lookDot = aLook.dotProduct(victimLook);
                        boolean isBack = lookDot >= 0.7;
                        boolean isFront = lookDot <= -0.5;
                        if (isBack || isFront) {
                            Vec3d victimHead = candidate.getPos().add(0.0, 1.6, 0.0);
                            Vec3d toHead = victimHead.subtract(aEye);
                            double distToHead = toHead.length();
                            if (!(distToHead < 0.01)) {
                                double aimDot = toHead.normalize().dotProduct(aLook);
                                if (!(aimDot < 0.8)) {
                                    victim = candidate;
                                    closest = dist;
                                }
                            }
                        }
                    }
                }
            }

            if (victim == null) {
                return false;
            } else if (holdingFish) {
                executeFishSlap(attacker, victim);
                return true;
            } else {
                boolean isJumpSlap = !attacker.isOnGround();
                Vec3d victimLookFinal = victim.getRotationVec(1.0F);
                if (aLook.dotProduct(victimLookFinal) <= -0.5) {
                    executeFrontSlap(attacker, victim);
                } else {
                    executeSlap(attacker, victim, isJumpSlap);
                }

                return true;
            }
        }
    }

    private static void executeSlap(ServerPlayerEntity attacker, ServerPlayerEntity victim, boolean isJumpSlap) {
        ServerWorld world = attacker.getServerWorld();
        Vec3d victimPos = victim.getPos();
        Vec3d victimFwd = victim.getRotationVec(1.0F);
        Vec3d victimFwdH = new Vec3d(victimFwd.x, 0.0, victimFwd.z).normalize();
        if (victimFwdH.lengthSquared() < 0.001) {
            victimFwdH = new Vec3d(1.0, 0.0, 0.0);
        }

        if (CoopMovesConfig.get().enableNormalSlapSmoothTp) {
            Vec3d snapPos = victimPos.subtract(victimFwdH.multiply(0.9));
            double safeY = snapPos.y;

            for (int dy = 0; dy <= 2; dy++) {
                BlockPos check = BlockPos.ofFloored(snapPos.x, snapPos.y - dy, snapPos.z);
                if (!world.getBlockState(check).isAir()) {
                    safeY = check.getY() + 1.0;
                    break;
                }
            }

            snapPos = new Vec3d(snapPos.x, safeY, snapPos.z);
            attacker.requestTeleport(snapPos.x, snapPos.y, snapPos.z);
        }

        Vec3d diff = victimPos.subtract(attacker.getPos());
        float yaw = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
        attacker.setYaw(yaw);
        attacker.setBodyYaw(yaw);
        attacker.setHeadYaw(yaw);
        attacker.prevBodyYaw = yaw;
        attacker.swingHand(Hand.field_5808, true);
        attacker.swingHand(Hand.field_5808, true);
        PoseNetworking.broadcastAnimState(attacker, 67);
        if (isJumpSlap) {
            Vec3d impactPos = victimPos.add(0.0, 1.2, 0.0);
            world.spawnParticles(ParticleTypes.field_11221, impactPos.x, impactPos.y, impactPos.z, 2, 0.1, 0.1, 0.1, 0.0);
            world.spawnParticles(ParticleTypes.field_11205, impactPos.x, impactPos.y, impactPos.z, 25, 0.35, 0.35, 0.35, 0.2);
            world.spawnParticles(ParticleTypes.field_11227, impactPos.x, impactPos.y, impactPos.z, 8, 0.2, 0.1, 0.2, 0.05);
            world.spawnParticles(ParticleTypes.field_11208, impactPos.x, impactPos.y, impactPos.z, 12, 0.2, 0.2, 0.2, 0.1);
            world.playSound(
                null, impactPos.x, impactPos.y, impactPos.z, SoundEvents.field_15152, SoundCategory.field_15248, 1.5F, 1.1F
            );
            world.playSound(null, impactPos.x, impactPos.y, impactPos.z, ModSounds.SLAP, SoundCategory.field_15248, 2.2F, 0.8F);
        }

        UUID victimId = victim.getUuid();
        new Thread(
                () -> {
                    try {
                        Thread.sleep(130L);
                    } catch (InterruptedException var5x) {
                    }

                    attacker.getServer()
                        .execute(
                            () -> {
                                ServerPlayerEntity v = attacker.getServer().getPlayerManager().getPlayer(victimId);
                                if (v != null) {
                                    Vec3d hitPos = v.getPos().add(0.0, 1.7, 0.0);
                                    if (isJumpSlap) {
                                        v.damage(v.getServerWorld().getDamageSources().playerAttack(attacker), 10.0F);
                                        v.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5916, 200, 1, false, true));
                                        world.playSound(
                                            null,
                                            hitPos.x,
                                            hitPos.y,
                                            hitPos.z,
                                            SoundEvents.field_15016,
                                            SoundCategory.field_15248,
                                            1.5F,
                                            0.9F
                                        );
                                        if (v.hasVehicle()) {
                                            v.stopRiding();
                                            Vec3d crushPos = v.getPos().subtract(0.0, 0.5, 0.0);
                                            v.requestTeleport(crushPos.x, crushPos.y, crushPos.z);
                                        }

                                        StrongSlapHandler.sendLookLockPublic(v, true);
                                        new Thread(() -> {
                                            try {
                                                Thread.sleep(3000L);
                                            } catch (InterruptedException var3x) {
                                            }

                                            attacker.getServer().execute(() -> {
                                                ServerPlayerEntity vv = attacker.getServer().getPlayerManager().getPlayer(victimId);
                                                if (vv != null) {
                                                    StrongSlapHandler.sendLookLockPublic(vv, false);
                                                }
                                            });
                                        }).start();
                                    } else {
                                        v.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5916, 60, 0, false, true));
                                    }

                                    v.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5909, 40, 6, false, true));
                                    float reactYaw = v.getHeadYaw() + 90.0F;
                                    v.setHeadYaw(reactYaw);
                                    SlapHandler.CameraFlickPayload flick = new SlapHandler.CameraFlickPayload(victimId, 70.0F);

                                    for (ServerPlayerEntity p : attacker.getServer().getPlayerManager().getPlayerList()) {
                                        ServerPlayNetworking.send(p, flick);
                                    }

                                    ServerPlayNetworking.send(v, new SlapHandler.ScreenClosePayload(victimId));
                                    v.sendMessage(Text.literal(isJumpSlap ? "§c§l✋ AERIAL SLAP!" : "§c§l✋ I like ya cut G"), true);
                                    attacker.sendMessage(Text.literal(isJumpSlap ? "§6§l✋ JUMP SLAP!" : "§6§l✋ SLAP!"), true);
                                    world.spawnParticles(
                                        ParticleTypes.field_11205, hitPos.x, hitPos.y, hitPos.z, 10, 0.15, 0.1, 0.15, 0.15
                                    );
                                    world.spawnParticles(ParticleTypes.field_11227, hitPos.x, hitPos.y, hitPos.z, 4, 0.1, 0.05, 0.1, 0.05);
                                    world.spawnParticles(ParticleTypes.field_11208, hitPos.x, hitPos.y, hitPos.z, 6, 0.1, 0.1, 0.1, 0.08);
                                    world.playSound(
                                        null, hitPos.x, hitPos.y, hitPos.z, ModSounds.SLAP, SoundCategory.field_15248, 1.4F, 1.0F
                                    );
                                    world.playSound(
                                        null,
                                        hitPos.x,
                                        hitPos.y,
                                        hitPos.z,
                                        SoundEvents.field_14840,
                                        SoundCategory.field_15248,
                                        0.8F,
                                        2.0F
                                    );
                                }
                            }
                        );
                }
            )
            .start();
    }

    private static void executeFishSlap(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        ServerWorld world = attacker.getServerWorld();
        Vec3d diff = victim.getPos().subtract(attacker.getPos());
        float yaw = (float)Math.toDegrees(Math.atan2(-diff.x, diff.z));
        attacker.setYaw(yaw);
        attacker.setBodyYaw(yaw);
        attacker.setHeadYaw(yaw);
        attacker.swingHand(Hand.field_5808, true);
        attacker.swingHand(Hand.field_5808, true);
        PoseNetworking.broadcastAnimState(attacker, 67);
        UUID victimId = victim.getUuid();
        new Thread(
                () -> {
                    try {
                        Thread.sleep(130L);
                    } catch (InterruptedException var4x) {
                    }

                    attacker.getServer()
                        .execute(
                            () -> {
                                ServerPlayerEntity v = attacker.getServer().getPlayerManager().getPlayer(victimId);
                                if (v != null) {
                                    Vec3d hitPos = v.getPos().add(0.0, 1.6, 0.0);
                                    world.spawnParticles(ParticleTypes.field_11202, hitPos.x, hitPos.y, hitPos.z, 60, 0.5, 0.3, 0.5, 0.2);
                                    world.spawnParticles(ParticleTypes.field_11232, hitPos.x, hitPos.y, hitPos.z, 40, 0.4, 0.4, 0.4, 0.06);
                                    world.spawnParticles(ParticleTypes.field_11241, hitPos.x, hitPos.y, hitPos.z, 30, 0.3, 0.2, 0.3, 0.15);
                                    world.spawnParticles(
                                        ParticleTypes.field_11242, hitPos.x, hitPos.y + 1.0, hitPos.z, 50, 0.6, 0.5, 0.6, 0.0
                                    );
                                    world.spawnParticles(ParticleTypes.field_11244, hitPos.x, hitPos.y, hitPos.z, 20, 0.3, 0.1, 0.3, 0.12);
                                    world.playSound(
                                        null,
                                        hitPos.x,
                                        hitPos.y,
                                        hitPos.z,
                                        SoundEvents.field_14591,
                                        SoundCategory.field_15248,
                                        2.5F,
                                        0.7F
                                    );
                                    world.playSound(
                                        null,
                                        hitPos.x,
                                        hitPos.y,
                                        hitPos.z,
                                        SoundEvents.field_14810,
                                        SoundCategory.field_15248,
                                        2.0F,
                                        1.1F
                                    );
                                    world.playSound(
                                        null,
                                        hitPos.x,
                                        hitPos.y,
                                        hitPos.z,
                                        SoundEvents.field_14876,
                                        SoundCategory.field_15248,
                                        1.5F,
                                        0.9F
                                    );
                                    world.playSound(
                                        null, hitPos.x, hitPos.y, hitPos.z, ModSounds.SLAP, SoundCategory.field_15248, 1.4F, 0.6F
                                    );
                                    CodEntity cod = (CodEntity)EntityType.field_6070.create(world);
                                    if (cod != null) {
                                        cod.setPosition(hitPos);
                                        cod.setVelocity((Math.random() - 0.5) * 0.4, 0.4 + Math.random() * 0.3, (Math.random() - 0.5) * 0.4);
                                        world.spawnEntity(cod);
                                    }

                                    SlapHandler.CameraFlickPayload flick = new SlapHandler.CameraFlickPayload(victimId, 70.0F);

                                    for (ServerPlayerEntity p : attacker.getServer().getPlayerManager().getPlayerList()) {
                                        ServerPlayNetworking.send(p, flick);
                                    }

                                    v.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5916, 100, 1, false, true));
                                    v.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5909, 40, 3, false, true));
                                    ServerPlayNetworking.send(v, new SlapHandler.FishSlapPayload(victimId));
                                    ServerPlayNetworking.send(v, new SlapHandler.ScreenClosePayload(victimId));
                                    v.sendMessage(Text.literal("§b§l\ud83d\udc1f YOU JUST GOT FISH SLAPPED"), true);
                                    attacker.sendMessage(Text.literal("§b§l\ud83d\udc1f FISH SLAP!"), true);
                                }
                            }
                        );
                }
            )
            .start();
    }

    private static void executeFrontSlap(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        ServerWorld world = attacker.getServerWorld();
        Vec3d diff = victim.getPos().subtract(attacker.getPos());
        float yaw = (float)Math.toDegrees(Math.atan2(-diff.x, diff.z));
        attacker.setYaw(yaw);
        attacker.setBodyYaw(yaw);
        attacker.setHeadYaw(yaw);
        attacker.swingHand(Hand.field_5808, true);
        attacker.swingHand(Hand.field_5808, true);
        PoseNetworking.broadcastAnimState(attacker, 82);
        UUID victimId = victim.getUuid();
        new Thread(
                () -> {
                    try {
                        Thread.sleep(250L);
                    } catch (InterruptedException var4x) {
                    }

                    attacker.getServer()
                        .execute(
                            () -> {
                                ServerPlayerEntity v = attacker.getServer().getPlayerManager().getPlayer(victimId);
                                if (v != null) {
                                    Vec3d hitPos = v.getPos().add(0.0, 1.7, 0.0);
                                    v.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5909, 40, 5, false, true));
                                    SlapHandler.CameraYawFlickPayload yawFlick = new SlapHandler.CameraYawFlickPayload(victimId, 45.0F);

                                    for (ServerPlayerEntity p : attacker.getServer().getPlayerManager().getPlayerList()) {
                                        ServerPlayNetworking.send(p, yawFlick);
                                    }

                                    ServerPlayNetworking.send(v, new SlapHandler.ScreenClosePayload(victimId));
                                    world.spawnParticles(
                                        ParticleTypes.field_11205, hitPos.x, hitPos.y, hitPos.z, 10, 0.15, 0.1, 0.15, 0.15
                                    );
                                    world.spawnParticles(ParticleTypes.field_11227, hitPos.x, hitPos.y, hitPos.z, 4, 0.1, 0.05, 0.1, 0.05);
                                    world.playSound(
                                        null, hitPos.x, hitPos.y, hitPos.z, ModSounds.SLAP, SoundCategory.field_15248, 1.4F, 0.9F
                                    );
                                    world.playSound(
                                        null,
                                        hitPos.x,
                                        hitPos.y,
                                        hitPos.z,
                                        SoundEvents.field_14840,
                                        SoundCategory.field_15248,
                                        0.8F,
                                        1.8F
                                    );
                                }
                            }
                        );
                }
            )
            .start();
    }

    public record CameraFlickPayload(UUID playerId, float pitchDelta) implements CustomPayload {
        public static final Id<SlapHandler.CameraFlickPayload> ID = new Id(new Identifier("testcoop", "camera_flick"));
        public static final PacketCodec<PacketByteBuf, SlapHandler.CameraFlickPayload> CODEC = PacketCodec.of((val, buf) -> {
            buf.writeUuid(val.playerId());
            buf.writeFloat(val.pitchDelta());
        }, buf -> new SlapHandler.CameraFlickPayload(buf.readUuid(), buf.readFloat()));

        public Id<SlapHandler.CameraFlickPayload> getId() {
            return ID;
        }
    }

    public record CameraYawFlickPayload(UUID playerId, float yawDelta) implements CustomPayload {
        public static final Id<SlapHandler.CameraYawFlickPayload> ID = new Id(new Identifier("testcoop", "camera_yaw_flick"));
        public static final PacketCodec<PacketByteBuf, SlapHandler.CameraYawFlickPayload> CODEC = PacketCodec.of((val, buf) -> {
            buf.writeUuid(val.playerId());
            buf.writeFloat(val.yawDelta());
        }, buf -> new SlapHandler.CameraYawFlickPayload(buf.readUuid(), buf.readFloat()));

        public Id<SlapHandler.CameraYawFlickPayload> getId() {
            return ID;
        }
    }

    public record FishSlapPayload(UUID playerId) implements CustomPayload {
        public static final Id<SlapHandler.FishSlapPayload> ID = new Id(new Identifier("testcoop", "fish_slap"));
        public static final PacketCodec<PacketByteBuf, SlapHandler.FishSlapPayload> CODEC = PacketCodec.of(
            (val, buf) -> buf.writeUuid(val.playerId()), buf -> new SlapHandler.FishSlapPayload(buf.readUuid())
        );

        public Id<SlapHandler.FishSlapPayload> getId() {
            return ID;
        }
    }

    public record ScreenClosePayload(UUID playerId) implements CustomPayload {
        public static final Id<SlapHandler.ScreenClosePayload> ID = new Id(new Identifier("testcoop", "screen_close"));
        public static final PacketCodec<PacketByteBuf, SlapHandler.ScreenClosePayload> CODEC = PacketCodec.of(
            (val, buf) -> buf.writeUuid(val.playerId()), buf -> new SlapHandler.ScreenClosePayload(buf.readUuid())
        );

        public Id<SlapHandler.ScreenClosePayload> getId() {
            return ID;
        }
    }
}
