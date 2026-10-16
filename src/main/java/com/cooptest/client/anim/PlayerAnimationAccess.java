package com.cooptest.client.anim;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

public final class PlayerAnimationAccess {
    public static PlayerAnimationController getPlayerAnimationLayer(AbstractClientPlayerEntity player, Identifier layerId) {
        return CoopAnimLib.controller(player);
    }
}
