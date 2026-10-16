package com.cooptest.fabricshim.mc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.util.Identifier;

/** 1.21 keys attribute modifiers by Identifier; 1.20.1 uses UUIDs. Derive a stable UUID from the id. */
public final class ModifierIds {
    public static UUID of(Identifier id) {
        return UUID.nameUUIDFromBytes(id.toString().getBytes(StandardCharsets.UTF_8));
    }
}
