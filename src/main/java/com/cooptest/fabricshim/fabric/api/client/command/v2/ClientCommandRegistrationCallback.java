package com.cooptest.fabricshim.fabric.api.client.command.v2;
import com.cooptest.fabricshim.fabric.api.event.Event;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
@FunctionalInterface
public interface ClientCommandRegistrationCallback {
    Event<ClientCommandRegistrationCallback> EVENT = new Event<>();
    void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess);
}
