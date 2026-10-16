package com.cooptest.fabricshim.mc;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketByteBuf;
public class RegistryByteBuf extends PacketByteBuf {
    public RegistryByteBuf(ByteBuf parent) { super(parent); }
}
