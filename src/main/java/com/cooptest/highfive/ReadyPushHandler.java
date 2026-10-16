package com.cooptest.highfive;

import com.cooptest.CoopMovesConfig;
import com.cooptest.GrabMechanic;
import com.cooptest.LaunchedPlayerTracker;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.cooptest.PushInteractionHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.player.UseEntityCallback;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.PlayerLookup;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Hand;
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

public class ReadyPushHandler {
    public static final long ARM_REQUIRED_MS = 1500L;
    public static final float PUSH_RANGE = 2.5F;
    public static final long INPUT_TIMEOUT_MS = 400L;
    public static final long START_ANIM_MS = 400L;
    public static final long PUSH_ANIM_MS = 400L;
    public static final long JUMP_WINDOW_MS = 800L;
    public static final double VEL_LOW = 0.5;
    public static final double VEL_MEDIUM = 1.8;
    public static final double VEL_HIGH = 3.5;
    private static final int ANIM_NONE = 0;
    private static final int ANIM_PUSH_START = 13;
    private static final int ANIM_PUSH_IDLE = 14;
    private static final int ANIM_PUSHING = 15;
    private static final Map<UUID, Long> armingSince = new HashMap<>();
    private static final Map<UUID, Long> inputLastSeen = new HashMap<>();
    private static final Map<UUID, Long> armed = new HashMap<>();
    private static final Map<UUID, Long> pushingUntil = new HashMap<>();
    private static final Map<UUID, Long> lastJumpTime = new HashMap<>();

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(ReadyPushHandler.PushInputPayload.ID, ReadyPushHandler.PushInputPayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ReadyPushHandler.PushInputPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            ctx.server().execute(() -> onInput(player, payload.shift(), payload.rightClick()));
        });
        ServerTickEvents.END_SERVER_TICK.register(ReadyPushHandler::tick);
        UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            } else if (hand != Hand.field_5808) {
                return ActionResult.PASS;
            } else if (!CoopMovesConfig.get().enablePush) {
                return ActionResult.PASS;
            } else if (player instanceof ServerPlayerEntity launchee) {
                if (entity instanceof ServerPlayerEntity pusher) {
                    if (launchee == pusher) {
                        return ActionResult.PASS;
                    } else if (!armed.containsKey(pusher.getUuid())) {
                        return ActionResult.PASS;
                    } else if (isMidPush(pusher.getUuid())) {
                        return ActionResult.PASS;
                    } else if (pusher.distanceTo(launchee) > CoopMovesConfig.get().pushRange) {
                        return ActionResult.PASS;
                    } else if (PushInteractionHandler.hasPushImmunity(launchee.getUuid())) {
                        return ActionResult.PASS;
                    } else {
                        launch(pusher, launchee);
                        return ActionResult.SUCCESS;
                    }
                } else {
                    return ActionResult.PASS;
                }
            } else {
                return ActionResult.PASS;
            }
        });
    }

    private static void onInput(ServerPlayerEntity player, boolean shift, boolean rightClick) {
        UUID id = player.getUuid();
        long now = System.currentTimeMillis();
        inputLastSeen.put(id, now);
        if (armed.containsKey(id)) {
            if (!shift) {
                disarm(player);
            }
        } else if (PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE) != PoseState.GRAB_READY) {
            armingSince.remove(id);
        } else {
            if (shift && rightClick) {
                armingSince.putIfAbsent(id, now);
            } else {
                armingSince.remove(id);
            }
        }
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();

        for (UUID id : new ArrayList<>(inputLastSeen.keySet())) {
            if (now - inputLastSeen.get(id) > 400L) {
                inputLastSeen.remove(id);
                armingSince.remove(id);
                if (armed.containsKey(id)) {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                    if (p != null) {
                        disarm(p);
                    } else {
                        cleanup(id);
                    }
                }
            }
        }

        for (UUID idx : new ArrayList<>(armingSince.keySet())) {
            if (now - armingSince.get(idx) >= CoopMovesConfig.get().pushArmHoldMs) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(idx);
                armingSince.remove(idx);
                if (p != null && PoseNetworking.poseStates.getOrDefault(idx, PoseState.NONE) == PoseState.GRAB_READY) {
                    arm(p);
                }
            }
        }

        for (UUID idxx : new ArrayList<>(armed.keySet())) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(idxx);
            if (p != null && p.isAlive()) {
                Long until = pushingUntil.get(idxx);
                if (until != null && now >= until) {
                    pushingUntil.remove(idxx);
                    PoseNetworking.broadcastAnimState(p, 14);
                }

                Long began = armed.get(idxx);
                if (began != null && began > 0L && now - began >= 400L) {
                    armed.put(idxx, 0L);
                    PoseNetworking.broadcastAnimState(p, 14);
                }
            } else {
                cleanup(idxx);
            }
        }

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!p.isOnGround() && p.getVelocity().y > 0.08) {
                lastJumpTime.put(p.getUuid(), now);
            }
        }

        lastJumpTime.entrySet().removeIf(e -> now - e.getValue() > 3200L);
    }

    private static void arm(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        if (server != null) {
            UUID id = p.getUuid();
            armed.put(id, System.currentTimeMillis());
            PoseNetworking.poseStates.put(id, PoseState.PUSH_IDLE);
            PoseNetworking.broadcastPoseChange(server, id, PoseState.PUSH_IDLE);
            PoseNetworking.broadcastAnimState(p, 13);
            Vec3d pos = p.getPos();
            p.getServerWorld()
                .playSound(
                    null, pos.x, pos.y, pos.z, (SoundEvent)SoundEvents.field_14793.comp_349(), SoundCategory.field_15248, 1.0F, 1.8F
                );
            p.sendMessage(Text.literal("§eReady to boost — homie right-clicks you!"), true);
        }
    }

    private static void disarm(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        UUID id = p.getUuid();
        armed.remove(id);
        pushingUntil.remove(id);
        armingSince.remove(id);
        if (server != null) {
            PoseNetworking.broadcastAnimState(p, 0);
            PoseNetworking.poseStates.put(id, PoseState.GRAB_READY);
            PoseNetworking.broadcastPoseChange(server, id, PoseState.GRAB_READY);
        }
    }

    private static void launch(ServerPlayerEntity pusher, ServerPlayerEntity launchee) {
        long now = System.currentTimeMillis();
        Long jt = lastJumpTime.get(launchee.getUuid());
        boolean recentJump = jt != null && now - jt < 800L;
        boolean airborne = !launchee.isOnGround();
        CoopMovesConfig cfg = CoopMovesConfig.get();
        double vel;
        String tier;
        if (airborne || recentJump) {
            vel = capToCeiling(launchee, cfg.pushPowerHigh);
            tier = "§bHIGH";
        } else if (launchee.isSneaking()) {
            vel = capToCeiling(launchee, cfg.pushPowerLow);
            tier = "§7LOW";
        } else {
            vel = capToCeiling(launchee, cfg.pushPowerMedium);
            tier = "§eMEDIUM";
        }

        launchee.sendMessage(Text.literal(tier + " boost!"), true);
        pushingUntil.put(pusher.getUuid(), now + 400L);
        PoseNetworking.broadcastAnimState(pusher, 15);
        PushInteractionHandler.PushAnimPayload pkt = new PushInteractionHandler.PushAnimPayload(pusher.getUuid());

        for (ServerPlayerEntity p : PlayerLookup.tracking(pusher)) {
            ServerPlayNetworking.send(p, pkt);
        }

        ServerPlayNetworking.send(pusher, pkt);
        launchee.setVelocity(launchee.getVelocity().x, 0.0, launchee.getVelocity().z);
        launchee.addVelocity(0.0, vel, 0.0);
        launchee.velocityModified = true;
        PushInteractionHandler.pushImmunity.put(launchee.getUuid(), now);
        LaunchedPlayerTracker.markPlayerAsLaunched(launchee.getUuid());
        UUID carried = GrabMechanic.holding.get(launchee.getUuid());
        if (carried != null) {
            ServerPlayerEntity c = launchee.getServer().getPlayerManager().getPlayer(carried);
            if (c != null) {
                c.addVelocity(0.0, vel * cfg.pushCarriedMultiplier, 0.0);
                c.velocityModified = true;
                LaunchedPlayerTracker.markPlayerAsLaunched(c.getUuid());
                PushInteractionHandler.pushImmunity.put(c.getUuid(), now);
            }
        }
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

    public static boolean isArmed(UUID id) {
        return armed.containsKey(id);
    }

    private static boolean isMidPush(UUID id) {
        Long until = pushingUntil.get(id);
        return until != null && System.currentTimeMillis() < until;
    }

    public static void cleanup(UUID id) {
        armed.remove(id);
        armingSince.remove(id);
        inputLastSeen.remove(id);
        pushingUntil.remove(id);
        lastJumpTime.remove(id);
    }

    public record PushInputPayload(boolean shift, boolean rightClick) implements CustomPayload {
        public static final Id<ReadyPushHandler.PushInputPayload> ID = new Id(new Identifier("cooptest", "readypush_input"));
        public static final PacketCodec<PacketByteBuf, ReadyPushHandler.PushInputPayload> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeBoolean(p.shift);
            buf.writeBoolean(p.rightClick);
        }, buf -> new ReadyPushHandler.PushInputPayload(buf.readBoolean(), buf.readBoolean()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
