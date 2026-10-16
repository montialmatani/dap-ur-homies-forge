package com.cooptest.spin;

import com.cooptest.HighFiveHandler;
import com.cooptest.PoseNetworking;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Hand;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.server.MinecraftServer;

public class HandSpinArmorStandTest {
    private static final Map<UUID, HandSpinArmorStandTest.TestSession> sessions = new HashMap<>();
    private static boolean tickRegistered = false;

    public static void ensureTickRegistered() {
        if (!tickRegistered) {
            tickRegistered = true;
            ServerTickEvents.END_SERVER_TICK.register(HandSpinArmorStandTest::tick);
        }
    }

    public static int start(ServerPlayerEntity player) {
        ensureTickRegistered();
        UUID id = player.getUuid();
        if (sessions.containsKey(id)) {
            player.sendMessage(Text.literal("§eAlready running — /testspin stop first"), false);
            return 0;
        } else {
            Vec3d fwd = HighFiveHandler.horizontalForward(player);
            double spawnDist = 1.5;
            Vec3d playerPos = player.getPos();
            Vec3d standPos = new Vec3d(
                playerPos.x + fwd.x * spawnDist, playerPos.y, playerPos.z + fwd.z * spawnDist
            );
            ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, player.getServerWorld());
            stand.setPos(standPos.x, standPos.y, standPos.z);
            stand.setInvulnerable(true);
            stand.setCustomName(Text.literal("HandSpin Test Partner"));
            stand.setCustomNameVisible(true);
            player.getServerWorld().spawnEntity(stand);
            Vec3d center = playerPos.add(standPos).multiply(0.5);
            double startAngle = Math.atan2(playerPos.z - center.z, playerPos.x - center.x);
            HandSpinArmorStandTest.TestSession s = new HandSpinArmorStandTest.TestSession(id, center, startAngle, player.getServer().getTicks());
            s.stand = stand;
            s.continuousYaw1 = player.getYaw();
            s.continuousYaw2 = stand.getYaw();
            sessions.put(id, s);
            ServerWorld world = player.getServerWorld();
            world.playSound(null, center.x, center.y + 1.0, center.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 1.3F);
            world.spawnParticles(ParticleTypes.CLOUD, center.x, center.y + 1.0, center.z, 14, 0.5, 0.4, 0.5, 0.03);
            world.spawnParticles(ParticleTypes.CRIT, center.x, center.y + 1.0, center.z, 10, 0.4, 0.3, 0.4, 0.05);
            player.sendMessage(Text.literal("§aHand Spin test started. §7/testspin cancel | cancel slow | damage | stop"), false);
            return 1;
        }
    }

    public static int cancel(ServerPlayerEntity player, boolean slow) {
        HandSpinArmorStandTest.TestSession s = sessions.get(player.getUuid());
        if (s == null) {
            player.sendMessage(Text.literal("§cNo test spin running — /testspin first"), false);
            return 0;
        } else if (s.stage != HandSpinArmorStandTest.Stage.RAMPING_UP && s.stage != HandSpinArmorStandTest.Stage.SPINNING) {
            player.sendMessage(Text.literal("§cAlready ending"), false);
            return 0;
        } else {
            s.stage = slow ? HandSpinArmorStandTest.Stage.ENDING_SLOW : HandSpinArmorStandTest.Stage.ENDING_FAST;
            player.sendMessage(
                Text.literal(slow ? "§7Branch B: slow decel to a full stop, then separate." : "§7Branch A: fast decel then push-apart."), false
            );
            return 1;
        }
    }

    public static int damage(ServerPlayerEntity player) {
        HandSpinArmorStandTest.TestSession s = sessions.get(player.getUuid());
        if (s == null) {
            player.sendMessage(Text.literal("§cNo test spin running — /testspin first"), false);
            return 0;
        } else if (s.stage != HandSpinArmorStandTest.Stage.RAMPING_UP && s.stage != HandSpinArmorStandTest.Stage.SPINNING) {
            player.sendMessage(Text.literal("§cAlready ending"), false);
            return 0;
        } else {
            s.stage = HandSpinArmorStandTest.Stage.DAMAGE_CUT;
            s.endAnimStartTick = -1L;
            player.sendMessage(Text.literal("§cBranch C: damage interrupt — instant cut, big knockback, no end anim."), false);
            return 1;
        }
    }

    public static int stop(ServerPlayerEntity player) {
        HandSpinArmorStandTest.TestSession s = sessions.remove(player.getUuid());
        if (s == null) {
            return 0;
        } else {
            if (s.stand != null) {
                s.stand.discard();
            }

            PoseNetworking.broadcastAnimState(player, 0);
            player.sendMessage(Text.literal("§7Test spin force-stopped."), false);
            return 1;
        }
    }

    private static void tick(MinecraftServer server) {
        if (!sessions.isEmpty()) {
            for (HandSpinArmorStandTest.TestSession s : new ArrayList<>(sessions.values())) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(s.playerId);
                if (player != null && s.stand != null && s.stand.isAlive()) {
                    switch (s.stage) {
                        case RAMPING_UP:
                        case SPINNING:
                            long elapsedMs = (server.getTicks() - s.startTick) * 50L;
                            if (s.stage == HandSpinArmorStandTest.Stage.RAMPING_UP) {
                                if (elapsedMs == 0L) {
                                    PoseNetworking.broadcastAnimState(player, 120);
                                }

                                if (elapsedMs >= 334L) {
                                    PoseNetworking.broadcastAnimState(player, 121);
                                    s.stage = HandSpinArmorStandTest.Stage.SPINNING;
                                }
                            }

                            s.angularVel = Math.min(0.2, s.angularVel + 0.0037);
                            s.angle = s.angle + s.angularVel;
                            applyOrbit(s, player);
                            swingHandIfDue(s, player, server);
                            playLoopEffects(s, player, server);
                            refreshShakePulse(s, player);
                            break;
                        case ENDING_FAST:
                            s.angularVel = Math.max(0.0, s.angularVel - 0.006);
                            s.angle = s.angle + s.angularVel;
                            applyOrbit(s, player);
                            swingHandIfDue(s, player, server);
                            if (s.angularVel <= 1.0E-4) {
                                beginPushApart(s, player);
                            }
                            break;
                        case ENDING_SLOW:
                            s.angularVel = Math.max(0.0, s.angularVel - 0.0015);
                            s.angle = s.angle + s.angularVel;
                            applyOrbit(s, player);
                            swingHandIfDue(s, player, server);
                            if (s.angularVel <= 1.0E-4) {
                                finishToEnd(s, player, server);
                            }
                            break;
                        case DAMAGE_CUT:
                            if (s.endAnimStartTick == -1L) {
                                beginDamageCutKnockback(s, player, server);
                            } else if (server.getTicks() - s.endAnimStartTick >= 2L) {
                                PoseNetworking.broadcastAnimState(player, 0);
                                s.stand.discard();
                                sessions.remove(s.playerId);
                                player.sendMessage(Text.literal("§cTest complete — damage cut."), false);
                            }
                            break;
                        case PUSH_APART:
                            if (s.pushTicksRemaining > 0) {
                                Vec3d step = s.playerRemainingDelta.multiply(1.0 / s.pushTicksRemaining);
                                Vec3d np = player.getPos().add(step);
                                player.requestTeleport(np.x, np.y, np.z);
                                s.playerRemainingDelta = s.playerRemainingDelta.subtract(step);
                                Vec3d standStep = s.standRemainingDelta.multiply(1.0 / s.pushTicksRemaining);
                                Vec3d nsp = s.stand.getPos().add(standStep);
                                s.stand.requestTeleport(nsp.x, nsp.y, nsp.z);
                                s.standRemainingDelta = s.standRemainingDelta.subtract(standStep);
                                s.pushTicksRemaining--;
                            } else {
                                finishToEnd(s, player, server);
                            }
                            break;
                        case DONE:
                            if (s.endAnimStartTick != -1L && (server.getTicks() - s.endAnimStartTick) * 50L >= 584L) {
                                PoseNetworking.broadcastAnimState(player, 0);
                                s.stand.discard();
                                sessions.remove(s.playerId);
                                player.sendMessage(Text.literal("§aTest complete."), false);
                            }
                    }
                } else {
                    if (s.stand != null) {
                        s.stand.discard();
                    }

                    sessions.remove(s.playerId);
                }
            }
        }
    }

    private static void swingHandIfDue(HandSpinArmorStandTest.TestSession s, ServerPlayerEntity player, MinecraftServer server) {
        long tick = server.getTicks();
        if (s.lastHandSwingTick == -1L || tick - s.lastHandSwingTick >= 4L) {
            s.lastHandSwingTick = tick;
            player.swingHand(Hand.MAIN_HAND, true);
        }
    }

    private static void playLoopEffects(HandSpinArmorStandTest.TestSession s, ServerPlayerEntity player, MinecraftServer server) {
        ServerWorld world = player.getServerWorld();
        world.spawnParticles(ParticleTypes.CRIT, s.center.x, s.center.y + 1.0, s.center.z, 2, 0.5, 0.4, 0.5, 0.02);
        if (server.getTicks() % 10 == 0) {
            world.playSound(
                null, s.center.x, s.center.y + 1.0, s.center.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.5F, 1.6F
            );
        }
    }

    private static void refreshShakePulse(HandSpinArmorStandTest.TestSession s, ServerPlayerEntity player) {
        long now = System.currentTimeMillis();
        if (now - s.lastShakePulseMs >= 150L) {
            s.lastShakePulseMs = now;
            ServerPlayNetworking.send(player, new HandSpinHandler.HandSpinShakePulsePayload(0.12F, 200L));
        }
    }

    private static void beginDamageCutKnockback(HandSpinArmorStandTest.TestSession s, ServerPlayerEntity player, MinecraftServer server) {
        Vec3d pos1 = player.getPos();
        Vec3d pos2 = s.stand.getPos();
        Vec3d out1 = pos1.subtract(s.center);
        Vec3d out2 = pos2.subtract(s.center);
        double len1 = Math.max(0.001, out1.horizontalLength());
        double len2 = Math.max(0.001, out2.horizontalLength());
        Vec3d dir1 = new Vec3d(out1.x / len1, 0.0, out1.z / len1);
        Vec3d dir2 = new Vec3d(out2.x / len2, 0.0, out2.z / len2);
        player.setVelocity(player.getVelocity().add(dir1.multiply(0.75)).add(0.0, 0.15, 0.0));
        player.velocityModified = true;
        Vec3d standTarget = pos2.add(dir2.multiply(1.5));
        s.stand.requestTeleport(standTarget.x, standTarget.y, standTarget.z);
        PoseNetworking.broadcastAnimState(player, 0);
        ServerWorld world = player.getServerWorld();
        world.playSound(
            null, s.center.x, s.center.y + 1.0, s.center.z, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.2F, 0.7F
        );
        s.endAnimStartTick = server.getTicks();
    }

    private static void applyOrbit(HandSpinArmorStandTest.TestSession s, ServerPlayerEntity player) {
        double x1 = s.center.x + Math.cos(s.angle) * 0.75;
        double z1 = s.center.z + Math.sin(s.angle) * 0.75;
        double x2 = s.center.x + Math.cos(s.angle + Math.PI) * 0.75;
        double z2 = s.center.z + Math.sin(s.angle + Math.PI) * 0.75;
        double y = s.center.y;
        player.requestTeleport(x1, y, z1);
        s.stand.requestTeleport(x2, y, z2);
        float yaw1 = (float)Math.toDegrees(Math.atan2(x1 - s.center.x, z1 - s.center.z));
        float yaw2 = (float)Math.toDegrees(Math.atan2(x2 - s.center.x, z2 - s.center.z));
        player.setYaw(yaw1);
        player.setHeadYaw(yaw1);
        player.setBodyYaw(yaw1);
        s.stand.setYaw(yaw2);
        s.stand.setHeadYaw(yaw2);
        s.stand.setBodyYaw(yaw2);
    }

    private static float unwrapYaw(Float previousContinuous, float rawTargetYaw) {
        if (previousContinuous == null) {
            return rawTargetYaw;
        } else {
            float delta = MathHelper.wrapDegrees(rawTargetYaw - previousContinuous);
            return previousContinuous + delta;
        }
    }

    private static void beginPushApart(HandSpinArmorStandTest.TestSession s, ServerPlayerEntity player) {
        Vec3d pos1 = player.getPos();
        Vec3d pos2 = s.stand.getPos();
        Vec3d out1 = pos1.subtract(s.center);
        Vec3d out2 = pos2.subtract(s.center);
        double len1 = Math.max(0.001, out1.horizontalLength());
        double len2 = Math.max(0.001, out2.horizontalLength());
        Vec3d dir1 = new Vec3d(out1.x / len1, 0.0, out1.z / len1);
        Vec3d dir2 = new Vec3d(out2.x / len2, 0.0, out2.z / len2);
        player.setVelocity(player.getVelocity().add(dir1.multiply(0.35)));
        player.velocityModified = true;
        s.playerRemainingDelta = dir1.multiply(0.6);
        s.standRemainingDelta = dir2.multiply(0.6);
        s.pushTicksRemaining = 6;
        s.stage = HandSpinArmorStandTest.Stage.PUSH_APART;
    }

    private static void finishToEnd(HandSpinArmorStandTest.TestSession s, ServerPlayerEntity player, MinecraftServer server) {
        PoseNetworking.broadcastAnimState(player, 122);
        s.endAnimStartTick = server.getTicks();
        s.stage = HandSpinArmorStandTest.Stage.DONE;
    }

    private static enum Stage {
        RAMPING_UP,
        SPINNING,
        ENDING_FAST,
        ENDING_SLOW,
        DAMAGE_CUT,
        PUSH_APART,
        DONE;
    }

    private static class TestSession {
        final UUID playerId;
        ArmorStandEntity stand;
        Vec3d center;
        double angle;
        double angularVel = 0.0;
        HandSpinArmorStandTest.Stage stage = HandSpinArmorStandTest.Stage.RAMPING_UP;
        long startTick;
        long endAnimStartTick = -1L;
        Vec3d playerRemainingDelta;
        Vec3d standRemainingDelta;
        int pushTicksRemaining = 0;
        long lastHandSwingTick = -1L;
        long lastShakePulseMs = 0L;
        Float continuousYaw1 = null;
        Float continuousYaw2 = null;

        TestSession(UUID playerId, Vec3d center, double startAngle, long startTick) {
            this.playerId = playerId;
            this.center = center;
            this.angle = startAngle;
            this.startTick = startTick;
        }
    }
}
