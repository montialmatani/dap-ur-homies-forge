package com.cooptest;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.player.UseEntityCallback;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.PlayerLookup;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class PushInteractionHandler {
    private static final float PUSH_RANGE = 2.5F;
    private static final long HOLD_REQUIRED_MS = 1500L;
    private static final long READY_WINDOW_MS = 3000L;
    private static final long COOLDOWN_MS = 1500L;
    private static final long PUSH_IMMUNITY_MS = 500L;
    private static final long JUMP_WINDOW_MS = 800L;
    private static final double VEL_LOW = 0.5;
    private static final double VEL_MEDIUM = 1.8;
    private static final double VEL_HIGH = 3.5;
    private static final HashMap<UUID, UUID> holdTarget = new HashMap<>();
    private static final HashMap<UUID, Long> holdStart = new HashMap<>();
    private static final HashMap<UUID, UUID> readyPushers = new HashMap<>();
    private static final HashMap<UUID, Long> readyStart = new HashMap<>();
    private static final HashMap<UUID, Long> cooldowns = new HashMap<>();
    public static final HashMap<UUID, Long> pushImmunity = new HashMap<>();
    public static final HashMap<UUID, Long> lastJumpTime = new HashMap<>();
    public static final Identifier PUSH_ANIM_ID = new Identifier("cooptest", "push_anim");

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(PushInteractionHandler.PushAnimPayload.ID, PushInteractionHandler.PushAnimPayload.CODEC);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PushInteractionHandler::tick);
        UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
            if (world.isClient) {
                return ActionResult.PASS;
            } else if (player instanceof ServerPlayerEntity sp) {
                if (!(entity instanceof ServerPlayerEntity target)) {
                    return ActionResult.PASS;
                } else if (!CoopMovesConfig.get().enablePush) {
                    return ActionResult.PASS;
                } else {
                    long now = System.currentTimeMillis();
                    if (sp.isSneaking()) {
                        if (HighFiveHandler.isInBlockingState(sp.getUuid())) {
                            return ActionResult.PASS;
                        } else if (isOnCooldown(sp.getUuid(), now)) {
                            return ActionResult.PASS;
                        } else if (readyPushers.containsKey(sp.getUuid())) {
                            return ActionResult.PASS;
                        } else if (sp.distanceTo(target) > 2.5F) {
                            return ActionResult.PASS;
                        } else {
                            UUID prevTarget = holdTarget.get(sp.getUuid());
                            if (!target.getUuid().equals(prevTarget)) {
                                holdTarget.put(sp.getUuid(), target.getUuid());
                                holdStart.put(sp.getUuid(), now);
                            }

                            return ActionResult.SUCCESS;
                        }
                    } else {
                        UUID intendedTarget = readyPushers.get(target.getUuid());
                        if (intendedTarget != null && intendedTarget.equals(sp.getUuid())) {
                            Long rs = readyStart.get(target.getUuid());
                            if (rs == null || now - rs > 3000L) {
                                return ActionResult.PASS;
                            } else if (isOnCooldown(target.getUuid(), now)) {
                                return ActionResult.PASS;
                            } else {
                                Long jt = lastJumpTime.get(sp.getUuid());
                                boolean recentJump = jt != null && now - jt < 800L;
                                double vel;
                                if (recentJump) {
                                    vel = capToCeiling(sp, 3.5);
                                } else if (sp.isSneaking()) {
                                    vel = capToCeiling(sp, 0.5);
                                } else {
                                    vel = capToCeiling(sp, 1.8);
                                }

                                readyPushers.remove(target.getUuid());
                                readyStart.remove(target.getUuid());
                                executePush(target, sp, vel, now);
                                return ActionResult.SUCCESS;
                            }
                        } else {
                            return ActionResult.PASS;
                        }
                    }
                }
            } else {
                return ActionResult.PASS;
            }
        });
    }

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();

        for (Entry<UUID, UUID> entry : new HashMap<>(holdTarget).entrySet()) {
            UUID pusherId = entry.getKey();
            UUID targetId = entry.getValue();
            Long startMs = holdStart.get(pusherId);
            if (startMs == null) {
                holdTarget.remove(pusherId);
            } else {
                ServerPlayerEntity pusher = server.getPlayerManager().getPlayer(pusherId);
                ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetId);
                if (pusher == null || !pusher.isSneaking() || target == null || pusher.distanceTo(target) > 2.5F) {
                    holdTarget.remove(pusherId);
                    holdStart.remove(pusherId);
                } else if (readyPushers.containsKey(pusherId)) {
                    holdTarget.remove(pusherId);
                    holdStart.remove(pusherId);
                } else if (now - startMs >= 1500L) {
                    holdTarget.remove(pusherId);
                    holdStart.remove(pusherId);
                    readyPushers.put(pusherId, targetId);
                    readyStart.put(pusherId, now);
                    Vec3d mid = pusher.getPos().add(target.getPos()).multiply(0.5);
                    pusher.getServerWorld()
                        .playSound(
                            null,
                            mid.x,
                            mid.y,
                            mid.z,
                            (SoundEvent)SoundEvents.field_14793.comp_349(),
                            SoundCategory.field_15248,
                            1.0F,
                            1.8F
                        );
                    pusher.sendMessage(Text.literal("§eTell homie to right-click!"), true);
                    target.sendMessage(Text.literal("§e[Right-click to launch!]"), true);
                }
            }
        }

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!p.isOnGround() && p.getVelocity().y > 0.08) {
                lastJumpTime.put(p.getUuid(), now);
            }
        }

        for (Entry<UUID, UUID> re : new HashMap<>(readyPushers).entrySet()) {
            UUID pusherId = re.getKey();
            UUID intendedTarget = re.getValue();
            Long rs = readyStart.get(pusherId);
            if (rs != null && now - rs <= 3000L) {
                ServerPlayerEntity pusher = server.getPlayerManager().getPlayer(pusherId);
                if (pusher != null) {
                    for (ServerPlayerEntity nearby : server.getPlayerManager().getPlayerList()) {
                        if (!nearby.getUuid().equals(pusherId) && pusher.distanceTo(nearby) <= 2.5F) {
                            UUID nearbyId = nearby.getUuid();
                            if (!nearbyId.equals(intendedTarget)) {
                                readyPushers.put(pusherId, nearbyId);
                                nearby.sendMessage(Text.literal("§e[Right-click to launch!]"), true);
                            }
                            break;
                        }
                    }
                }
            }
        }

        readyPushers.entrySet().removeIf(e -> {
            Long t = readyStart.get(e.getKey());
            return t == null || now - t > 3000L;
        });
        readyStart.entrySet().removeIf(e -> now - e.getValue() > 3000L);
        holdStart.entrySet().removeIf(e -> now - e.getValue() > 10000L);
        lastJumpTime.entrySet().removeIf(e -> now - e.getValue() > 3200L);
        cooldowns.entrySet().removeIf(e -> now - e.getValue() > 3000L);
    }

    private static void executePush(ServerPlayerEntity pusher, ServerPlayerEntity target, double velocity, long now) {
        PushInteractionHandler.PushAnimPayload pkt = new PushInteractionHandler.PushAnimPayload(pusher.getUuid());

        for (ServerPlayerEntity p : PlayerLookup.tracking(pusher)) {
            ServerPlayNetworking.send(p, pkt);
        }

        ServerPlayNetworking.send(pusher, pkt);
        target.setVelocity(target.getVelocity().x, 0.0, target.getVelocity().z);
        target.addVelocity(0.0, velocity, 0.0);
        target.velocityModified = true;
        pushImmunity.put(target.getUuid(), now);
        LaunchedPlayerTracker.markPlayerAsLaunched(target.getUuid());
        UUID carried = GrabMechanic.holding.get(target.getUuid());
        if (carried != null) {
            ServerPlayerEntity c = target.getServer().getPlayerManager().getPlayer(carried);
            if (c != null) {
                c.addVelocity(0.0, velocity * 0.85, 0.0);
                c.velocityModified = true;
                LaunchedPlayerTracker.markPlayerAsLaunched(c.getUuid());
                pushImmunity.put(c.getUuid(), now);
            }
        }

        cooldowns.put(pusher.getUuid(), now);
        PoseNetworking.broadcastPoseChange(Objects.requireNonNull(pusher.getServer()), pusher.getUuid(), PoseState.PUSH_ACTION);
    }

    private static double capToCeiling(ServerPlayerEntity t, double base) {
        BlockPos pos = t.getBlockPos();

        for (int y = 1; y <= 15; y++) {
            BlockPos check = pos.up(y);
            BlockState state = t.getWorld().getBlockState(check);
            if (!state.isAir() && state.isSolidBlock(t.getWorld(), check)) {
                return Math.min(base, Math.sqrt(3.2 * Math.max(2, y - 1)));
            }
        }

        return base;
    }

    private static boolean isOnCooldown(UUID uuid, long now) {
        Long t = cooldowns.get(uuid);
        return t != null && now - t < 1500L;
    }

    public static boolean hasPushImmunity(UUID uuid) {
        Long t = pushImmunity.get(uuid);
        if (t == null) {
            return false;
        } else if (System.currentTimeMillis() - t < 500L) {
            return true;
        } else {
            pushImmunity.remove(uuid);
            return false;
        }
    }

    public static void cleanup(UUID uuid) {
        holdTarget.remove(uuid);
        holdStart.remove(uuid);
        readyPushers.remove(uuid);
        readyStart.remove(uuid);
        cooldowns.remove(uuid);
        pushImmunity.remove(uuid);
        lastJumpTime.remove(uuid);
        holdTarget.entrySet().removeIf(e -> uuid.equals(e.getValue()));
        readyPushers.entrySet().removeIf(e -> uuid.equals(e.getValue()));
    }

    public static void cleanupExpiredImmunity() {
        long now = System.currentTimeMillis();
        pushImmunity.entrySet().removeIf(e -> now - e.getValue() > 500L);
    }

    public record PushAnimPayload(UUID playerId) implements CustomPayload {
        public static final Id<PushInteractionHandler.PushAnimPayload> ID = new Id(PushInteractionHandler.PUSH_ANIM_ID);
        public static final PacketCodec<PacketByteBuf, PushInteractionHandler.PushAnimPayload> CODEC = PacketCodec.of(
            (p, buf) -> buf.writeUuid(p.playerId), buf -> new PushInteractionHandler.PushAnimPayload(buf.readUuid())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
