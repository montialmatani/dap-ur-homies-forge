package com.cooptest;

import com.cooptest.spin.HandSpinHandler;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDamage;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.PlayerLookup;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.entity.damage.DamageTypes;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public class FallCatchHandler {
    public static final double CATCH_RANGE_HORIZONTAL = 3.0;
    public static final double CATCH_RANGE_VERTICAL = 4.0;
    public static final long CATCH_WINDOW_MS = 500L;
    public static final long CATCH_COOLDOWN_MS = 1000L;
    private static final Map<UUID, Long> catchReadyTime = new HashMap<>();
    private static final Map<UUID, Long> catchCooldowns = new HashMap<>();
    private static final Map<UUID, Boolean> successfulCatch = new HashMap<>();
    public static final Identifier CATCH_ANIM_ID = new Identifier("cooptest", "catch_anim");

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(FallCatchHandler.CatchAnimPayload.ID, FallCatchHandler.CatchAnimPayload.CODEC);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            long now = System.currentTimeMillis();
            Iterator<Entry<UUID, Long>> it = catchReadyTime.entrySet().iterator();

            while (it.hasNext()) {
                Entry<UUID, Long> entry = it.next();
                UUID playerId = entry.getKey();
                long readyTime = entry.getValue();
                PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
                if (pose != PoseState.GRAB_READY) {
                    if (!successfulCatch.getOrDefault(playerId, false)) {
                        catchCooldowns.put(playerId, now + 1000L);
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                        if (player != null) {
                            player.sendMessage(Text.literal("§c✗ Catch missed! 1 sec cooldown"), true);
                        }
                    }

                    it.remove();
                    successfulCatch.remove(playerId);
                }
            }

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID playerId = player.getUuid();
                PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
                if (pose == PoseState.GRAB_READY && !catchReadyTime.containsKey(playerId) && !HandSpinHandler.isInSession(playerId)) {
                    catchReadyTime.put(playerId, now);
                    successfulCatch.put(playerId, false);
                }
            }
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((AllowDamage)(entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity fallingPlayer) {
                if (!source.isOf(DamageTypes.FALL)) {
                    return true;
                } else {
                    ServerPlayerEntity catcher = findCatcher(fallingPlayer);
                    if (catcher != null) {
                        playCatchEffects(fallingPlayer, catcher);
                        successfulCatch.put(catcher.getUuid(), true);
                        return false;
                    } else {
                        return true;
                    }
                }
            } else {
                return true;
            }
        });
    }

    public static boolean isInCatchReadyMode(UUID playerId) {
        PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
        return pose == PoseState.GRAB_READY;
    }

    public static boolean isOnCatchCooldown(UUID playerId) {
        Long cooldownEnd = catchCooldowns.get(playerId);
        if (cooldownEnd == null) {
            return false;
        } else if (System.currentTimeMillis() >= cooldownEnd) {
            catchCooldowns.remove(playerId);
            return false;
        } else {
            return true;
        }
    }

    public static boolean canEnterCatchReady(UUID playerId) {
        return !isOnCatchCooldown(playerId);
    }

    private static ServerPlayerEntity findCatcher(ServerPlayerEntity fallingPlayer) {
        ServerWorld world = fallingPlayer.getServerWorld();
        long now = System.currentTimeMillis();
        Box searchBox = new Box(
            fallingPlayer.getX() - 3.0,
            fallingPlayer.getY() - 2.0,
            fallingPlayer.getZ() - 3.0,
            fallingPlayer.getX() + 3.0,
            fallingPlayer.getY() + 1.0,
            fallingPlayer.getZ() + 3.0
        );

        for (PlayerEntity player : world.getPlayers()) {
            if (player != fallingPlayer && player instanceof ServerPlayerEntity catcher) {
                UUID catcherId = catcher.getUuid();
                PoseState pose = PoseNetworking.poseStates.getOrDefault(catcherId, PoseState.NONE);
                if (pose == PoseState.GRAB_READY && !HandSpinHandler.isInSession(catcherId) && !isOnCatchCooldown(catcherId)) {
                    Long readyTime = catchReadyTime.get(catcherId);
                    if (readyTime != null && searchBox.contains(catcher.getPos())) {
                        double dx = fallingPlayer.getX() - catcher.getX();
                        double dz = fallingPlayer.getZ() - catcher.getZ();
                        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
                        if (horizontalDist <= 3.0) {
                            return catcher;
                        }
                    }
                }
            }
        }

        return null;
    }

    private static void playCatchEffects(ServerPlayerEntity caught, ServerPlayerEntity catcher) {
        ServerWorld world = catcher.getServerWorld();
        double x = (caught.getX() + catcher.getX()) / 2.0;
        double y = catcher.getY() + 1.5;
        double z = (caught.getZ() + catcher.getZ()) / 2.0;
        world.playSound(null, x, y, z, SoundEvents.field_14840, SoundCategory.field_15248, 1.2F, 0.7F);
        world.playSound(null, x, y, z, SoundEvents.field_14627, SoundCategory.field_15248, 1.0F, 1.2F);
        world.playSound(null, x, y, z, SoundEvents.field_15048, SoundCategory.field_15248, 1.0F, 0.8F);
        world.spawnParticles(ParticleTypes.field_11204, x, y, z, 12, 0.4, 0.3, 0.4, 0.02);
        world.spawnParticles(ParticleTypes.field_11205, x, y, z, 8, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticles(ParticleTypes.field_29642, x, y, z, 6, 0.2, 0.2, 0.2, 0.02);
        caught.setVelocity(0.0, 0.0, 0.0);
        caught.velocityModified = true;
        FallCatchHandler.CatchAnimPayload payload = new FallCatchHandler.CatchAnimPayload(catcher.getUuid(), caught.getUuid());

        for (ServerPlayerEntity nearby : PlayerLookup.tracking(catcher)) {
            ServerPlayNetworking.send(nearby, payload);
        }

        ServerPlayNetworking.send(catcher, payload);
        ServerPlayNetworking.send(caught, payload);
        PoseNetworking.poseStates.put(catcher.getUuid(), PoseState.NONE);
        PoseNetworking.broadcastPoseChange(catcher.getServer(), catcher.getUuid(), PoseState.NONE);
        caught.sendMessage(Text.literal("§a§l✓ " + catcher.getName().getString() + " caught you!"), true);
        catcher.sendMessage(Text.literal("§a§l✓ PERFECT CATCH! " + caught.getName().getString()), true);
    }

    public static void cleanup(UUID playerId) {
        catchReadyTime.remove(playerId);
        catchCooldowns.remove(playerId);
        successfulCatch.remove(playerId);
    }

    public record CatchAnimPayload(UUID catcherId, UUID caughtId) implements CustomPayload {
        public static final Id<FallCatchHandler.CatchAnimPayload> ID = new Id(FallCatchHandler.CATCH_ANIM_ID);
        public static final PacketCodec<PacketByteBuf, FallCatchHandler.CatchAnimPayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeUuid(payload.catcherId);
            buf.writeUuid(payload.caughtId);
        }, buf -> new FallCatchHandler.CatchAnimPayload(buf.readUuid(), buf.readUuid()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
