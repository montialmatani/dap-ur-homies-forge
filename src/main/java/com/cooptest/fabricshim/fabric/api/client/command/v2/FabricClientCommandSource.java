package com.cooptest.fabricshim.fabric.api.client.command.v2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
/** Implemented onto Forge's ClientCommandSourceStack by ClientCommandSourceMixin. */
public interface FabricClientCommandSource {
    void sendFeedback(Text message);
    void sendError(Text message);
    MinecraftClient getClient();
    ClientPlayerEntity getPlayer();
}
