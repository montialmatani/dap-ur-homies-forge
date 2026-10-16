package com.cooptest.fabricshim.fabric.api.entity.event.v1;
import com.cooptest.fabricshim.fabric.api.event.Event;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
public final class ServerEntityWorldChangeEvents {
    public static final Event<AfterPlayerChange> AFTER_PLAYER_CHANGE_WORLD = new Event<>();
    @FunctionalInterface public interface AfterPlayerChange { void afterChangeWorld(ServerPlayerEntity player, ServerWorld origin, ServerWorld destination); }
}
