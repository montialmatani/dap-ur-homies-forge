package com.cooptest.meme;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public record SpinYeetStartPayload(UUID grabberUUID, UUID grabbedUUID) implements CustomPayload {
    public static final Id<SpinYeetStartPayload> ID = new Id(new Identifier("cooptest", "spin_yeet_start"));
    public static final PacketCodec<PacketByteBuf, SpinYeetStartPayload> CODEC = PacketCodec.of((payload, buf) -> {
        buf.writeUuid(payload.grabberUUID());
        buf.writeUuid(payload.grabbedUUID());
    }, buf -> new SpinYeetStartPayload(buf.readUuid(), buf.readUuid()));

    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
