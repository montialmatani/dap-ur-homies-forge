package com.cooptest;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public record QTEWindowPayload(UUID playerId, String button, int stage, long windowStart, long windowEnd) implements CustomPayload {
    public static final Identifier QTE_WINDOW_ID = new Identifier("cooptest", "qte_window");
    public static final Id<QTEWindowPayload> ID = new Id(QTE_WINDOW_ID);
    public static final PacketCodec<PacketByteBuf, QTEWindowPayload> CODEC = PacketCodec.of((payload, buf) -> {
        buf.writeUuid(payload.playerId);
        buf.writeString(payload.button);
        buf.writeInt(payload.stage);
        buf.writeLong(payload.windowStart);
        buf.writeLong(payload.windowEnd);
    }, buf -> new QTEWindowPayload(buf.readUuid(), buf.readString(), buf.readInt(), buf.readLong(), buf.readLong()));

    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
