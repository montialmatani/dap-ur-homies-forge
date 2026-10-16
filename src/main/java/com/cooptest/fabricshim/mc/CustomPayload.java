package com.cooptest.fabricshim.mc;
import net.minecraft.util.Identifier;
/** Backport of 1.20.5+ CustomPayload: a payload knows its own id; the codec lives in CoopNetwork. */
public interface CustomPayload {
    Id<? extends CustomPayload> getId();
    static <T extends CustomPayload> Id<T> id(String id) { return new Id<>(new Identifier(id)); }
    record Id<T extends CustomPayload>(Identifier id) {
        @Override public String toString() { return id.toString(); }
    }
}
