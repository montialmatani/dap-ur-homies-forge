package com.cooptest.client.anim;

import java.util.function.Function;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

/** Keeps the original registration call shape; this port supports one animation layer per player. */
public final class PlayerAnimationFactory {
    public static final PlayerAnimationFactory ANIMATION_DATA_FACTORY = new PlayerAnimationFactory();

    Function<AbstractClientPlayerEntity, PlayerAnimationController> factory;
    Identifier layerId;

    public void registerFactory(Identifier id, int priority, Function<AbstractClientPlayerEntity, PlayerAnimationController> factory) {
        this.layerId = id;
        this.factory = factory;
    }
}
