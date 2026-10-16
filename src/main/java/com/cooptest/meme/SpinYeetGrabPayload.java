package com.cooptest.meme;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public record SpinYeetGrabPayload(UUID targetUUID) implements CustomPayload {
    public static final Id<SpinYeetGrabPayload> ID = new Id(new Identifier("cooptest", "spin_yeet_grab"));
    public static final PacketCodec<PacketByteBuf, SpinYeetGrabPayload> CODEC = PacketCodec.of(
        (payload, buf) -> buf.writeUuid(payload.targetUUID()), buf -> new SpinYeetGrabPayload(buf.readUuid())
    );

    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
