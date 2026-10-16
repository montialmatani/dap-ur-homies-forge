package com.cooptest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDamage;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.item.ElytraItem;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.registry.tag.BlockTags;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import net.minecraft.world.World.ExplosionSourceType;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class GrabMechanic {
    public static final HashMap<UUID, UUID> holding = new HashMap<>();
    public static final HashMap<UUID, UUID> heldBy = new HashMap<>();
    private static MinecraftServer cachedServer = null;
    public static final HashMap<UUID, Boolean> shieldMode = new HashMap<>();
    public static final HashMap<UUID, Long> shieldSwapCooldown = new HashMap<>();
    public static final HashMap<UUID, ArmorStandEntity> shieldArmorStands = new HashMap<>();
    private static final long SHIELD_SWAP_COOLDOWN_MS = 1000L;
    private static final HashMap<UUID, GrabMechanic.PendingThrow> pendingThrows = new HashMap<>();
    private static final HashMap<UUID, GrabMechanic.ThrownPlayerData> thrownPlayers = new HashMap<>();
    public static final HashMap<UUID, Boolean> elytraBoostRequests = new HashMap<>();
    public static final HashMap<UUID, float[]> airMovementInput = new HashMap<>();
    private static final double AIR_CONTROL_STRENGTH = 0.025;

    public static boolean tryGrab(ServerPlayerEntity holder, ServerPlayerEntity held) {
        if (holder == held) {
            return false;
        } else if (holder.distanceTo(held) > 3.0F) {
            return false;
        } else if (holding.containsKey(holder.getUuid())) {
            return false;
        } else if (heldBy.containsKey(held.getUuid())) {
            return false;
        } else if (PushInteractionHandler.hasPushImmunity(held.getUuid())) {
            return false;
        } else {
            PoseState holderPose = PoseNetworking.poseStates.getOrDefault(holder.getUuid(), PoseState.NONE);
            if (holderPose != PoseState.GRAB_READY) {
                return false;
            } else if (holder.hasVehicle() && holder.getVehicle() == held) {
                return false;
            } else if (held.hasVehicle() && held.getVehicle() == holder) {
                return false;
            } else {
                boolean success = held.startRiding(holder, true);
                if (!success) {
                    return false;
                } else {
                    holding.put(holder.getUuid(), held.getUuid());
                    heldBy.put(held.getUuid(), holder.getUuid());
                    PoseNetworking.poseStates.put(holder.getUuid(), PoseState.GRAB_HOLDING);
                    PoseNetworking.poseStates.put(held.getUuid(), PoseState.GRABBED);
                    if (holder.getServer() != null) {
                        PoseNetworking.broadcastPoseChange(holder.getServer(), holder.getUuid(), PoseState.GRAB_HOLDING);
                        PoseNetworking.broadcastPoseChange(holder.getServer(), held.getUuid(), PoseState.GRABBED);
                        GrabNetworking.broadcastGrabState(holder.getServer(), holder.getUuid(), held.getUuid(), true);
                        EntityPassengersSetS2CPacket packet = new EntityPassengersSetS2CPacket(holder);

                        for (ServerPlayerEntity p : holder.getServer().getPlayerManager().getPlayerList()) {
                            p.networkHandler.sendPacket(packet);
                        }
                    }

                    holder.getServerWorld()
                        .playSound(
                            null,
                            holder.getX(),
                            holder.getY(),
                            holder.getZ(),
                            SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
                            SoundCategory.PLAYERS,
                            1.0F,
                            1.0F
                        );
                    return true;
                }
            }
        }
    }

    public static boolean tryThrow(ServerPlayerEntity holder, float power) {
        UUID heldId = holding.get(holder.getUuid());
        if (heldId == null) {
            return false;
        } else if (isInShieldMode(holder.getUuid())) {
            holder.sendMessage(Text.literal("§cSwitch to throw mode first! (Press V)"), true);
            return false;
        } else {
            ServerPlayerEntity held = holder.getServer().getPlayerManager().getPlayer(heldId);
            if (held == null) {
                cleanupGrab(holder.getUuid());
                return false;
            } else {
                if (!holder.isCreative()) {
                    if (holder.getHungerManager().getFoodLevel() < 6) {
                        holder.sendMessage(Text.literal("§cToo hungry to throw!"), true);
                        return false;
                    }

                    holder.getHungerManager().addExhaustion(2.0F);
                }

                Vec3d lookDir = holder.getRotationVec(1.0F);
                float scaledPower = 0.5F + 2.0F * power;
                double horizX = lookDir.x * scaledPower * 1.6;
                double horizZ = lookDir.z * scaledPower * 1.6;
                double verticalBase = 0.4 + lookDir.y * 0.6;
                double maxVertical = 1.8;
                double verticalVel = Math.min(verticalBase + power * 0.6, maxVertical);
                if (lookDir.y < 0.0) {
                    verticalVel = Math.max(0.3, verticalVel);
                }

                Vec3d throwVelocity = new Vec3d(horizX, verticalVel, horizZ);
                Vec3d releasePos = holder.getPos().add(lookDir.multiply(1.5).multiply(1.0, 0.0, 1.0)).add(0.0, 0.5, 0.0);
                held.stopRiding();
                holding.remove(holder.getUuid());
                heldBy.remove(held.getUuid());
                PoseNetworking.poseStates.put(holder.getUuid(), PoseState.NONE);
                if (holder.getServer() != null) {
                    PoseNetworking.broadcastPoseChange(holder.getServer(), holder.getUuid(), PoseState.NONE);
                    PoseNetworking.broadcastAnimState(holder, 0);
                    GrabNetworking.broadcastGrabState(holder.getServer(), holder.getUuid(), held.getUuid(), false);
                    EntityPassengersSetS2CPacket packet = new EntityPassengersSetS2CPacket(holder);

                    for (ServerPlayerEntity p : holder.getServer().getPlayerManager().getPlayerList()) {
                        p.networkHandler.sendPacket(packet);
                    }
                }

                float throwYaw = holder.getYaw();
                float throwPitch = holder.getPitch();
                held.refreshPositionAndAngles(releasePos.x, releasePos.y, releasePos.z, throwYaw, throwPitch);
                held.setHeadYaw(throwYaw);
                held.networkHandler.requestTeleport(releasePos.x, releasePos.y, releasePos.z, throwYaw, throwPitch);
                pendingThrows.put(held.getUuid(), new GrabMechanic.PendingThrow(holder, held, throwVelocity, 3));
                holder.getServerWorld()
                    .playSound(
                        null,
                        holder.getX(),
                        holder.getY(),
                        holder.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                        SoundCategory.PLAYERS,
                        1.0F,
                        0.8F + power * 0.4F
                    );
                spawnThrowParticles(holder, held);
                return true;
            }
        }
    }

    private static void spawnThrowParticles(ServerPlayerEntity holder, ServerPlayerEntity held) {
        ServerWorld world = holder.getServerWorld();
        Vec3d pos = holder.getPos();

        for (int i = 0; i < 10; i++) {
            double offsetX = (world.random.nextDouble() - 0.5) * 0.5;
            double offsetY = world.random.nextDouble() * 0.5 + 0.5;
            double offsetZ = (world.random.nextDouble() - 0.5) * 0.5;
            world.spawnParticles(ParticleTypes.CLOUD, pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ, 1, 0.0, 0.0, 0.0, 0.05);
        }
    }

    public static void tick(MinecraftServer server) {
        cachedServer = server;
        tickShieldMode(server);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerId = player.getUuid();
            PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
            if (pose == PoseState.GRABBED && !player.hasVehicle() && !thrownPlayers.containsKey(playerId) && !pendingThrows.containsKey(playerId)) {
                heldBy.remove(playerId);
                PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                PoseNetworking.broadcastPoseChange(server, playerId, PoseState.NONE);
                PoseNetworking.broadcastAnimState(player, 0);
            }
        }

        for (ServerPlayerEntity playerx : server.getPlayerManager().getPlayerList()) {
            if (playerx.hasVehicle() && playerx.getVehicle() instanceof ServerPlayerEntity carrier) {
                PoseState pose = PoseNetworking.poseStates.get(playerx.getUuid());
                if (pose == PoseState.GRABBED) {
                    float carrierYaw = carrier.getYaw();
                    playerx.setYaw(carrierYaw);
                    playerx.setBodyYaw(carrierYaw);
                    playerx.setHeadYaw(carrierYaw);
                }
            }
        }

        Iterator<Entry<UUID, GrabMechanic.PendingThrow>> throwIterator = pendingThrows.entrySet().iterator();

        while (throwIterator.hasNext()) {
            Entry<UUID, GrabMechanic.PendingThrow> entry = throwIterator.next();
            GrabMechanic.PendingThrow pending = entry.getValue();
            pending.ticksRemaining--;
            if (pending.ticksRemaining <= 0) {
                ServerPlayerEntity held = pending.held;
                if (held != null && held.isAlive()) {
                    held.setVelocity(pending.velocity);
                    held.velocityModified = true;
                    held.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(held));
                    boolean wasOnFire = held.isOnFire();
                    thrownPlayers.put(held.getUuid(), new GrabMechanic.ThrownPlayerData(held.getY(), wasOnFire, pending.velocity));
                }

                throwIterator.remove();
            }
        }

        Set<UUID> toRemove = new HashSet<>();

        for (Entry<UUID, GrabMechanic.ThrownPlayerData> entry : new HashMap<>(thrownPlayers).entrySet()) {
            UUID playerId = entry.getKey();
            GrabMechanic.ThrownPlayerData data = entry.getValue();
            ServerPlayerEntity playerxx = server.getPlayerManager().getPlayer(playerId);
            if (playerxx == null) {
                toRemove.add(playerId);
            } else {
                data.ticksFlying++;
                Vec3d vel = playerxx.getVelocity();
                if (vel.horizontalLengthSquared() > 0.01) {
                    float velocityYaw = (float)Math.toDegrees(Math.atan2(-vel.x, vel.z));
                    playerxx.setYaw(velocityYaw);
                    playerxx.setBodyYaw(velocityYaw);
                    playerxx.setHeadYaw(velocityYaw);
                }

                float[] moveInput = airMovementInput.get(playerId);
                if (moveInput != null && (Math.abs(moveInput[0]) > 0.01F || Math.abs(moveInput[1]) > 0.01F)) {
                    float yawRad = (float)Math.toRadians(playerxx.getYaw());
                    float forward = moveInput[0];
                    float strafe = moveInput[1];
                    double driftX = (-strafe * Math.cos(yawRad) - forward * Math.sin(yawRad)) * 0.025;
                    double driftZ = (-strafe * Math.sin(yawRad) + forward * Math.cos(yawRad)) * 0.025;
                    Vec3d currentVel = playerxx.getVelocity();
                    playerxx.setVelocity(currentVel.add(driftX, 0.0, driftZ));
                    playerxx.velocityModified = true;
                }

                long timeSinceThrow = System.currentTimeMillis() - data.throwTimeMs;
                if (!data.elytraBoostUsed
                    && timeSinceThrow < 2000L
                    && elytraBoostRequests.remove(playerId) != null
                    && playerxx.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof ElytraItem) {
                    Vec3d look = playerxx.getRotationVec(1.0F);
                    double boostStrength = 1.5;
                    playerxx.setVelocity(
                        playerxx.getVelocity()
                            .add(look.x * boostStrength, look.y * boostStrength + 0.5, look.z * boostStrength)
                    );
                    playerxx.velocityModified = true;
                    playerxx.startFallFlying();
                    playerxx.getServerWorld()
                        .playSound(
                            null,
                            playerxx.getX(),
                            playerxx.getY(),
                            playerxx.getZ(),
                            SoundEvents.ITEM_FIRECHARGE_USE,
                            SoundCategory.PLAYERS,
                            1.0F,
                            1.2F
                        );
                    playerxx.getServerWorld()
                        .spawnParticles(ParticleTypes.FIREWORK, playerxx.getX(), playerxx.getY(), playerxx.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
                    data.elytraBoostUsed = true;
                    PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                    PoseNetworking.broadcastPoseChange(server, playerId, PoseState.NONE);
                    airMovementInput.remove(playerId);
                    toRemove.add(playerId);
                } else {
                    if (data.lastPos != null) {
                        checkWallCollision(playerxx, data);
                    }

                    data.lastPos = playerxx.getPos();
                    if (data.ticksFlying % 2 == 0) {
                        spawnTrailParticles(playerxx);
                        if (data.wasOnFire && playerxx.isOnFire()) {
                            spawnFireTrail(playerxx, data);
                        }
                    }

                    checkForNearbyCreepers(playerxx);
                    if (data.ticksFlying >= 10) {
                        boolean onGround = playerxx.isOnGround();
                        boolean inWater = playerxx.isTouchingWater();
                        boolean closeToGround = isCloseToGround(playerxx);
                        boolean isFalling = playerxx.getVelocity().y < -0.1;
                        if (onGround || inWater || closeToGround && isFalling) {
                            PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                            PoseNetworking.broadcastPoseChange(server, playerId, PoseState.NONE);
                            ServerPlayerEntity landedPlayer = server.getPlayerManager().getPlayer(playerId);
                            if (landedPlayer != null && !SpinHandler.isSpinning(playerId)) {
                                PoseNetworking.broadcastAnimState(landedPlayer, 0);
                            }

                            airMovementInput.remove(playerId);
                            if (data.wasOnFire) {
                                createFireExplosion(playerxx);
                            }

                            spawnLandingParticles(playerxx);
                            toRemove.add(playerId);
                        }
                    }

                    int maxTicks = SpinHandler.isSpinning(playerId) ? 1200 : 100;
                    if (data.ticksFlying > maxTicks) {
                        PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                        PoseNetworking.broadcastPoseChange(server, playerId, PoseState.NONE);
                        if (!SpinHandler.isSpinning(playerId)) {
                            ServerPlayerEntity timedOutPlayer = server.getPlayerManager().getPlayer(playerId);
                            if (timedOutPlayer != null) {
                                PoseNetworking.broadcastAnimState(timedOutPlayer, 0);
                            }
                        }

                        airMovementInput.remove(playerId);
                        toRemove.add(playerId);
                    }
                }
            }
        }

        thrownPlayers.keySet().removeAll(toRemove);
    }

    private static void checkWallCollision(ServerPlayerEntity player, GrabMechanic.ThrownPlayerData data) {
        ServerWorld world = player.getServerWorld();
        Vec3d currentPos = player.getPos();
        Vec3d velocity = player.getVelocity();
        double speed = velocity.horizontalLength();
        if (!(speed < 0.3)) {
            Vec3d direction = velocity.normalize();

            for (double dist = 0.3; dist <= 1.5; dist += 0.3) {
                Vec3d checkPos = currentPos.add(direction.multiply(dist));

                for (double yOff = 0.0; yOff <= 1.8; yOff += 0.9) {
                    BlockPos blockPos = new BlockPos(
                        (int)Math.floor(checkPos.x), (int)Math.floor(checkPos.y + yOff), (int)Math.floor(checkPos.z)
                    );
                    BlockState blockState = world.getBlockState(blockPos);
                    if (!blockState.isAir() && canBreakBlock(blockState, world, blockPos)) {
                        float hardness = blockState.getHardness(world, blockPos);
                        float damage = calculateWallDamage(hardness, speed);
                        world.breakBlock(blockPos, true, player);
                        world.playSound(null, blockPos, blockState.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0F, 1.0F);
                        if (damage > 0.0F) {
                            player.damage(world.getDamageSources().flyIntoWall(), damage);
                        }

                        player.setVelocity(velocity.multiply(0.7));
                        player.velocityModified = true;
                        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
                        world.spawnParticles(
                            ParticleTypes.CRIT,
                            blockPos.getX() + 0.5,
                            blockPos.getY() + 0.5,
                            blockPos.getZ() + 0.5,
                            10,
                            0.3,
                            0.3,
                            0.3,
                            0.1
                        );
                    }
                }
            }
        }
    }

    private static boolean canBreakBlock(BlockState state, ServerWorld world, BlockPos pos) {
        if (state.isOf(Blocks.BEDROCK)) {
            return false;
        } else if (state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.CRYING_OBSIDIAN)) {
            return false;
        } else if (state.isOf(Blocks.REINFORCED_DEEPSLATE)) {
            return false;
        } else if (state.isOf(Blocks.END_PORTAL_FRAME)) {
            return false;
        } else if (state.isOf(Blocks.BARRIER)) {
            return false;
        } else if (state.isOf(Blocks.COMMAND_BLOCK) || state.isOf(Blocks.CHAIN_COMMAND_BLOCK) || state.isOf(Blocks.REPEATING_COMMAND_BLOCK)) {
            return false;
        } else if (state.isOf(Blocks.STRUCTURE_BLOCK) || state.isOf(Blocks.JIGSAW)) {
            return false;
        } else if (state.isOf(Blocks.CHEST)
            || state.isOf(Blocks.TRAPPED_CHEST)
            || state.isOf(Blocks.ENDER_CHEST)
            || state.isOf(Blocks.BARREL)
            || state.isOf(Blocks.SHULKER_BOX)
            || state.isOf(Blocks.FURNACE)
            || state.isOf(Blocks.BLAST_FURNACE)
            || state.isOf(Blocks.SMOKER)
            || state.isOf(Blocks.BREWING_STAND)
            || state.isOf(Blocks.ENCHANTING_TABLE)
            || state.isOf(Blocks.ANVIL)
            || state.isOf(Blocks.CHIPPED_ANVIL)
            || state.isOf(Blocks.DAMAGED_ANVIL)
            || state.isOf(Blocks.CRAFTING_TABLE)
            || state.isOf(Blocks.CARTOGRAPHY_TABLE)
            || state.isOf(Blocks.FLETCHING_TABLE)
            || state.isOf(Blocks.GRINDSTONE)
            || state.isOf(Blocks.LOOM)
            || state.isOf(Blocks.SMITHING_TABLE)
            || state.isOf(Blocks.STONECUTTER)
            || state.isOf(Blocks.LECTERN)
            || state.isOf(Blocks.BEACON)
            || state.isOf(Blocks.RESPAWN_ANCHOR)
            || state.isOf(Blocks.LODESTONE)) {
            return false;
        } else if (state.isIn(BlockTags.DOORS) || state.isIn(BlockTags.TRAPDOORS) || state.isIn(BlockTags.FENCE_GATES)) {
            return false;
        } else if (state.isIn(BlockTags.SIGNS) || state.isIn(BlockTags.ALL_HANGING_SIGNS)) {
            return false;
        } else if (state.isIn(BlockTags.BEDS)) {
            return false;
        } else if (!state.isIn(BlockTags.BUTTONS) && !state.isOf(Blocks.LEVER)) {
            float hardness = state.getHardness(world, pos);
            return hardness < 0.0F ? false : !(hardness > 25.0F);
        } else {
            return false;
        }
    }

    private static float calculateWallDamage(float hardness, double speed) {
        float baseDamage = hardness * 0.8F;
        float speedMultiplier = (float)Math.min(2.0, speed);
        return Math.max(1.0F, Math.min(10.0F, baseDamage * speedMultiplier));
    }

    private static void spawnTrailParticles(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d pos = player.getPos();
        world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.5, pos.z, 1, 0.1, 0.1, 0.1, 0.02);
    }

    private static void spawnLandingParticles(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d pos = player.getPos();
        BlockPos groundPos = player.getBlockPos().down();
        BlockState groundBlock = world.getBlockState(groundPos);
        world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.5, pos.z, 1, 0.0, 0.0, 0.0, 0.0);

        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2) * i / 24.0;
            double offsetX = Math.cos(angle) * 0.8;
            double offsetZ = Math.sin(angle) * 0.8;
            double velX = Math.cos(angle) * 0.3;
            double velZ = Math.sin(angle) * 0.3;
            world.spawnParticles(ParticleTypes.CLOUD, pos.x + offsetX, pos.y + 0.2, pos.z + offsetZ, 1, velX, 0.1, velZ, 0.05);
            world.spawnParticles(
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                pos.x + offsetX * 0.5,
                pos.y + 0.1,
                pos.z + offsetZ * 0.5,
                1,
                velX * 0.5,
                0.2,
                velZ * 0.5,
                0.02
            );
        }

        if (!groundBlock.isAir()) {
            BlockStateParticleEffect blockParticle = new BlockStateParticleEffect(ParticleTypes.BLOCK, groundBlock);

            for (int i = 0; i < 30; i++) {
                double offsetX = (world.random.nextDouble() - 0.5) * 1.5;
                double offsetZ = (world.random.nextDouble() - 0.5) * 1.5;
                double velY = world.random.nextDouble() * 0.5 + 0.2;
                world.spawnParticles(blockParticle, pos.x + offsetX, pos.y + 0.1, pos.z + offsetZ, 1, 0.0, velY, 0.0, 0.15);
            }
        }

        world.spawnParticles(ParticleTypes.POOF, pos.x, pos.y + 0.3, pos.z, 15, 0.5, 0.3, 0.5, 0.05);
        world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y + 0.5, pos.z, 10, 0.5, 0.5, 0.5, 0.3);
        world.playSound(
            null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5F, 1.2F
        );
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.3F, 0.8F);
    }

    private static boolean isCloseToGround(ServerPlayerEntity player) {
        if (player.isOnGround()) {
            return true;
        } else if (player.getVelocity().y >= 0.0) {
            return false;
        } else {
            double maxDistance = 1.0;
            double startY = player.getY();

            for (double checkY = startY; checkY > startY - maxDistance; checkY -= 0.5) {
                BlockPos blockPos = player.getBlockPos().withY((int)checkY - 1);
                BlockState blockState = player.getServerWorld().getBlockState(blockPos);
                if (!blockState.isAir() && blockState.isSolidBlock(player.getServerWorld(), blockPos)) {
                    return true;
                }
            }

            return false;
        }
    }

    public static boolean tryDrop(ServerPlayerEntity holder) {
        UUID heldId = holding.get(holder.getUuid());
        if (heldId == null) {
            return false;
        } else {
            ServerPlayerEntity held = holder.getServer().getPlayerManager().getPlayer(heldId);
            if (held == null) {
                cleanupGrab(holder.getUuid());
                return false;
            } else {
                if (isInShieldMode(holder.getUuid())) {
                    holder.removeStatusEffect(StatusEffects.SLOWNESS);
                }

                held.stopRiding();
                cleanupGrab(holder.getUuid());
                PoseNetworking.poseStates.put(holder.getUuid(), PoseState.NONE);
                PoseNetworking.poseStates.put(held.getUuid(), PoseState.NONE);
                if (holder.getServer() != null) {
                    PoseNetworking.broadcastPoseChange(holder.getServer(), holder.getUuid(), PoseState.NONE);
                    PoseNetworking.broadcastPoseChange(holder.getServer(), held.getUuid(), PoseState.NONE);
                    PoseNetworking.broadcastAnimState(holder, 0);
                    PoseNetworking.broadcastAnimState(held, 0);
                    GrabNetworking.broadcastGrabState(holder.getServer(), holder.getUuid(), held.getUuid(), false);
                    EntityPassengersSetS2CPacket packet = new EntityPassengersSetS2CPacket(holder);

                    for (ServerPlayerEntity p : holder.getServer().getPlayerManager().getPlayerList()) {
                        p.networkHandler.sendPacket(packet);
                    }
                }

                holder.getServerWorld()
                    .playSound(
                        null, held.getX(), held.getY(), held.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5F, 0.8F
                    );
                return true;
            }
        }
    }

    public static boolean tryEscape(ServerPlayerEntity held) {
        UUID holderId = heldBy.get(held.getUuid());
        if (holderId == null) {
            return false;
        } else {
            ServerPlayerEntity holder = held.getServer().getPlayerManager().getPlayer(holderId);
            if (holder != null && isInShieldMode(holderId)) {
                holder.removeStatusEffect(StatusEffects.SLOWNESS);
            }

            held.stopRiding();
            cleanupGrab(holderId);
            PoseNetworking.poseStates.put(held.getUuid(), PoseState.NONE);
            if (holder != null) {
                PoseNetworking.poseStates.put(holder.getUuid(), PoseState.NONE);
                PoseNetworking.broadcastPoseChange(held.getServer(), holder.getUuid(), PoseState.NONE);
                PoseNetworking.broadcastAnimState(holder, 0);
            }

            if (held.getServer() != null) {
                PoseNetworking.broadcastPoseChange(held.getServer(), held.getUuid(), PoseState.NONE);
                PoseNetworking.broadcastAnimState(held, 0);
                GrabNetworking.broadcastGrabState(held.getServer(), holderId, held.getUuid(), false);
                if (holder != null) {
                    EntityPassengersSetS2CPacket packet = new EntityPassengersSetS2CPacket(holder);

                    for (ServerPlayerEntity p : held.getServer().getPlayerManager().getPlayerList()) {
                        p.networkHandler.sendPacket(packet);
                    }
                }

                Vec3d escapePos = held.getPos().add(0.0, 0.1, 0.0);
                held.requestTeleport(escapePos.x, escapePos.y, escapePos.z);
            }

            held.getServerWorld()
                .playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.8F, 1.2F);
            return true;
        }
    }

    public static void cleanupGrab(UUID holderUuid) {
        UUID heldUuid = holding.remove(holderUuid);
        if (heldUuid != null) {
            heldBy.remove(heldUuid);
        }

        shieldMode.remove(holderUuid);
        shieldSwapCooldown.remove(holderUuid);
        ArmorStandEntity armorStand = shieldArmorStands.remove(holderUuid);
        if (armorStand != null && !armorStand.isRemoved()) {
            armorStand.discard();
        }
    }

    public static boolean isHolding(ServerPlayerEntity player) {
        return holding.containsKey(player.getUuid());
    }

    public static boolean isBeingHeld(ServerPlayerEntity player) {
        return heldBy.containsKey(player.getUuid());
    }

    public static void forceRelease(UUID playerUuid) {
        UUID heldUuid = holding.remove(playerUuid);
        if (heldUuid != null) {
            heldBy.remove(heldUuid);
        }

        UUID holderUuid = heldBy.remove(playerUuid);
        if (holderUuid != null) {
            holding.remove(holderUuid);
        }

        PoseNetworking.poseStates.remove(playerUuid);
        pendingThrows.remove(playerUuid);
        thrownPlayers.remove(playerUuid);
    }

    private static void spawnFireTrail(ServerPlayerEntity player, GrabMechanic.ThrownPlayerData data) {
        ServerWorld world = player.getServerWorld();
        Vec3d pos = player.getPos();
        world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.5, pos.z, 3, 0.2, 0.2, 0.2, 0.02);
        world.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.3, pos.z, 2, 0.1, 0.1, 0.1, 0.01);
        if (data.ticksFlying % 4 == 0) {
            BlockPos groundPos = player.getBlockPos().down();

            for (int i = 0; i < 5; i++) {
                BlockState belowState = world.getBlockState(groundPos);
                if (!belowState.isAir()) {
                    BlockPos firePos = groundPos.up();
                    BlockState fireSpot = world.getBlockState(firePos);
                    if (fireSpot.isAir()) {
                        world.setBlockState(firePos, Blocks.FIRE.getDefaultState());
                    }
                    break;
                }

                groundPos = groundPos.down();
            }
        }
    }

    private static void checkForNearbyCreepers(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        double checkRadius = 4.0;

        for (CreeperEntity creeper : world.getEntitiesByClass(
            CreeperEntity.class, player.getBoundingBox().expand(checkRadius), creeperx -> creeperx.isAlive() && player.distanceTo(creeperx) <= checkRadius
        )) {
            world.createExplosion(creeper, creeper.getX(), creeper.getY(), creeper.getZ(), 3.0F, ExplosionSourceType.MOB);
            creeper.discard();
            world.playSound(
                null, creeper.getX(), creeper.getY(), creeper.getZ(), SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 1.0F, 1.0F
            );
        }

        for (GhastEntity ghast : world.getEntitiesByClass(
            GhastEntity.class, player.getBoundingBox().expand(checkRadius), ghastx -> ghastx.isAlive() && player.distanceTo(ghastx) <= checkRadius
        )) {
            Vec3d ghastPos = ghast.getPos();
            ghast.damage(world.getDamageSources().playerAttack(player), 1000.0F);
            world.playSound(
                null, ghastPos.x, ghastPos.y, ghastPos.z, ModSounds.EXPLOSION_IMPACT, SoundCategory.PLAYERS, 2.0F, 1.0F
            );
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, ghastPos.x, ghastPos.y, ghastPos.z, 3, 0.5, 0.5, 0.5, 0.0);
            world.spawnParticles(ParticleTypes.CLOUD, ghastPos.x, ghastPos.y, ghastPos.z, 30, 1.5, 1.5, 1.5, 0.1);
            world.spawnParticles(ParticleTypes.FLAME, ghastPos.x, ghastPos.y, ghastPos.z, 20, 1.0, 1.0, 1.0, 0.2);
            player.sendMessage(Text.literal("§6§l\ud83d\udca5 GHAST OBLITERATED! \ud83d\udca5"), true);
        }
    }

    private static void createFireExplosion(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d pos = player.getPos();
        world.createExplosion(player, pos.x, pos.y, pos.z, 2.0F, true, ExplosionSourceType.MOB);
        world.playSound(null, pos.x, pos.y, pos.z, ModSounds.EXPLOSION_IMPACT, SoundCategory.PLAYERS, 1.5F, 1.0F);
        player.damage(world.getDamageSources().onFire(), 16.0F);

        for (int i = 0; i < 20; i++) {
            double offsetX = (world.random.nextDouble() - 0.5) * 3.0;
            double offsetY = world.random.nextDouble() * 2.0;
            double offsetZ = (world.random.nextDouble() - 0.5) * 3.0;
            world.spawnParticles(ParticleTypes.FLAME, pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ, 1, 0.0, 0.0, 0.0, 0.1);
        }
    }

    public static void setAirMovementInput(UUID playerId, float forward, float strafe) {
        if (thrownPlayers.containsKey(playerId)) {
            airMovementInput.put(playerId, new float[]{forward, strafe});
        }
    }

    public static boolean isPlayerThrown(UUID playerId) {
        return thrownPlayers.containsKey(playerId);
    }

    public static void requestElytraBoost(UUID playerId) {
        if (thrownPlayers.containsKey(playerId)) {
            elytraBoostRequests.put(playerId, true);
        }
    }

    public static void fullCleanup(UUID playerId) {
        thrownPlayers.remove(playerId);
        pendingThrows.remove(playerId);
        elytraBoostRequests.remove(playerId);
        airMovementInput.remove(playerId);
        shieldMode.remove(playerId);
        shieldSwapCooldown.remove(playerId);
        ArmorStandEntity armorStand = shieldArmorStands.remove(playerId);
        if (armorStand != null && !armorStand.isRemoved()) {
            armorStand.discard();
        }

        if (holding.containsKey(playerId)) {
            UUID heldId = holding.get(playerId);
            heldBy.remove(heldId);
            holding.remove(playerId);
            if (cachedServer != null) {
                ServerPlayerEntity heldPlayer = cachedServer.getPlayerManager().getPlayer(heldId);
                if (heldPlayer != null) {
                    heldPlayer.stopRiding();
                    PoseNetworking.poseStates.put(heldId, PoseState.NONE);
                    PoseNetworking.broadcastPoseChange(cachedServer, heldId, PoseState.NONE);
                    PoseNetworking.broadcastAnimState(heldPlayer, 0);
                    EntityPassengersSetS2CPacket passPacket = new EntityPassengersSetS2CPacket(heldPlayer);

                    for (ServerPlayerEntity p : cachedServer.getPlayerManager().getPlayerList()) {
                        try {
                            p.networkHandler.sendPacket(passPacket);
                        } catch (Exception var10) {
                        }
                    }
                }

                try {
                    GrabNetworking.broadcastGrabState(cachedServer, playerId, heldId, false);
                } catch (Exception var9) {
                }
            }

            ArmorStandEntity holderArmorStand = shieldArmorStands.remove(playerId);
            if (holderArmorStand != null && !holderArmorStand.isRemoved()) {
                holderArmorStand.discard();
            }
        }

        if (heldBy.containsKey(playerId)) {
            UUID holderId = heldBy.get(playerId);
            holding.remove(holderId);
            heldBy.remove(playerId);
            shieldMode.remove(holderId);
            if (cachedServer != null) {
                PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                PoseNetworking.broadcastPoseChange(cachedServer, playerId, PoseState.NONE);

                try {
                    GrabNetworking.broadcastGrabState(cachedServer, holderId, playerId, false);
                } catch (Exception var8) {
                }

                ServerPlayerEntity holder = cachedServer.getPlayerManager().getPlayer(holderId);
                if (holder != null) {
                    PoseNetworking.poseStates.put(holderId, PoseState.NONE);
                    PoseNetworking.broadcastPoseChange(cachedServer, holderId, PoseState.NONE);
                    PoseNetworking.broadcastAnimState(holder, 0);
                }
            }

            ArmorStandEntity holderArmorStand = shieldArmorStands.remove(holderId);
            if (holderArmorStand != null && !holderArmorStand.isRemoved()) {
                holderArmorStand.discard();
            }
        }
    }

    public static boolean toggleShieldMode(ServerPlayerEntity holder) {
        UUID holderId = holder.getUuid();
        if (!holding.containsKey(holderId)) {
            return false;
        } else {
            Long cooldownEnd = shieldSwapCooldown.get(holderId);
            if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
                long remaining = (cooldownEnd - System.currentTimeMillis()) / 100L;
                holder.sendMessage(Text.literal("§cSwap cooldown! " + remaining / 10.0 + "s"), true);
                return false;
            } else {
                UUID heldId = holding.get(holderId);
                ServerPlayerEntity held = holder.getServer().getPlayerManager().getPlayer(heldId);
                if (held == null) {
                    return false;
                } else {
                    boolean currentMode = shieldMode.getOrDefault(holderId, false);
                    boolean newMode = !currentMode;
                    shieldMode.put(holderId, newMode);
                    shieldSwapCooldown.put(holderId, System.currentTimeMillis() + 1000L);
                    if (newMode) {
                        holder.sendMessage(Text.literal("§b\ud83d\udee1 HUMAN SHIELD MODE"), true);
                        held.sendMessage(Text.literal("§c⚠ You are now a SHIELD!"), true);
                        held.stopRiding();
                        ServerWorld world = holder.getServerWorld();
                        double yaw = Math.toRadians(holder.getYaw());
                        double forwardX = -Math.sin(yaw) * 0.8;
                        double forwardZ = Math.cos(yaw) * 0.8;
                        ArmorStandEntity armorStand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
                        armorStand.setPosition(holder.getX() + forwardX, holder.getY() - 0.5, holder.getZ() + forwardZ);
                        armorStand.setYaw(holder.getYaw());
                        armorStand.setInvisible(true);
                        armorStand.setNoGravity(true);
                        armorStand.setInvulnerable(true);
                        armorStand.setSilent(true);
                        world.spawnEntity(armorStand);
                        shieldArmorStands.put(holderId, armorStand);
                        held.startRiding(armorStand, true);
                        world.playSound(
                            null,
                            holder.getX(),
                            holder.getY(),
                            holder.getZ(),
                            SoundEvents.ITEM_SHIELD_BLOCK,
                            SoundCategory.PLAYERS,
                            1.0F,
                            1.2F
                        );
                        holder.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 999999, 1, false, false, true));
                        PoseNetworking.poseStates.put(heldId, PoseState.NONE);
                        PoseNetworking.broadcastPoseChange(holder.getServer(), heldId, PoseState.NONE);
                        PoseNetworking.poseStates.put(holderId, PoseState.GRAB_HOLDING);
                        PoseNetworking.broadcastAnimState(held, 29);
                        PoseNetworking.broadcastAnimState(holder, 28);
                        EntityPassengersSetS2CPacket holderPacket = new EntityPassengersSetS2CPacket(holder);

                        for (ServerPlayerEntity p : holder.getServer().getPlayerManager().getPlayerList()) {
                            p.networkHandler.sendPacket(holderPacket);
                        }

                        ArmorStandEntity as = shieldArmorStands.get(holderId);
                        if (as != null) {
                            EntityPassengersSetS2CPacket asPacket = new EntityPassengersSetS2CPacket(as);

                            for (ServerPlayerEntity p : holder.getServer().getPlayerManager().getPlayerList()) {
                                p.networkHandler.sendPacket(asPacket);
                            }
                        }

                        broadcastShieldMode(holder.getServer(), holderId, heldId, true);
                    } else {
                        holder.sendMessage(Text.literal("§e✋ THROW MODE"), true);
                        held.sendMessage(Text.literal("§eBack to throw mode"), true);
                        held.stopRiding();
                        ArmorStandEntity armorStand = shieldArmorStands.remove(holderId);
                        if (armorStand != null) {
                            armorStand.discard();
                        }

                        if (!holder.hasVehicle() || holder.getVehicle() != held) {
                            held.startRiding(holder, true);
                        }

                        PoseNetworking.poseStates.put(heldId, PoseState.GRABBED);
                        PoseNetworking.broadcastPoseChange(held.getServer(), heldId, PoseState.GRABBED);
                        PoseNetworking.broadcastAnimState(holder, 3);
                        holder.removeStatusEffect(StatusEffects.SLOWNESS);
                        EntityPassengersSetS2CPacket holderPacket = new EntityPassengersSetS2CPacket(holder);

                        for (ServerPlayerEntity p : holder.getServer().getPlayerManager().getPlayerList()) {
                            p.networkHandler.sendPacket(holderPacket);
                        }

                        holder.getServerWorld()
                            .playSound(
                                null,
                                holder.getX(),
                                holder.getY(),
                                holder.getZ(),
                                SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
                                SoundCategory.PLAYERS,
                                1.0F,
                                1.0F
                            );
                        broadcastShieldMode(holder.getServer(), holderId, heldId, false);
                    }

                    return true;
                }
            }
        }
    }

    public static void tickShieldMode(MinecraftServer server) {
        Iterator<Entry<UUID, ArmorStandEntity>> iterator = shieldArmorStands.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<UUID, ArmorStandEntity> entry = iterator.next();
            UUID holderId = entry.getKey();
            ArmorStandEntity armorStand = entry.getValue();
            if (!shieldMode.getOrDefault(holderId, false)) {
                armorStand.discard();
                iterator.remove();
            } else {
                ServerPlayerEntity holder = server.getPlayerManager().getPlayer(holderId);
                if (holder != null && !armorStand.isRemoved()) {
                    UUID heldId = holding.get(holderId);
                    if (heldId == null) {
                        armorStand.discard();
                        iterator.remove();
                        shieldMode.remove(holderId);
                        shieldSwapCooldown.remove(holderId);
                        holder.removeStatusEffect(StatusEffects.SLOWNESS);
                        PoseNetworking.poseStates.put(holderId, PoseState.NONE);
                        PoseNetworking.broadcastPoseChange(holder.getServer(), holderId, PoseState.NONE);
                        PoseNetworking.broadcastAnimState(holder, 0);
                        GrabNetworking.broadcastGrabState(server, holderId, heldId, false);
                        EntityPassengersSetS2CPacket packet = new EntityPassengersSetS2CPacket(holder);

                        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                            p.networkHandler.sendPacket(packet);
                        }

                        holder.sendMessage(Text.literal("§c Shield dropped!"), true);
                    } else {
                        ServerPlayerEntity heldPlayer = server.getPlayerManager().getPlayer(heldId);
                        if (heldPlayer != null && heldPlayer.isAlive()) {
                            double yaw = Math.toRadians(holder.getYaw());
                            double forwardX = -Math.sin(yaw) * 0.8;
                            double forwardZ = Math.cos(yaw) * 0.8;
                            double newX = holder.getX() + forwardX;
                            double newY = holder.getY() - 0.5;
                            double newZ = holder.getZ() + forwardZ;
                            armorStand.setPosition(newX, newY, newZ);
                            armorStand.setYaw(holder.getYaw());
                            if (armorStand.getFirstPassenger() instanceof ServerPlayerEntity shieldPlayer) {
                                float heldYaw = holder.getYaw();
                                shieldPlayer.setYaw(heldYaw);
                                shieldPlayer.setBodyYaw(heldYaw);
                                shieldPlayer.setHeadYaw(heldYaw);
                                PoseNetworking.AnimStateSyncPayload animPayload = new PoseNetworking.AnimStateSyncPayload(shieldPlayer.getUuid(), 29);
                                ServerPlayNetworking.send(holder, animPayload);
                                EntityPositionS2CPacket posPacket = new EntityPositionS2CPacket(shieldPlayer);
                                holder.networkHandler.sendPacket(posPacket);
                                EntityPositionS2CPacket standPacket = new EntityPositionS2CPacket(armorStand);
                                holder.networkHandler.sendPacket(standPacket);
                            }
                        } else {
                            armorStand.discard();
                            iterator.remove();
                            shieldMode.remove(holderId);
                            shieldSwapCooldown.remove(holderId);
                            holding.remove(holderId);
                            heldBy.remove(heldId);
                            holder.removeStatusEffect(StatusEffects.SLOWNESS);
                            PoseNetworking.poseStates.put(holderId, PoseState.NONE);
                            PoseNetworking.broadcastPoseChange(holder.getServer(), holderId, PoseState.NONE);
                            PoseNetworking.broadcastAnimState(holder, 0);
                            GrabNetworking.broadcastGrabState(server, holderId, heldId, false);
                            EntityPassengersSetS2CPacket packet = new EntityPassengersSetS2CPacket(holder);

                            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                                p.networkHandler.sendPacket(packet);
                            }

                            holder.sendMessage(Text.literal("§c Shield died!"), true);
                        }
                    }
                } else {
                    if (!armorStand.isRemoved()) {
                        armorStand.discard();
                    }

                    iterator.remove();
                    shieldMode.remove(holderId);
                }
            }
        }
    }

    public static boolean isInShieldMode(UUID holderId) {
        return shieldMode.getOrDefault(holderId, false);
    }

    public static ServerPlayerEntity getShieldPlayer(ServerPlayerEntity holder) {
        if (!isInShieldMode(holder.getUuid())) {
            return null;
        } else {
            UUID heldId = holding.get(holder.getUuid());
            return heldId == null ? null : holder.getServer().getPlayerManager().getPlayer(heldId);
        }
    }

    private static void broadcastShieldMode(MinecraftServer server, UUID holderId, UUID heldId, boolean enabled) {
        GrabMechanic.ShieldModePayload payload = new GrabMechanic.ShieldModePayload(holderId, heldId, enabled);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void registerShieldDamageEvent() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((AllowDamage)(entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity holder) {
                if (isInShieldMode(holder.getUuid())) {
                    ServerPlayerEntity shield = getShieldPlayer(holder);
                    if (shield != null && shield.isAlive()) {
                        shield.damage(source, amount);
                        return false;
                    }
                }

                return true;
            } else {
                return true;
            }
        });
    }

    private static class PendingThrow {
        ServerPlayerEntity holder;
        ServerPlayerEntity held;
        Vec3d velocity;
        int ticksRemaining;

        PendingThrow(ServerPlayerEntity holder, ServerPlayerEntity held, Vec3d velocity, int delay) {
            this.holder = holder;
            this.held = held;
            this.velocity = velocity;
            this.ticksRemaining = delay;
        }
    }

    public record ShieldModePayload(UUID holderId, UUID heldId, boolean enabled) implements CustomPayload {
        public static final Id<GrabMechanic.ShieldModePayload> ID = new Id(new Identifier("testcoop", "shield_mode"));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            PayloadTypeRegistry.playS2C().register(ID, PacketCodec.of((payload, buf) -> {
                buf.writeUuid(payload.holderId);
                buf.writeUuid(payload.heldId);
                buf.writeBoolean(payload.enabled);
            }, buf -> new GrabMechanic.ShieldModePayload(buf.readUuid(), buf.readUuid(), buf.readBoolean())));
        }
    }

    private static class ThrownPlayerData {
        double startY;
        int ticksFlying;
        boolean wasOnFire;
        Vec3d lastPos;
        Vec3d velocity;
        long throwTimeMs;
        boolean elytraBoostUsed;

        ThrownPlayerData(double startY, boolean wasOnFire, Vec3d velocity) {
            this.startY = startY;
            this.ticksFlying = 0;
            this.wasOnFire = wasOnFire;
            this.lastPos = null;
            this.velocity = velocity;
            this.throwTimeMs = System.currentTimeMillis();
            this.elytraBoostUsed = false;
        }
    }
}
