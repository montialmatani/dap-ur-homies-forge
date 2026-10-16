package com.cooptest;

import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public class HeavenDapPayloads {
    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(HeavenDapPayloads.HeavenDapStartPayload.ID, HeavenDapPayloads.HeavenDapStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HeavenDapPayloads.HeavenDapEndPayload.ID, HeavenDapPayloads.HeavenDapEndPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HeavenDapPayloads.RestoreVolumePayload.ID, HeavenDapPayloads.RestoreVolumePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HeavenDapPayloads.HeavenImpactPayload.ID, HeavenDapPayloads.HeavenImpactPayload.CODEC);
    }

    public record HeavenDapEndPayload() implements CustomPayload {
        public static final Id<HeavenDapPayloads.HeavenDapEndPayload> ID = new Id(new Identifier("testcoop", "heaven_dap_end"));
        public static final PacketCodec<PacketByteBuf, HeavenDapPayloads.HeavenDapEndPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {}, buf -> new HeavenDapPayloads.HeavenDapEndPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HeavenDapStartPayload() implements CustomPayload {
        public static final Id<HeavenDapPayloads.HeavenDapStartPayload> ID = new Id(new Identifier("testcoop", "heaven_dap_start"));
        public static final PacketCodec<PacketByteBuf, HeavenDapPayloads.HeavenDapStartPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {}, buf -> new HeavenDapPayloads.HeavenDapStartPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HeavenImpactPayload() implements CustomPayload {
        public static final Id<HeavenDapPayloads.HeavenImpactPayload> ID = new Id(new Identifier("testcoop", "heaven_impact"));
        public static final PacketCodec<PacketByteBuf, HeavenDapPayloads.HeavenImpactPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {}, buf -> new HeavenDapPayloads.HeavenImpactPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record RestoreVolumePayload() implements CustomPayload {
        public static final Id<HeavenDapPayloads.RestoreVolumePayload> ID = new Id(new Identifier("testcoop", "restore_volume"));
        public static final PacketCodec<PacketByteBuf, HeavenDapPayloads.RestoreVolumePayload> CODEC = PacketCodec.of(
            (payload, buf) -> {}, buf -> new HeavenDapPayloads.RestoreVolumePayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
