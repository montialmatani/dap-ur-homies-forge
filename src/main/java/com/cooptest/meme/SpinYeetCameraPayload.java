package com.cooptest.meme;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public record SpinYeetCameraPayload(float rollDegrees) implements CustomPayload {
    public static final Id<SpinYeetCameraPayload> ID = new Id(new Identifier("cooptest", "spin_yeet_camera"));
    public static final PacketCodec<PacketByteBuf, SpinYeetCameraPayload> CODEC = PacketCodec.of(
        (payload, buf) -> buf.writeFloat(payload.rollDegrees()), buf -> new SpinYeetCameraPayload(buf.readFloat())
    );

    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
