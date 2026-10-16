package com.cooptest.fabricshim.mc;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
/** Backport of the 1.20.5+ PacketCodec API (the subset this mod uses). */
public interface PacketCodec<B, V> {
    V decode(B buf);
    void encode(B buf, V value);

    @FunctionalInterface interface ValueFirstEncoder<B, V> { void encode(V value, B buf); }
    @FunctionalInterface interface PacketEncoder<B, V> { void encode(B buf, V value); }
    @FunctionalInterface interface PacketDecoder<B, V> { V decode(B buf); }
    @FunctionalInterface interface Function3<A, B, C, R> { R apply(A a, B b, C c); }
    @FunctionalInterface interface Function4<A, B, C, D, R> { R apply(A a, B b, C c, D d); }

    static <B, V> PacketCodec<B, V> of(ValueFirstEncoder<B, V> encoder, PacketDecoder<B, V> decoder) {
        return new PacketCodec<>() {
            public V decode(B buf) { return decoder.decode(buf); }
            public void encode(B buf, V value) { encoder.encode(value, buf); }
        };
    }
    static <B, V> PacketCodec<B, V> ofStatic(PacketEncoder<B, V> encoder, PacketDecoder<B, V> decoder) {
        return new PacketCodec<>() {
            public V decode(B buf) { return decoder.decode(buf); }
            public void encode(B buf, V value) { encoder.encode(buf, value); }
        };
    }
    static <B, V> PacketCodec<B, V> unit(V value) {
        return new PacketCodec<>() {
            public V decode(B buf) { return value; }
            public void encode(B buf, V v) { }
        };
    }
    static <B, C, T1> PacketCodec<B, C> tuple(PacketCodec<? super B, T1> c1, Function<C, T1> f1, Function<T1, C> to) {
        return ofStatic((buf, v) -> c1.encode(buf, f1.apply(v)), buf -> to.apply(c1.decode(buf)));
    }
    static <B, C, T1, T2> PacketCodec<B, C> tuple(PacketCodec<? super B, T1> c1, Function<C, T1> f1, PacketCodec<? super B, T2> c2, Function<C, T2> f2, BiFunction<T1, T2, C> to) {
        return ofStatic((buf, v) -> { c1.encode(buf, f1.apply(v)); c2.encode(buf, f2.apply(v)); },
                buf -> { T1 a = c1.decode(buf); T2 b = c2.decode(buf); return to.apply(a, b); });
    }
    static <B, C, T1, T2, T3> PacketCodec<B, C> tuple(PacketCodec<? super B, T1> c1, Function<C, T1> f1, PacketCodec<? super B, T2> c2, Function<C, T2> f2, PacketCodec<? super B, T3> c3, Function<C, T3> f3, Function3<T1, T2, T3, C> to) {
        return ofStatic((buf, v) -> { c1.encode(buf, f1.apply(v)); c2.encode(buf, f2.apply(v)); c3.encode(buf, f3.apply(v)); },
                buf -> { T1 a = c1.decode(buf); T2 b = c2.decode(buf); T3 c = c3.decode(buf); return to.apply(a, b, c); });
    }
    static <B, C, T1, T2, T3, T4> PacketCodec<B, C> tuple(PacketCodec<? super B, T1> c1, Function<C, T1> f1, PacketCodec<? super B, T2> c2, Function<C, T2> f2, PacketCodec<? super B, T3> c3, Function<C, T3> f3, PacketCodec<? super B, T4> c4, Function<C, T4> f4, Function4<T1, T2, T3, T4, C> to) {
        return ofStatic((buf, v) -> { c1.encode(buf, f1.apply(v)); c2.encode(buf, f2.apply(v)); c3.encode(buf, f3.apply(v)); c4.encode(buf, f4.apply(v)); },
                buf -> { T1 a = c1.decode(buf); T2 b = c2.decode(buf); T3 c = c3.decode(buf); T4 d = c4.decode(buf); return to.apply(a, b, c, d); });
    }
    default <O> PacketCodec<B, O> xmap(Function<? super V, ? extends O> to, Function<? super O, ? extends V> from) {
        PacketCodec<B, V> self = this;
        return new PacketCodec<>() {
            public O decode(B buf) { return to.apply(self.decode(buf)); }
            public void encode(B buf, O value) { self.encode(buf, from.apply(value)); }
        };
    }
    default <S extends B> PacketCodec<S, V> cast() { @SuppressWarnings("unchecked") PacketCodec<S, V> c = (PacketCodec<S, V>) this; return c; }
}
