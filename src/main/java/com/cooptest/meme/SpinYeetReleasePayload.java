package com.cooptest.meme;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public record SpinYeetReleasePayload() implements CustomPayload {
    public static final Id<SpinYeetReleasePayload> ID = new Id(new Identifier("cooptest", "spin_yeet_release"));
    public static final PacketCodec<PacketByteBuf, SpinYeetReleasePayload> CODEC = PacketCodec.of(
        (payload, buf) -> {}, buf -> new SpinYeetReleasePayload()
    );

    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
