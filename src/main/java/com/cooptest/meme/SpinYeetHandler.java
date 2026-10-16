package com.cooptest.meme;

import com.cooptest.PoseNetworking;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import com.cooptest.fabricshim.mc.CustomPayload;
import net.minecraft.server.MinecraftServer;

public class SpinYeetHandler {
    private static final int ANIM_NONE = 0;
    private static final int ANIM_SPIN_YEET_GRABBER = 106;
    private static final int ANIM_SPIN_YEET_GRABBED = 107;
    private static final Map<UUID, SpinYeetSession> grabberToSession = new HashMap<>();
    private static final Map<UUID, UUID> grabbedToGrabber = new HashMap<>();
    private static final Map<UUID, Integer> yeetedTicks = new HashMap<>();
    private static final Map<UUID, ServerWorld> yeetedWorlds = new HashMap<>();

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(SpinYeetGrabPayload.ID, SpinYeetGrabPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SpinYeetReleasePayload.ID, SpinYeetReleasePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpinYeetStartPayload.ID, SpinYeetStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpinYeetCameraPayload.ID, SpinYeetCameraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpinYeetEndPayload.ID, SpinYeetEndPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpinYeetGrabberYawPayload.ID, SpinYeetGrabberYawPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpinYeetImpactPayload.ID, SpinYeetImpactPayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
            SpinYeetGrabPayload.ID,
            (payload, ctx) -> {
                ServerPlayerEntity grabber = ctx.player();
                System.out
                    .println(
                        "[SpinYeet-DEBUG] Grab packet received from "
                            + grabber.getName().getString()
                            + " | enabled="
                            + SpinYeetConfig.ENABLED
                            + " | alreadyGrabbing="
                            + grabberToSession.containsKey(grabber.getUuid())
                    );
                if (!SpinYeetConfig.ENABLED) {
                    System.out.println("[SpinYeet-DEBUG] BLOCKED — set enabled=true in coopmoves_spinyeet.properties");
                } else if (!grabberToSession.containsKey(grabber.getUuid())) {
                    ServerWorld world = grabber.getServerWorld();
                    Entity target = world.getEntity(payload.targetUUID());
                    if (target == null) {
                        System.out.println("[SpinYeet-DEBUG] BLOCKED — target UUID not found in server world");
                    } else {
                        double distSq = target.squaredDistanceTo(grabber);
                        System.out
                            .println("[SpinYeet-DEBUG] Target=" + target.getName().getString() + " distSq=" + String.format("%.2f", distSq) + " max=4.0");
                        if (target instanceof LivingEntity grabbed) {
                            if (distSq > 4.0) {
                                System.out.println("[SpinYeet-DEBUG] BLOCKED — out of range");
                            } else if (grabbedToGrabber.containsKey(target.getUuid())) {
                                System.out.println("[SpinYeet-DEBUG] BLOCKED — already grabbed");
                            } else {
                                System.out.println("[SpinYeet-DEBUG] Starting spin session!");
                                SpinYeetSession session = new SpinYeetSession(grabber.getUuid(), target.getUuid());
                                session.spinAngle = (float)Math.atan2(
                                    target.getZ() - grabber.getZ(), target.getX() - grabber.getX()
                                );
                                grabberToSession.put(grabber.getUuid(), session);
                                grabbedToGrabber.put(target.getUuid(), grabber.getUuid());
                                PoseNetworking.broadcastAnimState(grabber, 106);
                                if (grabbed instanceof ServerPlayerEntity sp) {
                                    PoseNetworking.broadcastAnimState(sp, 107);
                                }

                                broadcast(world, grabber, new SpinYeetStartPayload(grabber.getUuid(), target.getUuid()));
                            }
                        } else {
                            System.out.println("[SpinYeet-DEBUG] BLOCKED — not a LivingEntity");
                        }
                    }
                }
            }
        );
        ServerPlayNetworking.registerGlobalReceiver(SpinYeetReleasePayload.ID, (payload, ctx) -> {
            ServerPlayerEntity grabber = ctx.player();
            SpinYeetSession session = grabberToSession.get(grabber.getUuid());
            if (session != null && session.active) {
                performYeet(grabber.getServerWorld(), grabber, session);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(SpinYeetHandler::onTick);
    }

    private static void onTick(MinecraftServer server) {
        for (UUID grabberUuid : new ArrayList<>(grabberToSession.keySet())) {
            SpinYeetSession session = grabberToSession.get(grabberUuid);
            if (session != null && session.active) {
                ServerPlayerEntity grabber = server.getPlayerManager().getPlayer(grabberUuid);
                if (grabber == null) {
                    cancelSession(server, session);
                } else {
                    ServerWorld world = grabber.getServerWorld();
                    Entity grabbed = world.getEntity(session.grabbedUuid);
                    if (grabbed != null && grabbed.isAlive()) {
                        session.tickCount++;
                        float t = Math.min(session.tickCount / 100.0F, 1.0F);
                        session.currentSpeed = 0.05F + 0.75F * (t * t);
                        session.spinAngle = session.spinAngle + session.currentSpeed;
                        double orbitX = grabber.getX() + Math.cos(session.spinAngle) * 1.5;
                        double orbitY = grabber.getY() + 0.6F;
                        double orbitZ = grabber.getZ() + Math.sin(session.spinAngle) * 1.5;
                        float grabbedYaw = (float)Math.toDegrees(session.spinAngle) + 90.0F;
                        grabbed.setVelocity(0.0, 0.0, 0.0);
                        grabbed.setSwimming(true);
                        if (grabbed instanceof ServerPlayerEntity sp) {
                            sp.setPosition(orbitX, orbitY, orbitZ);
                            sp.networkHandler.requestTeleport(orbitX, orbitY, orbitZ, grabbedYaw, grabbed.getPitch());
                        } else {
                            grabbed.setPosition(orbitX, orbitY, orbitZ);
                            grabbed.setYaw(grabbedYaw);
                        }

                        float yawDelta = (float)Math.toDegrees(session.currentSpeed) * 1.0F;
                        ServerPlayNetworking.send(grabber, new SpinYeetGrabberYawPayload(yawDelta));
                        if (session.tickCount % 2 == 0 && grabbed instanceof ServerPlayerEntity sp) {
                            float tiltT = Math.min(session.tickCount / 100.0F, 1.0F);
                            float roll = tiltT * 90.0F;
                            ServerPlayNetworking.send(sp, new SpinYeetCameraPayload(roll));
                        }
                    } else {
                        cancelSession(server, session);
                    }
                }
            }
        }

        for (UUID yeetedUuid : new ArrayList<>(yeetedTicks.keySet())) {
            int ticksLeft = yeetedTicks.get(yeetedUuid);
            if (ticksLeft <= 0) {
                yeetedTicks.remove(yeetedUuid);
                ServerWorld w = yeetedWorlds.remove(yeetedUuid);
                Entity e = w != null ? w.getEntity(yeetedUuid) : null;
                if (e instanceof ServerPlayerEntity sp) {
                    sp.setSwimming(false);
                    PoseNetworking.broadcastAnimState(sp, 0);
                } else if (e != null) {
                    e.setSwimming(false);
                }
            } else {
                yeetedTicks.put(yeetedUuid, ticksLeft - 1);
                ServerWorld w = yeetedWorlds.get(yeetedUuid);
                if (w == null) {
                    yeetedTicks.remove(yeetedUuid);
                } else {
                    Entity yeeted = w.getEntity(yeetedUuid);
                    if (yeeted != null && yeeted.isAlive()) {
                        Vec3d pos = yeeted.getPos();
                        BlockPos center = yeeted.getBlockPos();
                        int r = (int)Math.ceil(2.0);
                        float rSq = 4.0F;
                        boolean hitAny = false;

                        for (int dx = -r; dx <= r; dx++) {
                            for (int dy = -r; dy <= r; dy++) {
                                for (int dz = -r; dz <= r; dz++) {
                                    if (!(dx * dx + dy * dy + dz * dz > rSq)) {
                                        BlockPos bp = center.add(dx, dy, dz);
                                        BlockState state = w.getBlockState(bp);
                                        if (!state.isAir() && !(state.getHardness(w, bp) < 0.0F)) {
                                            w.setBlockState(bp, Blocks.AIR.getDefaultState(), 19);
                                            hitAny = true;
                                        }
                                    }
                                }
                            }
                        }

                        if (hitAny) {
                            w.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.5, pos.z, 2, 0.4, 0.4, 0.4, 0.0);
                            w.playSound(
                                null,
                                pos.x,
                                pos.y,
                                pos.z,
                                (SoundEvent)SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                                SoundCategory.BLOCKS,
                                0.6F,
                                1.6F
                            );
                        }
                    } else {
                        yeetedTicks.remove(yeetedUuid);
                        yeetedWorlds.remove(yeetedUuid);
                    }
                }
            }
        }
    }

    private static void performYeet(ServerWorld world, ServerPlayerEntity grabber, SpinYeetSession session) {
        session.active = false;
        Entity grabbed = world.getEntity(session.grabbedUuid);
        float speedT = Math.min(session.currentSpeed / 0.8F, 1.0F);
        float yeetT = speedT * speedT;
        float horiz = 0.8F + 6.2F * yeetT;
        float vert = 0.3F + 2.2F * yeetT;
        Vec3d look = grabber.getRotationVec(1.0F);
        double hLen = Math.sqrt(look.x * look.x + look.z * look.z);
        double normX = hLen > 0.001 ? look.x / hLen : 0.0;
        double normZ = hLen > 0.001 ? look.z / hLen : 0.0;
        if (grabbed != null && grabbed.isAlive()) {
            grabbed.setSwimming(false);
            grabbed.setVelocity(normX * horiz, vert, normZ * horiz);
            grabbed.velocityModified = true;
            if (grabbed instanceof ServerPlayerEntity sp) {
                sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
            }
        }

        PoseNetworking.broadcastAnimState(grabber, 0);
        ServerPlayNetworking.send(grabber, new SpinYeetImpactPayload());
        yeetedTicks.put(session.grabbedUuid, 100);
        yeetedWorlds.put(session.grabbedUuid, world);
        broadcast(world, grabber, new SpinYeetEndPayload(session.grabbedUuid, true));
        removeSession(session);
    }

    private static void cancelSession(MinecraftServer server, SpinYeetSession session) {
        session.active = false;
        SpinYeetEndPayload end = new SpinYeetEndPayload(session.grabbedUuid, false);

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, end);
        }

        ServerPlayerEntity grabber = server.getPlayerManager().getPlayer(session.grabberUuid);
        if (grabber != null) {
            PoseNetworking.broadcastAnimState(grabber, 0);
        }

        ServerPlayerEntity grabbed = server.getPlayerManager().getPlayer(session.grabbedUuid);
        if (grabbed != null) {
            grabbed.setSwimming(false);
            PoseNetworking.broadcastAnimState(grabbed, 0);
        }

        removeSession(session);
    }

    private static void removeSession(SpinYeetSession session) {
        grabberToSession.remove(session.grabberUuid);
        grabbedToGrabber.remove(session.grabbedUuid);
    }

    private static <T extends CustomPayload> void broadcast(ServerWorld world, ServerPlayerEntity near, T payload) {
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(near) < 2500.0) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            SpinYeetSession asGrabber = grabberToSession.get(player.getUuid());
            if (asGrabber != null) {
                cancelSession(server, asGrabber);
            } else {
                UUID grabberUuid = grabbedToGrabber.get(player.getUuid());
                if (grabberUuid != null) {
                    SpinYeetSession asGrabbed = grabberToSession.get(grabberUuid);
                    if (asGrabbed != null) {
                        cancelSession(server, asGrabbed);
                    }
                }
            }
        }
    }

    public static boolean isCurrentlyGrabbed(UUID uuid) {
        return grabbedToGrabber.containsKey(uuid);
    }
}
