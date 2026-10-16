package com.cooptest.fabricshim.fabric.api.networking.v1;
import com.cooptest.fabricshim.forge.CoopNetwork;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.RegistryByteBuf;
public final class PayloadTypeRegistry {
    private static final PayloadTypeRegistry C2S = new PayloadTypeRegistry();
    private static final PayloadTypeRegistry S2C = new PayloadTypeRegistry();
    public static PayloadTypeRegistry playC2S() { return C2S; }
    public static PayloadTypeRegistry playS2C() { return S2C; }
    public <T extends CustomPayload> CustomPayload.Id<T> register(CustomPayload.Id<T> id, PacketCodec<? super RegistryByteBuf, T> codec) {
        CoopNetwork.registerCodec(id, codec);
        return id;
    }
}
