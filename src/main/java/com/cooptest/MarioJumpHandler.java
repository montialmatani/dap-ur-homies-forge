package com.cooptest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public class MarioJumpHandler {
    private static final Map<UUID, Long> jumpCooldown = new HashMap<>();
    private static final long COOLDOWN_MS = 500L;
    private static final Map<UUID, Long> marioAnimEnd = new HashMap<>();
    private static final Map<UUID, Long> popAnimEnd = new HashMap<>();
    private static final long MARIO_ANIM_DURATION_MS = 500L;
    private static final long POP_ANIM_DURATION_MS = 417L;
    private static final double LAUNCH_VELOCITY = 0.68;

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(MarioJumpHandler.MarioJumpRequestPayload.ID, MarioJumpHandler.MarioJumpRequestPayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(MarioJumpHandler.MarioJumpRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> onMarioJumpRequest(player));
        });
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            long now = System.currentTimeMillis();
            Iterator<Entry<UUID, Long>> marioIt = marioAnimEnd.entrySet().iterator();

            while (marioIt.hasNext()) {
                Entry<UUID, Long> entry = marioIt.next();
                if (now >= entry.getValue()) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                    if (player != null) {
                        PoseNetworking.broadcastAnimState(player, 0);
                    }

                    marioIt.remove();
                }
            }

            Iterator<Entry<UUID, Long>> popIt = popAnimEnd.entrySet().iterator();

            while (popIt.hasNext()) {
                Entry<UUID, Long> entry = popIt.next();
                if (now >= entry.getValue()) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                    if (player != null) {
                        PoseNetworking.broadcastAnimState(player, 0);
                    }

                    popIt.remove();
                }
            }
        });
    }

    private static void onMarioJumpRequest(ServerPlayerEntity jumper) {
        if (jumper != null) {
            UUID jumperId = jumper.getUuid();
            long now = System.currentTimeMillis();
            if (!jumpCooldown.containsKey(jumperId) || now - jumpCooldown.get(jumperId) >= 500L) {
                if (!HighFiveHandler.isInBlockingState(jumperId)) {
                    ServerPlayerEntity target = findPlayerBelow(jumper);
                    if (target != null) {
                        executeMarioJump(jumper, target);
                        jumpCooldown.put(jumperId, now);
                    }
                }
            }
        }
    }

    private static ServerPlayerEntity findPlayerBelow(ServerPlayerEntity jumper) {
        ServerWorld world = jumper.getServerWorld();
        Vec3d jumperPos = jumper.getPos();
        double jumperFeetY = jumperPos.y;
        Box searchBox = new Box(
            jumperPos.x - 0.8,
            jumperPos.y - 2.5,
            jumperPos.z - 0.8,
            jumperPos.x + 0.8,
            jumperPos.y + 0.5,
            jumperPos.z + 0.8
        );

        for (ServerPlayerEntity target : world.getEntitiesByClass(ServerPlayerEntity.class, searchBox, p -> p != jumper && p.isAlive())) {
            Vec3d targetPos = target.getPos();
            double targetHeadY = targetPos.y + target.getStandingEyeHeight() + 0.15;
            double heightDiff = jumperFeetY - targetHeadY;
            if (heightDiff >= -0.35 && heightDiff <= 0.5) {
                double horizDist = Math.sqrt(
                    Math.pow(jumperPos.x - targetPos.x, 2.0) + Math.pow(jumperPos.z - targetPos.z, 2.0)
                );
                if (horizDist <= 0.7) {
                    return target;
                }
            }
        }

        return null;
    }

    private static void executeMarioJump(ServerPlayerEntity jumper, ServerPlayerEntity target) {
        ServerWorld world = jumper.getServerWorld();
        Vec3d pos = jumper.getPos();
        long now = System.currentTimeMillis();
        System.out.println("[MARIO JUMP] Executing mario jump!");
        System.out.println("[MARIO JUMP] Jumper: " + jumper.getName().getString() + " (UUID: " + jumper.getUuid() + ")");
        System.out.println("[MARIO JUMP] Target: " + target.getName().getString() + " (UUID: " + target.getUuid() + ")");
        System.out.println("[MARIO JUMP] Broadcasting MARIO_JUMP (ordinal 30) to jumper");
        System.out.println("[MARIO JUMP] Broadcasting POP (ordinal 31) to target");
        Vec3d velocity = jumper.getVelocity();
        jumper.setVelocity(velocity.x, 0.68, velocity.z);
        jumper.velocityModified = true;
        PoseNetworking.broadcastAnimState(jumper, 30);
        PoseNetworking.broadcastAnimState(target, 31);
        System.out.println("[MARIO JUMP] Animations broadcast complete IT WORKING FINALLY!");
        marioAnimEnd.put(jumper.getUuid(), now + 500L);
        popAnimEnd.put(target.getUuid(), now + 417L);
        world.playSound(null, pos.x, pos.y, pos.z, ModSounds.MARIO_JUMP, SoundCategory.PLAYERS, 1.0F, 1.0F);
        jumper.sendMessage(Text.literal("§a WAHOO!"), true);
        target.sendMessage(Text.literal("§c BONK!"), true);
    }

    public static void cleanup(UUID playerId) {
        jumpCooldown.remove(playerId);
        marioAnimEnd.remove(playerId);
        popAnimEnd.remove(playerId);
    }

    public record MarioJumpRequestPayload() implements CustomPayload {
        public static final Id<MarioJumpHandler.MarioJumpRequestPayload> ID = new Id(new Identifier("testcoop", "mario_jump_request"));
        public static final PacketCodec<PacketByteBuf, MarioJumpHandler.MarioJumpRequestPayload> CODEC = PacketCodec.unit(
            new MarioJumpHandler.MarioJumpRequestPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
