package com.cooptest.fabricshim.forge;

import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.fabric.api.networking.v1.PacketSender;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.RegistryByteBuf;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Carries every 1.21-style CustomPayload over a single Forge SimpleChannel.
 * Wire format: [identifier][payload bytes written by the payload's PacketCodec].
 * Handlers run on the main thread, matching Fabric API's behaviour on 1.20.5+.
 */
public final class CoopNetwork {
    private static final Logger LOGGER = LoggerFactory.getLogger("coopmoves/net");
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new Identifier("coopmoves", "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static final Map<Identifier, PacketCodec<? super RegistryByteBuf, ?>> CODECS = new ConcurrentHashMap<>();
    private static final Map<Identifier, ServerPlayNetworking.PlayPayloadHandler<?>> SERVER_HANDLERS = new ConcurrentHashMap<>();
    static final Map<Identifier, ClientPlayNetworking.PlayPayloadHandler<?>> CLIENT_HANDLERS = new ConcurrentHashMap<>();
    private static boolean initialized;

    /** Holds one decoded (or to-be-encoded) payload. */
    public record Envelope(CustomPayload payload) {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        CHANNEL.messageBuilder(Envelope.class, 0)
                .encoder(CoopNetwork::encode)
                .decoder(CoopNetwork::decode)
                .consumerMainThread(CoopNetwork::handle)
                .add();
    }

    public static <T extends CustomPayload> void registerCodec(CustomPayload.Id<T> id, PacketCodec<? super RegistryByteBuf, T> codec) {
        CODECS.putIfAbsent(id.id(), codec);
    }

    public static boolean hasCodec(CustomPayload.Id<?> id) {
        return CODECS.containsKey(id.id());
    }

    public static <T extends CustomPayload> boolean registerServerHandler(CustomPayload.Id<T> id, ServerPlayNetworking.PlayPayloadHandler<T> handler) {
        return SERVER_HANDLERS.putIfAbsent(id.id(), handler) == null;
    }

    public static <T extends CustomPayload> boolean registerClientHandler(CustomPayload.Id<T> id, ClientPlayNetworking.PlayPayloadHandler<T> handler) {
        return CLIENT_HANDLERS.putIfAbsent(id.id(), handler) == null;
    }

    public static void sendToPlayer(ServerPlayerEntity player, CustomPayload payload) {
        if (player == null || player.networkHandler == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Envelope(payload));
    }

    public static void sendToServer(CustomPayload payload) {
        CHANNEL.sendToServer(new Envelope(payload));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void encode(Envelope env, PacketByteBuf buf) {
        Identifier id = env.payload().getId().id();
        PacketCodec codec = CODECS.get(id);
        if (codec == null) throw new IllegalStateException("No codec registered for payload " + id);
        buf.writeIdentifier(id);
        codec.encode(new RegistryByteBuf(buf), env.payload());
    }

    private static Envelope decode(PacketByteBuf buf) {
        Identifier id = buf.readIdentifier();
        PacketCodec<? super RegistryByteBuf, ?> codec = CODECS.get(id);
        if (codec == null) {
            LOGGER.warn("Dropping unknown payload {}", id);
            buf.skipBytes(buf.readableBytes());
            return new Envelope(null);
        }
        return new Envelope((CustomPayload) codec.decode(new RegistryByteBuf(buf)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void handle(Envelope env, Supplier<NetworkEvent.Context> ctxSupplier) {
        CustomPayload payload = env.payload();
        if (payload == null) return;
        NetworkEvent.Context ctx = ctxSupplier.get();
        Identifier id = payload.getId().id();
        try {
            if (ctx.getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                ServerPlayerEntity sender = ctx.getSender();
                ServerPlayNetworking.PlayPayloadHandler handler = SERVER_HANDLERS.get(id);
                if (handler == null || sender == null) return;
                handler.receive(payload, new ServerContext(sender.getServer(), sender));
            } else {
                ClientNetDispatch.dispatch(payload);
            }
        } catch (Throwable t) {
            LOGGER.error("Error handling payload {}", id, t);
        }
    }

    record ServerContext(MinecraftServer server, ServerPlayerEntity player) implements ServerPlayNetworking.Context {
        @Override
        public PacketSender responseSender() {
            return p -> sendToPlayer(player, p);
        }
    }
}
