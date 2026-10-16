package com.cooptest.meme;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public record SpinYeetEndPayload(UUID grabbedUUID, boolean wasYeeted) implements CustomPayload {
    public static final Id<SpinYeetEndPayload> ID = new Id(new Identifier("cooptest", "spin_yeet_end"));
    public static final PacketCodec<PacketByteBuf, SpinYeetEndPayload> CODEC = PacketCodec.of((payload, buf) -> {
        buf.writeUuid(payload.grabbedUUID());
        buf.writeBoolean(payload.wasYeeted());
    }, buf -> new SpinYeetEndPayload(buf.readUuid(), buf.readBoolean()));

    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
