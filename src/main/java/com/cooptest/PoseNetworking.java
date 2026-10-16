package com.cooptest;

import com.cooptest.client.ChargedDapClientHandler;
import com.cooptest.client.CoopAnimationHandler;
import com.cooptest.client.CoopCameraShakeHandler;
import com.cooptest.client.CoopChromaHandler;
import com.cooptest.client.CoopImpactHandler;
import com.cooptest.client.CoopRadialBlurHandler;
import com.cooptest.client.CoopScreenSquishHandler;
import com.cooptest.client.CoopShockwaveRenderer;
import com.cooptest.client.CoopSpeedLinesRenderer;
import com.cooptest.client.FallDapClientHandler;
import com.cooptest.client.HighFiveClientHandler;
import com.cooptest.client.MahitoClientHandler;
import com.cooptest.client.PushClientHandler;
import java.util.HashMap;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class PoseNetworking {
    public static final HashMap<UUID, PoseState> poseStates = new HashMap<>();
    public static final HashMap<UUID, Float> chargeProgress = new HashMap<>();

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(PoseNetworking.PoseSyncPayload.ID, PoseNetworking.PoseSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PoseNetworking.PoseSyncPayload.ID, PoseNetworking.PoseSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PoseNetworking.ChargeSyncPayload.ID, PoseNetworking.ChargeSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PoseNetworking.ChargeSyncPayload.ID, PoseNetworking.ChargeSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PoseNetworking.ThrowAnimPayload.ID, PoseNetworking.ThrowAnimPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PoseNetworking.ThrowAnimPayload.ID, PoseNetworking.ThrowAnimPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PoseNetworking.AnimStateSyncPayload.ID, PoseNetworking.AnimStateSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PoseNetworking.AnimStateSyncPayload.ID, PoseNetworking.AnimStateSyncPayload.CODEC);
    }

    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(PoseNetworking.PoseSyncPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            PoseState state = PoseState.values()[payload.poseOrdinal()];
            context.server().execute(() -> {
                ServerPlayerEntity requester = context.server().getPlayerManager().getPlayer(id);
                if (state == PoseState.GRAB_READY && HighFiveHandler.isInBlockingState(id)) {
                    if (requester != null) {
                        ServerPlayNetworking.send(requester, new PoseNetworking.PoseSyncPayload(id, PoseState.NONE.ordinal()));
                    }
                } else if (state == PoseState.PUSH_IDLE && requester != null && !requester.getMainHandStack().isEmpty()) {
                    requester.sendMessage(Text.literal("§cHold nothing in your main hand to push!"), true);
                    ServerPlayNetworking.send(requester, new PoseNetworking.PoseSyncPayload(id, PoseState.NONE.ordinal()));
                } else {
                    poseStates.put(id, state);

                    for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
                        ServerPlayNetworking.send(player, new PoseNetworking.PoseSyncPayload(id, state.ordinal()));
                    }
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(PoseNetworking.ChargeSyncPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            float progress = payload.progress();
            context.server().execute(() -> {
                chargeProgress.put(id, progress);

                for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
                    if (!player.getUuid().equals(id)) {
                        ServerPlayNetworking.send(player, new PoseNetworking.ChargeSyncPayload(id, progress));
                    }
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(PoseNetworking.ThrowAnimPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            context.server().execute(() -> {
                for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
                    if (!player.getUuid().equals(id)) {
                        ServerPlayNetworking.send(player, new PoseNetworking.ThrowAnimPayload(id));
                    }
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(PoseNetworking.AnimStateSyncPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            int animState = payload.animStateOrdinal();
            context.server().execute(() -> {
                for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
                    if (!player.getUuid().equals(id)) {
                        ServerPlayNetworking.send(player, new PoseNetworking.AnimStateSyncPayload(id, animState));
                    }
                }
            });
        });
    }

    public static void registerClientReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(PoseNetworking.PoseSyncPayload.ID, (payload, context) -> {
            UUID id = payload.playerId();
            PoseState state = PoseState.values()[payload.poseOrdinal()];
            poseStates.put(id, state);
            context.client().execute(() -> {
                if (context.client().world != null) {
                    for (PlayerEntity player : context.client().world.getPlayers()) {
                        if (player.getUuid().equals(id)) {
                            CoopAnimationHandler.updatePlayerAnimation(player, state);
                            break;
                        }
                    }
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(
            PoseNetworking.ChargeSyncPayload.ID, (payload, context) -> chargeProgress.put(payload.playerId(), payload.progress())
        );
        ClientPlayNetworking.registerGlobalReceiver(
            PoseNetworking.ThrowAnimPayload.ID, (payload, context) -> ArmPoseTracker.throwAnimationStart.put(payload.playerId(), System.currentTimeMillis())
        );
        ClientPlayNetworking.registerGlobalReceiver(
            PoseNetworking.AnimStateSyncPayload.ID,
            (payload, context) -> {
                UUID id = payload.playerId();
                int animState = payload.animStateOrdinal();
                context.client()
                    .execute(
                        () -> {
                            if (context.client().world != null) {
                                ClientPlayerEntity localPlayer = context.client().player;
                                if (animState == 0) {
                                    ChargedDapClientHandler.cleanup(id);
                                    CoopAnimationHandler.cleanup(id);
                                    HighFiveClientHandler.cleanup(id);
                                    PushClientHandler.cleanup(id);
                                    MahitoClientHandler.cleanup(id);
                                    FallDapClientHandler.cleanup(id);
                                    ArmPoseTracker.cleanup(id);
                                }

                                PlayerEntity targetPlayer = null;

                                for (PlayerEntity player : context.client().world.getPlayers()) {
                                    if (player.getUuid().equals(id)) {
                                        targetPlayer = player;
                                        break;
                                    }
                                }

                                if (targetPlayer != null) {
                                    if (localPlayer != null) {
                                        boolean isPerfectDap = animState == 26;
                                        boolean isDapHit = animState == 10;
                                        boolean isParticipant = id.equals(localPlayer.getUuid()) || localPlayer.distanceTo(targetPlayer) < 10.0;
                                        if (isParticipant) {
                                            if (isPerfectDap) {
                                                long shakeDuration = CoopImpactHandler.PERFECT_DAP_SEQUENCE.length * 33L;
                                                CoopImpactHandler.start(CoopImpactHandler.PERFECT_DAP_SEQUENCE, 33L, true);
                                                CoopCameraShakeHandler.shake(1.2F, shakeDuration);
                                                CoopChromaHandler.start();
                                                CoopRadialBlurHandler.start();
                                                CoopSpeedLinesRenderer.start();
                                                CoopScreenSquishHandler.trigger();
                                                if (!localPlayer.getUuid().equals(targetPlayer.getUuid())) {
                                                    Vec3d mid = localPlayer.getPos()
                                                        .add(targetPlayer.getPos())
                                                        .multiply(0.5)
                                                        .add(0.0, 1.4, 0.0);
                                                    CoopShockwaveRenderer.start(mid);
                                                } else {
                                                    Vec3d front = localPlayer.getPos()
                                                        .add(localPlayer.getRotationVec(1.0F).multiply(1.5))
                                                        .add(0.0, 1.0, 0.0);
                                                    CoopShockwaveRenderer.start(front);
                                                }
                                            } else if (isDapHit && CoopMovesConfig.get().impactFramesEveryDap) {
                                                long shakeDuration = CoopImpactHandler.REGULAR_DAP_SEQUENCE.length * 33L;
                                                CoopImpactHandler.start(CoopImpactHandler.REGULAR_DAP_SEQUENCE, 33L, true);
                                                CoopCameraShakeHandler.shake(0.6F, shakeDuration);
                                                CoopChromaHandler.start();
                                                CoopRadialBlurHandler.start();
                                                CoopSpeedLinesRenderer.start();
                                                CoopScreenSquishHandler.trigger();
                                                if (!localPlayer.getUuid().equals(targetPlayer.getUuid())) {
                                                    Vec3d mid = localPlayer.getPos()
                                                        .add(targetPlayer.getPos())
                                                        .multiply(0.5)
                                                        .add(0.0, 1.4, 0.0);
                                                    CoopShockwaveRenderer.start(mid);
                                                } else {
                                                    Vec3d front = localPlayer.getPos()
                                                        .add(localPlayer.getRotationVec(1.0F).multiply(1.5))
                                                        .add(0.0, 1.0, 0.0);
                                                    CoopShockwaveRenderer.start(front);
                                                }
                                            }
                                        }
                                    }

                                    CoopAnimationHandler.setAnimStateFromNetwork(targetPlayer, animState);
                                }
                            }
                        }
                    );
            }
        );
    }

    public static void sendPoseToServer(UUID playerId, PoseState state) {
        ClientPlayNetworking.send(new PoseNetworking.PoseSyncPayload(playerId, state.ordinal()));
    }

    public static void sendChargeProgress(UUID playerId, float progress) {
        ClientPlayNetworking.send(new PoseNetworking.ChargeSyncPayload(playerId, progress));
    }

    public static void sendThrowAnimation(UUID playerId) {
        ClientPlayNetworking.send(new PoseNetworking.ThrowAnimPayload(playerId));
    }

    public static void sendAnimState(UUID playerId, int animStateOrdinal) {
        ClientPlayNetworking.send(new PoseNetworking.AnimStateSyncPayload(playerId, animStateOrdinal));
    }

    public static void broadcastPoseChange(MinecraftServer server, UUID playerId, PoseState state) {
        poseStates.put(playerId, state);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, new PoseNetworking.PoseSyncPayload(playerId, state.ordinal()));
        }
    }

    public static void broadcastAnimState(ServerPlayerEntity sourcePlayer, int animStateOrdinal) {
        MinecraftServer server = sourcePlayer.getServer();
        if (server != null) {
            UUID playerId = sourcePlayer.getUuid();
            PoseNetworking.AnimStateSyncPayload payload = new PoseNetworking.AnimStateSyncPayload(playerId, animStateOrdinal);

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    public record AnimStateSyncPayload(UUID playerId, int animStateOrdinal) implements CustomPayload {
        public static final Id<PoseNetworking.AnimStateSyncPayload> ID = new Id(new Identifier("cooptest", "anim_state_sync"));
        public static final PacketCodec<PacketByteBuf, PoseNetworking.AnimStateSyncPayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeUuid(payload.playerId);
            buf.writeInt(payload.animStateOrdinal);
        }, buf -> new PoseNetworking.AnimStateSyncPayload(buf.readUuid(), buf.readInt()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ChargeSyncPayload(UUID playerId, float progress) implements CustomPayload {
        public static final Id<PoseNetworking.ChargeSyncPayload> ID = new Id(new Identifier("cooptest", "charge_sync"));
        public static final PacketCodec<PacketByteBuf, PoseNetworking.ChargeSyncPayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeUuid(payload.playerId);
            buf.writeFloat(payload.progress);
        }, buf -> new PoseNetworking.ChargeSyncPayload(buf.readUuid(), buf.readFloat()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PoseSyncPayload(UUID playerId, int poseOrdinal) implements CustomPayload {
        public static final Id<PoseNetworking.PoseSyncPayload> ID = new Id(new Identifier("cooptest", "pose_sync"));
        public static final PacketCodec<PacketByteBuf, PoseNetworking.PoseSyncPayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeUuid(payload.playerId);
            buf.writeInt(payload.poseOrdinal);
        }, buf -> new PoseNetworking.PoseSyncPayload(buf.readUuid(), buf.readInt()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ThrowAnimPayload(UUID playerId) implements CustomPayload {
        public static final Id<PoseNetworking.ThrowAnimPayload> ID = new Id(new Identifier("cooptest", "throw_anim"));
        public static final PacketCodec<PacketByteBuf, PoseNetworking.ThrowAnimPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeUuid(payload.playerId), buf -> new PoseNetworking.ThrowAnimPayload(buf.readUuid())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
