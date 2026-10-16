package com.cooptest;

import com.cooptest.bros.BrosHandler;
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
import com.cooptest.fabricshim.fabric.api.networking.v1.PlayerLookup;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.util.math.Box;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.entity.attribute.EntityAttributes;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public class KickHandler {
    public static final float KICK_RANGE = 2.0F;
    public static final float DROP_KICK_RANGE = 3.0F;
    public static final float KICK_DAMAGE = 2.0F;
    public static final float DROP_KICK_DAMAGE = 10.0F;
    public static final double KICK_KB_STRENGTH = 1.5;
    public static final double DROP_KICK_KB_STRENGTH = 3.5;
    public static final long CHARGE_TIME_MS = 3000L;
    public static final long KICK_COOLDOWN_MS = 2000L;
    private static final double KICK_SLOW_AMOUNT = -0.4;
    private static final double DROP_KICK_SLOW_AMOUNT = -0.9;
    private static final long KICK_ANIM_MS = 1000L;
    private static final long DROP_KICK_ANIM_MS = 1750L;
    private static final long DROP_KICK_SLOW_DELAY = 830L;
    private static final java.util.UUID KICK_SLOW_ID = com.cooptest.fabricshim.mc.ModifierIds.of(new Identifier("testcoop", "kick_slow"));
    private static final int ANIM_KICK = 61;
    private static final int ANIM_DROP_KICK = 62;
    private static final Map<UUID, Long> chargeStart = new HashMap<>();
    private static final Map<UUID, Long> cooldownEnd = new HashMap<>();
    private static final Map<UUID, Integer> lastSyncTick = new HashMap<>();
    private static final Map<UUID, Long> slowApplyAt = new HashMap<>();
    private static final Map<UUID, Long> slowRemoveAt = new HashMap<>();
    private static final Map<UUID, Double> slowAmount = new HashMap<>();
    private static final Map<UUID, Long> kickPushWindowEnd = new HashMap<>();
    private static final Map<UUID, Vec3d> kickPushFwd = new HashMap<>();

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(KickHandler.KickStartPayload.ID, KickHandler.KickStartPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(KickHandler.KickReleasePayload.ID, KickHandler.KickReleasePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(KickHandler.KickChargeSyncPayload.ID, KickHandler.KickChargeSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(KickHandler.KickCooldownPayload.ID, KickHandler.KickCooldownPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(KickHandler.KickResultPayload.ID, KickHandler.KickResultPayload.CODEC);
    }

    public static boolean isBusy(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE) != PoseState.NONE) {
            return true;
        } else if (GrabMechanic.isHolding(player)) {
            return true;
        } else if (FallCatchHandler.isInCatchReadyMode(id)) {
            return true;
        } else {
            return BrosHandler.isEngaged(id) ? true : SpearStrikeHandler.isFlying(player);
        }
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(KickHandler.KickStartPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            UUID id = player.getUuid();
            if (CoopMovesConfig.get().enableKick) {
                if (!isOnCooldown(id)) {
                    if (!isBusy(player)) {
                        if (payload.dropKickMode()) {
                            if (!CoopMovesConfig.get().enableDropKick) {
                                executeKick(player, false);
                                return;
                            }

                            chargeStart.put(id, System.currentTimeMillis());
                            broadcastChargeSync(player, true, 0.0F);
                        } else {
                            executeKick(player, false);
                        }
                    }
                }
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(KickHandler.KickReleasePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            UUID id = player.getUuid();
            Long start = chargeStart.remove(id);
            lastSyncTick.remove(id);
            if (start != null) {
                boolean fullCharge = System.currentTimeMillis() - start >= 3000L;
                broadcastChargeSync(player, false, 0.0F);
                executeKick(player, fullCharge);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            long now = System.currentTimeMillis();
            int tick = server.getTicks();
            Iterator<Entry<UUID, Long>> it = chargeStart.entrySet().iterator();

            while (it.hasNext()) {
                Entry<UUID, Long> entry = it.next();
                UUID id = entry.getKey();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player == null) {
                    it.remove();
                    lastSyncTick.remove(id);
                } else if (!player.isSprinting()) {
                    it.remove();
                    lastSyncTick.remove(id);
                    broadcastChargeSync(player, false, 0.0F);
                    ServerPlayNetworking.send(player, new KickHandler.KickCooldownPayload(0L));
                } else {
                    int lastTick = lastSyncTick.getOrDefault(id, -999);
                    if (tick - lastTick >= 2) {
                        lastSyncTick.put(id, tick);
                        float pct = Math.min(1.0F, (float)(now - entry.getValue()) / 3000.0F);
                        broadcastChargeSync(player, true, pct);
                    }
                }
            }

            Iterator<Entry<UUID, Long>> pushWindowIt = kickPushWindowEnd.entrySet().iterator();

            while (pushWindowIt.hasNext()) {
                Entry<UUID, Long> entry = pushWindowIt.next();
                if (now >= entry.getValue()) {
                    pushWindowIt.remove();
                    kickPushFwd.remove(entry.getKey());
                } else {
                    UUID id = entry.getKey();
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                    Vec3d fwd = kickPushFwd.get(id);
                    if (player != null && fwd != null) {
                        float reach = 2.5F;
                        Box box = player.getBoundingBox().expand(reach + 0.3);

                        for (Entity target : player.getServerWorld().getOtherEntities(player, box, e -> e instanceof LivingEntity && !e.isRemoved())) {
                            double dx = target.getX() - player.getX();
                            double dz = target.getZ() - player.getZ();
                            double dist = Math.sqrt(dx * dx + dz * dz);
                            if (!(dist > reach)) {
                                double dot = dist < 0.01 ? 1.0 : (fwd.x * dx + fwd.z * dz) / dist;
                                if (!(dot < 0.2)) {
                                    Vec3d vel = target.getVelocity();
                                    target.setVelocity(vel.add(fwd.x * 0.12, 0.06, fwd.z * 0.12));
                                    ((LivingEntity)target).velocityModified = true;
                                }
                            }
                        }
                    } else {
                        pushWindowIt.remove();
                    }
                }
            }

            Iterator<Entry<UUID, Long>> applyIt = slowApplyAt.entrySet().iterator();

            while (applyIt.hasNext()) {
                Entry<UUID, Long> entry = applyIt.next();
                if (now >= entry.getValue()) {
                    applyIt.remove();
                    UUID id = entry.getKey();
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                    if (player != null) {
                        applySlowdown(player, slowAmount.getOrDefault(id, -0.9));
                    }
                }
            }

            Iterator<Entry<UUID, Long>> removeIt = slowRemoveAt.entrySet().iterator();

            while (removeIt.hasNext()) {
                Entry<UUID, Long> entry = removeIt.next();
                if (now >= entry.getValue()) {
                    removeIt.remove();
                    slowAmount.remove(entry.getKey());
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                    if (player != null) {
                        removeSlowdown(player);
                    }
                }
            }
        });
    }

    private static void executeKick(ServerPlayerEntity player, boolean isDropKick) {
        UUID id = player.getUuid();
        ServerWorld world = player.getServerWorld();
        float reach = isDropKick ? 3.0F : 2.0F;
        float damage = isDropKick ? 10.0F : 2.0F;
        double kbStrength = isDropKick ? 3.5 : 1.5;
        double upwardPop = isDropKick ? 0.55 : 0.35;
        float yaw = player.getYaw();
        double fwdX = -Math.sin(Math.toRadians(yaw));
        double fwdZ = Math.cos(Math.toRadians(yaw));
        Box searchBox = player.getBoundingBox().expand(reach + 0.5);
        List<Entity> candidates = world.getOtherEntities(player, searchBox, e -> e instanceof LivingEntity && !e.isRemoved() && !e.isSpectator());
        boolean hitAny = false;
        List<ServerPlayerEntity> playerHits = new ArrayList<>();

        for (Entity target : candidates) {
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (!(dist > reach)) {
                double dot = dist < 0.01 ? 1.0 : (fwdX * dx + fwdZ * dz) / dist;
                if (!(dot < 0.25)) {
                    target.damage(world.getDamageSources().playerAttack(player), damage);
                    if (target instanceof LivingEntity living) {
                        if (isDropKick) {
                            living.setVelocity(fwdX * 4.0, 0.8, fwdZ * 4.0);
                        } else {
                            living.takeKnockback(kbStrength, -dx, -dz);
                            Vec3d vel2 = living.getVelocity();
                            living.setVelocity(vel2.x, upwardPop, vel2.z);
                        }

                        living.velocityModified = true;
                    }

                    if (target instanceof ServerPlayerEntity hitPlayer) {
                        playerHits.add(hitPlayer);
                    }

                    Vec3d hitPos = player.getPos()
                        .add(target.getPos())
                        .multiply(0.5)
                        .add(0.0, player.getHeight() * 0.55, 0.0);
                    if (isDropKick) {
                        world.spawnParticles(ParticleTypes.EXPLOSION, hitPos.x, hitPos.y, hitPos.z, 1, 0.2, 0.2, 0.2, 0.0);
                        world.spawnParticles(ParticleTypes.CRIT, hitPos.x, hitPos.y, hitPos.z, 5, 0.3, 0.3, 0.3, 0.15);
                        world.spawnParticles(ParticleTypes.ENCHANTED_HIT, hitPos.x, hitPos.y, hitPos.z, 4, 0.2, 0.2, 0.2, 0.1);
                    } else {
                        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, hitPos.x, hitPos.y, hitPos.z, 5, 0.2, 0.2, 0.2, 0.1);
                        world.spawnParticles(ParticleTypes.CRIT, hitPos.x, hitPos.y, hitPos.z, 8, 0.2, 0.2, 0.2, 0.1);
                    }

                    hitAny = true;
                }
            }
        }

        if (isDropKick && playerHits.size() == 1) {
            ServerPlayerEntity other = playerHits.get(0);
            Vec3d mid = player.getPos().add(other.getPos()).multiply(0.5).add(0.0, 1.0, 0.0);
            world.spawnParticles(ParticleTypes.EXPLOSION, mid.x, mid.y, mid.z, 2, 0.3, 0.3, 0.3, 0.0);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, mid.x, mid.y, mid.z, 1, 0.2, 0.2, 0.2, 0.0);
            world.spawnParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 8, 0.4, 0.4, 0.4, 0.2);
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT, mid.x, mid.y, mid.z, 6, 0.3, 0.3, 0.3, 0.15);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, mid.x, mid.y, mid.z, 4, 0.3, 0.3, 0.3, 0.02);

            for (int i = 0; i < 8; i++) {
                double angle = (Math.PI / 4) * i;
                double rx = mid.x + Math.cos(angle) * 1.5;
                double rz = mid.z + Math.sin(angle) * 1.5;
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, rx, mid.y - 0.8, rz, 1, 0.0, 0.0, 0.0, 0.0);
            }

            world.playSound(
                null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2F, 0.8F
            );
            world.playSound(null, mid.x, mid.y, mid.z, ModSounds.EXPLOSION_IMPACT, SoundCategory.PLAYERS, 1.0F, 0.7F);
            hitAny = false;
        }

        if (hitAny && isDropKick) {
            world.playSound(
                null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.0F, 1.0F
            );
            world.playSound(
                null, player.getX(), player.getY(), player.getZ(), ModSounds.EXPLOSION_IMPACT, SoundCategory.PLAYERS, 0.9F, 0.9F
            );
        } else if (!hitAny) {
            world.playSound(
                null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.4F, 1.2F
            );
        }

        PoseNetworking.broadcastAnimState(player, isDropKick ? 62 : 61);
        long now = System.currentTimeMillis();
        kickPushWindowEnd.put(id, now + 540L);
        kickPushFwd.put(id, new Vec3d(fwdX, 0.0, fwdZ));
        if (!isDropKick) {
            player.addVelocity(fwdX * 0.3, 0.0, fwdZ * 0.3);
            player.velocityModified = true;
            applySlowdown(player, -0.4);
            slowRemoveAt.put(id, now + 1000L);
        } else {
            player.addVelocity(fwdX * 0.5, 0.0, fwdZ * 0.5);
            player.velocityModified = true;
            slowApplyAt.put(id, now + 830L);
            slowRemoveAt.put(id, now + 1750L);
            slowAmount.put(id, -0.9);
        }

        ServerPlayNetworking.send(player, new KickHandler.KickResultPayload(isDropKick, hitAny));
        cooldownEnd.put(id, now + 2000L);
        ServerPlayNetworking.send(player, new KickHandler.KickCooldownPayload(2000L));
    }

    private static void applySlowdown(ServerPlayerEntity player, double amount) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(KICK_SLOW_ID);
            attr.addTemporaryModifier(new EntityAttributeModifier(KICK_SLOW_ID, "kick_slow_id", amount, Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeSlowdown(ServerPlayerEntity player) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(KICK_SLOW_ID);
        }
    }

    private static boolean isOnCooldown(UUID id) {
        Long end = cooldownEnd.get(id);
        return end != null && System.currentTimeMillis() < end;
    }

    private static void broadcastChargeSync(ServerPlayerEntity player, boolean isCharging, float pct) {
        KickHandler.KickChargeSyncPayload pkt = new KickHandler.KickChargeSyncPayload(player.getUuid(), isCharging, pct);

        for (ServerPlayerEntity other : PlayerLookup.around(player.getServerWorld(), player.getPos(), 30.0)) {
            ServerPlayNetworking.send(other, pkt);
        }

        ServerPlayNetworking.send(player, pkt);
    }

    public static void cleanup(UUID id) {
        chargeStart.remove(id);
        cooldownEnd.remove(id);
        lastSyncTick.remove(id);
        slowApplyAt.remove(id);
        slowRemoveAt.remove(id);
        slowAmount.remove(id);
        kickPushWindowEnd.remove(id);
        kickPushFwd.remove(id);
    }

    public record KickChargeSyncPayload(UUID playerId, boolean isCharging, float chargePercent) implements CustomPayload {
        public static final Id<KickHandler.KickChargeSyncPayload> ID = new Id(new Identifier("testcoop", "kick_charge_sync"));
        public static final PacketCodec<PacketByteBuf, KickHandler.KickChargeSyncPayload> CODEC = PacketCodec.of((val, buf) -> {
            buf.writeUuid(val.playerId());
            buf.writeBoolean(val.isCharging());
            buf.writeFloat(val.chargePercent());
        }, buf -> new KickHandler.KickChargeSyncPayload(buf.readUuid(), buf.readBoolean(), buf.readFloat()));

        public Id<KickHandler.KickChargeSyncPayload> getId() {
            return ID;
        }
    }

    public record KickCooldownPayload(long cooldownMs) implements CustomPayload {
        public static final Id<KickHandler.KickCooldownPayload> ID = new Id(new Identifier("testcoop", "kick_cooldown"));
        public static final PacketCodec<PacketByteBuf, KickHandler.KickCooldownPayload> CODEC = PacketCodec.of(
            (val, buf) -> buf.writeLong(val.cooldownMs()), buf -> new KickHandler.KickCooldownPayload(buf.readLong())
        );

        public Id<KickHandler.KickCooldownPayload> getId() {
            return ID;
        }
    }

    public record KickReleasePayload() implements CustomPayload {
        public static final Id<KickHandler.KickReleasePayload> ID = new Id(new Identifier("testcoop", "kick_release"));
        public static final PacketCodec<PacketByteBuf, KickHandler.KickReleasePayload> CODEC = PacketCodec.unit(new KickHandler.KickReleasePayload());

        public Id<KickHandler.KickReleasePayload> getId() {
            return ID;
        }
    }

    public record KickResultPayload(boolean isDropKick, boolean hit) implements CustomPayload {
        public static final Id<KickHandler.KickResultPayload> ID = new Id(new Identifier("testcoop", "kick_result"));
        public static final PacketCodec<PacketByteBuf, KickHandler.KickResultPayload> CODEC = PacketCodec.of((val, buf) -> {
            buf.writeBoolean(val.isDropKick());
            buf.writeBoolean(val.hit());
        }, buf -> new KickHandler.KickResultPayload(buf.readBoolean(), buf.readBoolean()));

        public Id<KickHandler.KickResultPayload> getId() {
            return ID;
        }
    }

    public record KickStartPayload(boolean dropKickMode) implements CustomPayload {
        public static final Id<KickHandler.KickStartPayload> ID = new Id(new Identifier("testcoop", "kick_start"));
        public static final PacketCodec<PacketByteBuf, KickHandler.KickStartPayload> CODEC = PacketCodec.of(
            (val, buf) -> buf.writeBoolean(val.dropKickMode()), buf -> new KickHandler.KickStartPayload(buf.readBoolean())
        );

        public Id<KickHandler.KickStartPayload> getId() {
            return ID;
        }
    }
}
