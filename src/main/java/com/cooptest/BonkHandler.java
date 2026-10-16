package com.cooptest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
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

public class BonkHandler {
    public static boolean ENABLED = true;
    private static final int ANIM_LAY_DOWN = 79;
    private static final int ANIM_BONK = 80;
    private static final int ANIM_NONE = 0;
    private static final double BONKER_Y_OFFSET = 0.6;
    private static final double BONKER_XZ_OFFSET = 0.6;
    private static final double VICTIM_Y_SINK = 1.0;
    private static final long LOOP_MS = 3125L;
    private static final long HIT_RIGHT_1 = 375L;
    private static final long HIT_LEFT_1 = 667L;
    private static final long HIT_RIGHT_2 = 1417L;
    private static final long HIT_ANVIL = 2542L;
    private static final long HIT_LEFT_2 = 2917L;
    private static final long HIT_WINDOW = 100L;
    private static final float CAM_SIDE = 40.0F;
    private static final float CAM_UP = -55.0F;
    private static final double JOIN_RANGE = 2.5;
    public static final Identifier BONK_CAMERA_ID = new Identifier("cooptest", "bonk_camera");
    public static final Identifier BONK_MOVE_LOCK_ID = new Identifier("cooptest", "bonk_move_lock");
    public static final Identifier BONK_L_KEY_ID = new Identifier("cooptest", "bonk_l_key");
    private static final Map<UUID, Float> layingPlayers = new HashMap<>();
    private static final Map<UUID, UUID> bonkSessions = new HashMap<>();
    private static final Map<UUID, UUID> victimMap = new HashMap<>();
    private static final Map<UUID, Long> sessionStartMs = new HashMap<>();
    private static final Map<UUID, Float> victimLockedYaw = new HashMap<>();
    private static final Map<UUID, Set<Long>> firedThisCycle = new HashMap<>();

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(BonkHandler.BonkCameraPayload.ID, BonkHandler.BonkCameraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BonkHandler.BonkMoveLockPayload.ID, BonkHandler.BonkMoveLockPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BonkHandler.BonkLKeyPayload.ID, BonkHandler.BonkLKeyPayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(BonkHandler.BonkLKeyPayload.ID, (payload, ctx) -> ctx.server().execute(() -> onLKey(ctx.player())));
        ServerTickEvents.END_SERVER_TICK.register(BonkHandler::tick);
    }

    public static void onLKey(ServerPlayerEntity player) {
        if (ENABLED) {
            UUID id = player.getUuid();
            if (bonkSessions.containsKey(id)) {
                stopBonk(id, player.getServer());
            } else if (layingPlayers.containsKey(id)) {
                stopLaying(id, player.getServer());
            } else {
                UUID victim = findNearbyLaying(player);
                if (victim != null) {
                    startBonk(player, victim);
                } else {
                    startLaying(player);
                }
            }
        }
    }

    private static void startLaying(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        layingPlayers.put(id, player.getYaw());
        player.teleport(
            player.getServerWorld(), player.getX(), player.getY() - 1.0, player.getZ(), Set.of(), player.getYaw(), 0.0F
        );
        PoseNetworking.broadcastAnimState(player, 79);
        ServerPlayNetworking.send(player, new BonkHandler.BonkMoveLockPayload(true));
        player.sendMessage(Text.literal("§7[Laying down — press L to get up]"), true);
    }

    private static void stopLaying(UUID id, MinecraftServer server) {
        layingPlayers.remove(id);
        sessionStartMs.remove(id);
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
        if (p != null) {
            p.teleport(p.getServerWorld(), p.getX(), p.getY() + 1.0, p.getZ(), Set.of(), p.getYaw(), 0.0F);
            PoseNetworking.broadcastAnimState(p, 0);
            ServerPlayNetworking.send(p, new BonkHandler.BonkMoveLockPayload(false));
        }
    }

    private static void startBonk(ServerPlayerEntity bonker, UUID victimId) {
        MinecraftServer server = bonker.getServer();
        if (server != null) {
            ServerPlayerEntity victim = server.getPlayerManager().getPlayer(victimId);
            if (victim != null) {
                UUID bonkerId = bonker.getUuid();
                bonkSessions.put(bonkerId, victimId);
                victimMap.put(victimId, bonkerId);
                sessionStartMs.put(bonkerId, System.currentTimeMillis());
                victimLockedYaw.put(bonkerId, victim.getYaw());
                Vec3d vPos = victim.getPos();
                float lockedYaw = victim.getYaw();
                double fwdX = -Math.sin(Math.toRadians(lockedYaw));
                double fwdZ = Math.cos(Math.toRadians(lockedYaw));
                double targetX = vPos.x + fwdX * 0.6;
                double targetZ = vPos.z + fwdZ * 0.6;
                double targetY = vPos.y + 0.6;
                float yaw = lockedYaw + 180.0F;
                float pitch = 30.0F;
                bonker.teleport(bonker.getServerWorld(), targetX, targetY, targetZ, Set.of(), yaw, pitch);
                bonker.setYaw(yaw);
                bonker.setBodyYaw(yaw);
                bonker.setHeadYaw(yaw);
                bonker.setInvulnerable(true);
                PoseNetworking.broadcastAnimState(bonker, 80);
                ServerPlayNetworking.send(bonker, new BonkHandler.BonkMoveLockPayload(true));
                bonker.sendMessage(Text.literal("§c§lBONK! §7Press L to stop"), true);
                victim.sendMessage(Text.literal("§c§lYou're getting bonked! §7Press L to escape"), true);
            }
        }
    }

    private static void stopBonk(UUID bonkerId, MinecraftServer server) {
        UUID victimId = bonkSessions.remove(bonkerId);
        if (victimId != null) {
            victimMap.remove(victimId);
        }

        sessionStartMs.remove(bonkerId);
        victimLockedYaw.remove(bonkerId);
        ServerPlayerEntity bonker = server.getPlayerManager().getPlayer(bonkerId);
        if (bonker != null) {
            bonker.setInvulnerable(false);
            PoseNetworking.broadcastAnimState(bonker, 0);
            ServerPlayNetworking.send(bonker, new BonkHandler.BonkMoveLockPayload(false));
            bonker.addVelocity(0.0, 0.3, 0.0);
            bonker.velocityModified = true;
        }

        if (victimId != null) {
            layingPlayers.remove(victimId);
            ServerPlayerEntity victim = server.getPlayerManager().getPlayer(victimId);
            if (victim != null) {
                victim.teleport(
                    victim.getServerWorld(), victim.getX(), victim.getY() + 1.0, victim.getZ(), Set.of(), victim.getYaw(), 0.0F
                );
                PoseNetworking.broadcastAnimState(victim, 0);
                ServerPlayNetworking.send(victim, new BonkHandler.BonkMoveLockPayload(false));
            }
        }
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();

        for (UUID id : new ArrayList<>(layingPlayers.keySet())) {
            if (!victimMap.containsKey(id)) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                if (p == null) {
                    stopLaying(id, server);
                } else {
                    p.setVelocity(0.0, 0.0, 0.0);
                    p.velocityModified = true;
                }
            }
        }

        for (UUID bonkerId : new ArrayList<>(bonkSessions.keySet())) {
            UUID victimId = bonkSessions.get(bonkerId);
            ServerPlayerEntity bonker = server.getPlayerManager().getPlayer(bonkerId);
            ServerPlayerEntity victim = server.getPlayerManager().getPlayer(victimId);
            if (bonker != null && victim != null) {
                Vec3d vPos = victim.getPos();
                float lockedYaw = victimLockedYaw.getOrDefault(bonkerId, victim.getYaw());
                double fwdX = -Math.sin(Math.toRadians(lockedYaw));
                double fwdZ = Math.cos(Math.toRadians(lockedYaw));
                double bx = vPos.x + fwdX * 0.6;
                double bz = vPos.z + fwdZ * 0.6;
                float bonkerYaw = lockedYaw + 180.0F;
                float bonkerPitch = 30.0F;
                bonker.teleport(bonker.getServerWorld(), bx, vPos.y + 0.6, bz, Set.of(), bonkerYaw, bonkerPitch);
                bonker.setInvulnerable(true);
                victim.setVelocity(0.0, 0.0, 0.0);
                victim.velocityModified = true;
                Long startMs = sessionStartMs.get(bonkerId);
                if (startMs != null) {
                    long loopElapsed = (now - startMs) % 3125L;
                    ServerWorld world = bonker.getServerWorld();
                    Vec3d handPos = bonker.getPos().add(0.0, 1.4, 0.0);
                    fireIfInWindow(
                        loopElapsed,
                        375L,
                        bonkerId,
                        () -> {
                            spawnImpactParticles(world, handPos);
                            world.playSound(
                                null, handPos.x, handPos.y, handPos.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.2F
                            );
                            world.playSound(
                                null, handPos.x, handPos.y, handPos.z, ModSounds.DAP_HIT, SoundCategory.PLAYERS, 0.9F, 1.1F
                            );
                            ServerPlayNetworking.send(victim, new BonkHandler.BonkCameraPayload(40.0F, 0.0F));
                        }
                    );
                    fireIfInWindow(
                        loopElapsed,
                        667L,
                        bonkerId,
                        () -> {
                            spawnImpactParticles(world, handPos);
                            world.playSound(
                                null, handPos.x, handPos.y, handPos.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.0F
                            );
                            world.playSound(
                                null, handPos.x, handPos.y, handPos.z, ModSounds.DAP_HIT, SoundCategory.PLAYERS, 0.9F, 0.9F
                            );
                            ServerPlayNetworking.send(victim, new BonkHandler.BonkCameraPayload(-40.0F, 0.0F));
                        }
                    );
                    fireIfInWindow(
                        loopElapsed,
                        1417L,
                        bonkerId,
                        () -> {
                            spawnImpactParticles(world, handPos);
                            world.playSound(
                                null, handPos.x, handPos.y, handPos.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.3F
                            );
                            ServerPlayNetworking.send(victim, new BonkHandler.BonkCameraPayload(40.0F, 0.0F));
                        }
                    );
                    fireIfInWindow(loopElapsed, 2542L, bonkerId, () -> {
                        Vec3d mid = victim.getPos().add(0.0, 1.0, 0.0);
                        world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.5F, 0.8F);
                        world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.2F, 0.6F);
                        world.spawnParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 20, 0.4, 0.3, 0.4, 0.15);
                        world.spawnParticles(ParticleTypes.ENCHANTED_HIT, mid.x, mid.y, mid.z, 12, 0.3, 0.2, 0.3, 0.1);
                        world.spawnParticles(ParticleTypes.FLASH, mid.x, mid.y, mid.z, 2, 0.0, 0.0, 0.0, 0.0);
                        world.spawnParticles(ParticleTypes.EXPLOSION, mid.x, mid.y, mid.z, 3, 0.2, 0.1, 0.2, 0.05);
                        ServerPlayNetworking.send(victim, new BonkHandler.BonkCameraPayload(0.0F, -55.0F));
                    });
                    fireIfInWindow(
                        loopElapsed,
                        2917L,
                        bonkerId,
                        () -> {
                            spawnImpactParticles(world, handPos);
                            world.playSound(
                                null, handPos.x, handPos.y, handPos.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 0.9F, 1.1F
                            );
                            ServerPlayNetworking.send(victim, new BonkHandler.BonkCameraPayload(-40.0F, 0.0F));
                        }
                    );
                }
            } else {
                stopBonk(bonkerId, server);
            }
        }
    }

    private static void fireIfInWindow(long loopElapsed, long target, UUID bonkerId, Runnable action) {
        if (loopElapsed >= target && loopElapsed < target + 100L) {
            Set<Long> fired = firedThisCycle.computeIfAbsent(bonkerId, k -> new HashSet<>());
            Long sStart = sessionStartMs.get(bonkerId);
            long cycleIndex = sStart != null ? (System.currentTimeMillis() - sStart) / 3125L : 0L;
            long cycleKey = cycleIndex * 100000L + target;
            if (fired.add(cycleKey)) {
                action.run();
                fired.removeIf(k -> k < cycleKey - 3125L);
            }
        }
    }

    private static void spawnImpactParticles(ServerWorld world, Vec3d pos) {
        world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 8, 0.2, 0.2, 0.2, 0.1);
        world.spawnParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y, pos.z, 4, 0.15, 0.15, 0.15, 0.06);
    }

    private static UUID findNearbyLaying(ServerPlayerEntity bonker) {
        for (UUID id : layingPlayers.keySet()) {
            if (!victimMap.containsKey(id)) {
                ServerPlayerEntity victim = bonker.getServer().getPlayerManager().getPlayer(id);
                if (victim != null && bonker.distanceTo(victim) <= 2.5) {
                    return id;
                }
            }
        }

        return null;
    }

    public static boolean isLaying(UUID id) {
        return layingPlayers.containsKey(id) || victimMap.containsKey(id);
    }

    public static boolean isInBonkSession(UUID id) {
        return bonkSessions.containsKey(id) || victimMap.containsKey(id) || layingPlayers.containsKey(id);
    }

    public static void cleanup(UUID id) {
        layingPlayers.remove(id);
        firedThisCycle.remove(id);
        UUID victim = bonkSessions.remove(id);
        if (victim != null) {
            victimMap.remove(victim);
        }

        UUID bonker = victimMap.remove(id);
        if (bonker != null) {
            bonkSessions.remove(bonker);
        }

        sessionStartMs.remove(id);
        victimLockedYaw.remove(id);
    }

    public record BonkCameraPayload(float deltaYaw, float deltaPitch) implements CustomPayload {
        public static final Id<BonkHandler.BonkCameraPayload> ID = new Id(BonkHandler.BONK_CAMERA_ID);
        public static final PacketCodec<PacketByteBuf, BonkHandler.BonkCameraPayload> CODEC = PacketCodec.of((v, b) -> {
            b.writeFloat(v.deltaYaw);
            b.writeFloat(v.deltaPitch);
        }, b -> new BonkHandler.BonkCameraPayload(b.readFloat(), b.readFloat()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BonkLKeyPayload() implements CustomPayload {
        public static final Id<BonkHandler.BonkLKeyPayload> ID = new Id(BonkHandler.BONK_L_KEY_ID);
        public static final PacketCodec<PacketByteBuf, BonkHandler.BonkLKeyPayload> CODEC = PacketCodec.unit(new BonkHandler.BonkLKeyPayload());

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BonkMoveLockPayload(boolean locked) implements CustomPayload {
        public static final Id<BonkHandler.BonkMoveLockPayload> ID = new Id(BonkHandler.BONK_MOVE_LOCK_ID);
        public static final PacketCodec<PacketByteBuf, BonkHandler.BonkMoveLockPayload> CODEC = PacketCodec.of(
            (v, b) -> b.writeBoolean(v.locked), b -> new BonkHandler.BonkMoveLockPayload(b.readBoolean())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
