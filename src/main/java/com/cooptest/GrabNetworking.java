package com.cooptest;

import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class GrabNetworking {
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(GrabNetworking.ThrowRequestPayload.ID, GrabNetworking.ThrowRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GrabNetworking.DropRequestPayload.ID, GrabNetworking.DropRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GrabNetworking.EscapeRequestPayload.ID, GrabNetworking.EscapeRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GrabNetworking.ElytraBoostRequestPayload.ID, GrabNetworking.ElytraBoostRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GrabNetworking.AirMovementPayload.ID, GrabNetworking.AirMovementPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GrabNetworking.ShieldTogglePayload.ID, GrabNetworking.ShieldTogglePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GrabNetworking.GrabStatePayload.ID, GrabNetworking.GrabStatePayload.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(GrabNetworking.ThrowRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            float power = payload.power();
            context.server().execute(() -> {
                if (GrabMechanic.isHolding(player)) {
                    GrabMechanic.tryThrow(player, power);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(GrabNetworking.DropRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (GrabMechanic.isHolding(player)) {
                    GrabMechanic.tryDrop(player);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(GrabNetworking.EscapeRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (GrabMechanic.isBeingHeld(player)) {
                    GrabMechanic.tryEscape(player);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(GrabNetworking.ElytraBoostRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> GrabMechanic.requestElytraBoost(player.getUuid()));
        });
        ServerPlayNetworking.registerGlobalReceiver(GrabNetworking.AirMovementPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> GrabMechanic.setAirMovementInput(player.getUuid(), payload.forward(), payload.strafe()));
        });
        ServerPlayNetworking.registerGlobalReceiver(GrabNetworking.ShieldTogglePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> GrabMechanic.toggleShieldMode(player));
        });
    }

    public static void broadcastGrabState(MinecraftServer server, UUID holderUuid, UUID heldUuid, boolean isStart) {
        GrabNetworking.GrabStatePayload payload = new GrabNetworking.GrabStatePayload(holderUuid, heldUuid, isStart);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public record AirMovementPayload(float forward, float strafe) implements CustomPayload {
        public static final Id<GrabNetworking.AirMovementPayload> ID = new Id(new Identifier("cooptest", "air_movement"));
        public static final PacketCodec<PacketByteBuf, GrabNetworking.AirMovementPayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeFloat(payload.forward);
            buf.writeFloat(payload.strafe);
        }, buf -> new GrabNetworking.AirMovementPayload(buf.readFloat(), buf.readFloat()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record DropRequestPayload() implements CustomPayload {
        public static final Id<GrabNetworking.DropRequestPayload> ID = new Id(new Identifier("cooptest", "drop_request"));
        public static final PacketCodec<PacketByteBuf, GrabNetworking.DropRequestPayload> CODEC = PacketCodec.unit(new GrabNetworking.DropRequestPayload());

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ElytraBoostRequestPayload() implements CustomPayload {
        public static final Id<GrabNetworking.ElytraBoostRequestPayload> ID = new Id(new Identifier("cooptest", "elytra_boost"));
        public static final PacketCodec<PacketByteBuf, GrabNetworking.ElytraBoostRequestPayload> CODEC = PacketCodec.unit(
            new GrabNetworking.ElytraBoostRequestPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record EscapeRequestPayload() implements CustomPayload {
        public static final Id<GrabNetworking.EscapeRequestPayload> ID = new Id(new Identifier("cooptest", "escape_request"));
        public static final PacketCodec<PacketByteBuf, GrabNetworking.EscapeRequestPayload> CODEC = PacketCodec.unit(
            new GrabNetworking.EscapeRequestPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record GrabStatePayload(UUID holderUuid, UUID heldUuid, boolean isStart) implements CustomPayload {
        public static final Id<GrabNetworking.GrabStatePayload> ID = new Id(new Identifier("cooptest", "grab_state"));
        public static final PacketCodec<PacketByteBuf, GrabNetworking.GrabStatePayload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeUuid(payload.holderUuid);
            buf.writeUuid(payload.heldUuid);
            buf.writeBoolean(payload.isStart);
        }, buf -> new GrabNetworking.GrabStatePayload(buf.readUuid(), buf.readUuid(), buf.readBoolean()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ShieldTogglePayload() implements CustomPayload {
        public static final Id<GrabNetworking.ShieldTogglePayload> ID = new Id(new Identifier("cooptest", "shield_toggle"));
        public static final PacketCodec<PacketByteBuf, GrabNetworking.ShieldTogglePayload> CODEC = PacketCodec.unit(new GrabNetworking.ShieldTogglePayload());

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ThrowRequestPayload(float power) implements CustomPayload {
        public static final Id<GrabNetworking.ThrowRequestPayload> ID = new Id(new Identifier("cooptest", "throw_request"));
        public static final PacketCodec<PacketByteBuf, GrabNetworking.ThrowRequestPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeFloat(payload.power), buf -> new GrabNetworking.ThrowRequestPayload(buf.readFloat())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
