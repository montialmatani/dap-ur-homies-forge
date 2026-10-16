package com.cooptest;

import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;

public final class CoopSlowCleanup {
    private static final Identifier[] SLOW_IDS = new Identifier[]{
        new Identifier("testcoop", "sike_slow"),
        new Identifier("testcoop", "highfive_fast_slow"),
        new Identifier("testcoop", "highfive_pass_slow"),
        new Identifier("testcoop", "kick_slow"),
        new Identifier("testcoop", "dap_run_slow")
    };

    private CoopSlowCleanup() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> clear(handler.getPlayer()));
    }

    public static void clear(ServerPlayerEntity player) {
        if (player != null) {
            EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (attr != null) {
                for (Identifier id : SLOW_IDS) {
                    try {
                        attr.removeModifier(id);
                    } catch (Throwable var7) {
                    }
                }
            }
        }
    }
}
