package com.cooptest;

import com.cooptest.client.CoopAnimationHandler;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Hand;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
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

public class FallDapHandler {
    private static final Map<UUID, FallDapHandler.FallDapState> fallDapPlayers = new HashMap<>();
    private static final Map<UUID, Long> fallChargeStartTime = new HashMap<>();
    private static final Map<UUID, Long> squashedPlayers = new HashMap<>();
    private static final long SQUASHED_DURATION_MS = 25000L;
    private static final Map<UUID, Double> fallStartY = new HashMap<>();
    private static final double REQUIRED_FALL_BLOCKS = 20.0;
    private static final long FALL_CHARGE_DURATION_MS = 750L;

    public static void register() {
        PayloadTypeRegistry.playS2C().register(FallDapHandler.FallDapAnimPayload.ID, FallDapHandler.FallDapAnimPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FallDapHandler.SquashAnimPayload.ID, FallDapHandler.SquashAnimPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> tick(server));
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<Entry<UUID, Long>> squashIt = squashedPlayers.entrySet().iterator();

        while (squashIt.hasNext()) {
            Entry<UUID, Long> entry = squashIt.next();
            UUID playerId = entry.getKey();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (now >= entry.getValue()) {
                squashIt.remove();
                if (player != null) {
                    PoseNetworking.broadcastAnimState(player, 0);
                    player.removeStatusEffect(StatusEffects.field_5909);
                    player.removeStatusEffect(StatusEffects.field_5913);
                }
            } else if (player != null) {
                if (!player.hasStatusEffect(StatusEffects.field_5909) || player.getStatusEffect(StatusEffects.field_5909).getDuration() < 40) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5909, 60, 2, false, false));
                }

                if (!player.hasStatusEffect(StatusEffects.field_5913) || player.getStatusEffect(StatusEffects.field_5913).getDuration() < 40) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5913, 60, 250, false, false));
                }
            }
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerId = player.getUuid();
            if (!isSquashed(playerId)) {
                if (player.getEquippedStack(EquipmentSlot.field_6174).isOf(Items.field_8833)) {
                    cleanup(playerId);
                } else if (player.isSprinting() && ChargedDapHandler.isFullyCharged(playerId)) {
                    cleanup(playerId);
                } else {
                    boolean isChargingDap = ChargedDapHandler.isCharging(playerId);
                    boolean isOnGround = player.isOnGround();
                    boolean isFalling = player.getVelocity().y < -0.1;
                    FallDapHandler.FallDapState currentState = fallDapPlayers.getOrDefault(playerId, FallDapHandler.FallDapState.NONE);
                    if (currentState == FallDapHandler.FallDapState.NONE) {
                        if (isChargingDap && isFalling && !isOnGround) {
                            if (!fallStartY.containsKey(playerId)) {
                                fallStartY.put(playerId, player.getY());
                            }

                            double startY = fallStartY.get(playerId);
                            double fallen = startY - player.getY();
                            if (fallen >= 20.0 && ChargedDapHandler.isFullyCharged(playerId)) {
                                startFallDapCharge(player);
                            }
                        } else if (isOnGround) {
                            fallStartY.remove(playerId);
                        }
                    } else if (currentState == FallDapHandler.FallDapState.CHARGING) {
                        Long chargeStart = fallChargeStartTime.get(playerId);
                        if (chargeStart != null && now - chargeStart >= 750L) {
                            fallDapPlayers.put(playerId, FallDapHandler.FallDapState.FALLING);
                            broadcastFallDapAnim(player, 2);
                            player.sendMessage(Text.literal("§c§l FALL DAP READY! "), true);
                        }

                        if (isOnGround) {
                            if (isChargingDap) {
                                resetToNormalCharge(player);
                            } else {
                                cleanup(playerId);
                                PoseNetworking.broadcastAnimState(player, 0);
                            }
                        }
                    } else if (currentState == FallDapHandler.FallDapState.FALLING) {
                        ServerPlayerEntity victim = findSquashTarget(player, 3.0);
                        if (victim != null) {
                            squashPlayer(player, victim);
                            cleanup(playerId);
                        } else if (isOnGround) {
                            if (isChargingDap) {
                                resetToNormalCharge(player);
                            } else {
                                cleanup(playerId);
                                PoseNetworking.broadcastAnimState(player, 0);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void startFallDapCharge(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        fallDapPlayers.put(playerId, FallDapHandler.FallDapState.CHARGING);
        fallChargeStartTime.put(playerId, System.currentTimeMillis());
        broadcastFallDapAnim(player, 1);
        player.sendMessage(Text.literal("§e§l⚡ FALL DAP CHARGING! ⚡"), true);
    }

    private static void resetToNormalCharge(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        fallDapPlayers.remove(playerId);
        fallStartY.remove(playerId);
        fallChargeStartTime.remove(playerId);
        broadcastFallDapAnim(player, 0);
        PoseNetworking.broadcastAnimState(player, CoopAnimationHandler.AnimState.DAP_CHARGE_IDLE.ordinal());
        player.sendMessage(Text.literal("§7Fall dap reset - touched ground"), true);
    }

    public static boolean isInFallDapState(UUID playerId) {
        FallDapHandler.FallDapState state = fallDapPlayers.get(playerId);
        return state == FallDapHandler.FallDapState.CHARGING || state == FallDapHandler.FallDapState.FALLING;
    }

    public static boolean isReadyToFallDap(UUID playerId) {
        return fallDapPlayers.get(playerId) == FallDapHandler.FallDapState.FALLING;
    }

    public static void executeFallDapHit(ServerWorld world, Vec3d pos, ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        UUID attackerId = attacker.getUuid();
        broadcastFallDapAnim(attacker, 3);
        cleanup(attackerId);
    }

    private static void squashPlayer(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        ServerWorld world = attacker.getServerWorld();
        Vec3d pos = victim.getPos();
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.field_14833, SoundCategory.field_15248, 2.0F, 0.5F);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.field_15115, SoundCategory.field_15248, 1.0F, 0.8F);
        world.spawnParticles(ParticleTypes.field_11205, pos.x, pos.y + 1.0, pos.z, 30, 0.5, 0.3, 0.5, 0.2);
        world.spawnParticles(ParticleTypes.field_11251, pos.x, pos.y, pos.z, 20, 0.5, 0.2, 0.5, 0.05);
        dropHandItems(victim, world, pos);
        victim.damage(world.getDamageSources().playerAttack(attacker), 10.0F);
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5909, 420, 2, false, false));
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5913, 420, 250, false, false));
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5916, 300, 0, false, false));
        victim.setVelocity(0.0, 0.0, 0.0);
        victim.velocityModified = true;
        squashedPlayers.put(victim.getUuid(), System.currentTimeMillis() + 25000L);

        for (ServerPlayerEntity p : world.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, new FallDapHandler.SquashAnimPayload(victim.getUuid()));
        }

        attacker.sendMessage(Text.literal("§c§l\ud83d\udc80 SQUASHED! \ud83d\udc80"), true);
        victim.sendMessage(Text.literal("§c§lYOU GOT SQUASHED FOR 25 SECONDS!"), true);
    }

    private static void dropHandItems(ServerPlayerEntity player, ServerWorld world, Vec3d pos) {
        ItemStack mainStack = player.getMainHandStack();
        if (!mainStack.isEmpty()) {
            ItemEntity mainItem = new ItemEntity(world, pos.x, pos.y + 0.5, pos.z, mainStack.copy());
            mainItem.setVelocity(
                (world.random.nextDouble() - 0.5) * 0.3, world.random.nextDouble() * 0.2 + 0.1, (world.random.nextDouble() - 0.5) * 0.3
            );
            world.spawnEntity(mainItem);
            player.setStackInHand(Hand.field_5808, ItemStack.EMPTY);
        }

        ItemStack offStack = player.getOffHandStack();
        if (!offStack.isEmpty()) {
            ItemEntity offItem = new ItemEntity(world, pos.x, pos.y + 0.5, pos.z, offStack.copy());
            offItem.setVelocity(
                (world.random.nextDouble() - 0.5) * 0.3, world.random.nextDouble() * 0.2 + 0.1, (world.random.nextDouble() - 0.5) * 0.3
            );
            world.spawnEntity(offItem);
            player.setStackInHand(Hand.field_5810, ItemStack.EMPTY);
        }

        ItemStack helmet = player.getEquippedStack(EquipmentSlot.field_6169);
        if (!helmet.isEmpty()) {
            ItemEntity helmetItem = new ItemEntity(world, pos.x, pos.y + 1.0, pos.z, helmet.copy());
            helmetItem.setVelocity(
                (world.random.nextDouble() - 0.5) * 0.4, world.random.nextDouble() * 0.4 + 0.3, (world.random.nextDouble() - 0.5) * 0.4
            );
            world.spawnEntity(helmetItem);
            player.equipStack(EquipmentSlot.field_6169, ItemStack.EMPTY);
        }
    }

    private static ServerPlayerEntity findSquashTarget(ServerPlayerEntity attacker, double horizontalRange) {
        double attackerY = attacker.getY();

        for (ServerPlayerEntity other : attacker.getServerWorld().getPlayers()) {
            if (other != attacker && !isSquashed(other.getUuid())) {
                double otherY = other.getY();
                double heightDiff = attackerY - otherY;
                if (!(heightDiff < 0.5) && !(heightDiff > 3.0)) {
                    double dx = attacker.getX() - other.getX();
                    double dz = attacker.getZ() - other.getZ();
                    double horizontalDist = Math.sqrt(dx * dx + dz * dz);
                    if (horizontalDist <= horizontalRange) {
                        return other;
                    }
                }
            }
        }

        return null;
    }

    private static ServerPlayerEntity findNearbyPlayer(ServerPlayerEntity player, double range) {
        for (ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
            if (other != player && other.squaredDistanceTo(player) <= range * range) {
                return other;
            }
        }

        return null;
    }

    public static boolean isSquashed(UUID playerId) {
        Long endTime = squashedPlayers.get(playerId);
        if (endTime == null) {
            return false;
        } else if (System.currentTimeMillis() >= endTime) {
            squashedPlayers.remove(playerId);
            return false;
        } else {
            return true;
        }
    }

    private static void broadcastFallDapAnim(ServerPlayerEntity player, int state) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            FallDapHandler.FallDapAnimPayload payload = new FallDapHandler.FallDapAnimPayload(player.getUuid(), state);

            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    public static void cleanup(UUID playerId) {
        fallDapPlayers.remove(playerId);
        fallStartY.remove(playerId);
        fallChargeStartTime.remove(playerId);
        squashedPlayers.remove(playerId);
    }

    public record FallDapAnimPayload(UUID playerId, int state) implements CustomPayload {
        public static final Id<FallDapHandler.FallDapAnimPayload> ID = new Id(new Identifier("testcoop", "fall_dap_anim"));
        public static final PacketCodec<PacketByteBuf, FallDapHandler.FallDapAnimPayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeUuid(payload.playerId);
            buf.writeInt(payload.state);
        }, buf -> new FallDapHandler.FallDapAnimPayload(buf.readUuid(), buf.readInt()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static enum FallDapState {
        NONE,
        CHARGING,
        FALLING;
    }

    public record SquashAnimPayload(UUID playerId) implements CustomPayload {
        public static final Id<FallDapHandler.SquashAnimPayload> ID = new Id(new Identifier("testcoop", "squash_anim"));
        public static final PacketCodec<PacketByteBuf, FallDapHandler.SquashAnimPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeUuid(payload.playerId), buf -> new FallDapHandler.SquashAnimPayload(buf.readUuid())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
