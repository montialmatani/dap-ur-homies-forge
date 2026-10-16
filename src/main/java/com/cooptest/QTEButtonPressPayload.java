package com.cooptest;

import net.minecraft.util.Identifier;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.RegistryByteBuf;
import com.cooptest.fabricshim.mc.PacketCodecs;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public record QTEButtonPressPayload(String button) implements CustomPayload {
    public static final Identifier QTE_BUTTON_PRESS_ID = new Identifier("cooptest", "qte_button_press");
    public static final Id<QTEButtonPressPayload> ID = new Id(QTE_BUTTON_PRESS_ID);
    public static final PacketCodec<RegistryByteBuf, QTEButtonPressPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.STRING, QTEButtonPressPayload::button, QTEButtonPressPayload::new
    );

    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
