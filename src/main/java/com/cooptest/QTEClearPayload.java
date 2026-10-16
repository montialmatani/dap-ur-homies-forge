package com.cooptest;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public record QTEClearPayload(UUID playerId) implements CustomPayload {
    public static final Identifier QTE_CLEAR_ID = new Identifier("cooptest", "qte_clear");
    public static final Id<QTEClearPayload> ID = new Id(QTE_CLEAR_ID);
    public static final PacketCodec<PacketByteBuf, QTEClearPayload> CODEC = PacketCodec.of(
        (payload, buf) -> buf.writeUuid(payload.playerId), buf -> new QTEClearPayload(buf.readUuid())
    );

    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
