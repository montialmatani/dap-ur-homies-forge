package com.cooptest.fabricshim.forge.mixin;

import com.cooptest.fabricshim.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraftforge.client.ClientCommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;

/** Lets client commands written against Fabric's FabricClientCommandSource run on Forge's client source. */
@Mixin(ClientCommandSourceStack.class)
public abstract class ClientCommandSourceMixin implements FabricClientCommandSource {
    @Override
    public void sendFeedback(Text message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
    }

    @Override
    public MinecraftClient getClient() {
        return MinecraftClient.getInstance();
    }

    @Override
    public ClientPlayerEntity getPlayer() {
        return MinecraftClient.getInstance().player;
    }
}
