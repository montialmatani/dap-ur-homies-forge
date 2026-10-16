package com.cooptest.meme;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public record SpinYeetImpactPayload() implements CustomPayload {
    public static final Id<SpinYeetImpactPayload> ID = new Id(new Identifier("cooptest", "spin_yeet_impact"));
    public static final PacketCodec<PacketByteBuf, SpinYeetImpactPayload> CODEC = PacketCodec.of((payload, buf) -> {}, buf -> new SpinYeetImpactPayload());

    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
