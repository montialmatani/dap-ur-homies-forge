package com.cooptest.fabricshim.mc;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketByteBuf;
public final class PacketCodecs {
    public static final PacketCodec<ByteBuf, Boolean> BOOL = PacketCodec.ofStatic(ByteBuf::writeBoolean, ByteBuf::readBoolean);
    public static final PacketCodec<ByteBuf, Integer> INTEGER = PacketCodec.ofStatic(ByteBuf::writeInt, ByteBuf::readInt);
    public static final PacketCodec<ByteBuf, Float> FLOAT = PacketCodec.ofStatic(ByteBuf::writeFloat, ByteBuf::readFloat);
    public static final PacketCodec<ByteBuf, Double> DOUBLE = PacketCodec.ofStatic(ByteBuf::writeDouble, ByteBuf::readDouble);
    public static final PacketCodec<ByteBuf, Long> LONG = PacketCodec.ofStatic(ByteBuf::writeLong, ByteBuf::readLong);
    public static final PacketCodec<ByteBuf, Integer> VAR_INT = PacketCodec.ofStatic((b, v) -> new PacketByteBuf(b).writeVarInt(v), b -> new PacketByteBuf(b).readVarInt());
    public static final PacketCodec<ByteBuf, String> STRING = PacketCodec.ofStatic((b, v) -> new PacketByteBuf(b).writeString(v), b -> new PacketByteBuf(b).readString());
}
