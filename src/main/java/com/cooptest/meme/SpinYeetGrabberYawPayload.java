package com.cooptest.meme;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public record SpinYeetGrabberYawPayload(float yawDelta) implements CustomPayload {
    public static final Id<SpinYeetGrabberYawPayload> ID = new Id(new Identifier("cooptest", "spin_yeet_grabber_yaw"));
    public static final PacketCodec<PacketByteBuf, SpinYeetGrabberYawPayload> CODEC = PacketCodec.of(
        (payload, buf) -> buf.writeFloat(payload.yawDelta()), buf -> new SpinYeetGrabberYawPayload(buf.readFloat())
    );

    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
