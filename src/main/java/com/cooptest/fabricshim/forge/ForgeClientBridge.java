package com.cooptest.fabricshim.forge;

import com.cooptest.fabricshim.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.HudRenderCallback;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.WorldRenderContext;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.WorldRenderEvents;
import com.cooptest.fabricshim.fabric.api.resource.ResourceManagerHelper;
import com.cooptest.fabricshim.mc.RenderTickCounter;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.resource.ResourceType;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Client-side half of the bridge. Only loaded on the physical client. */
public final class ForgeClientBridge {
    private ForgeClientBridge() {}

    public static void register(IEventBus modBus) {
        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener(ForgeClientBridge::onClientTick);
        bus.addListener(ForgeClientBridge::onHud);
        bus.addListener(ForgeClientBridge::onRenderStage);
        bus.addListener(ForgeClientBridge::onLoggingIn);
        bus.addListener(ForgeClientBridge::onLoggingOut);
        bus.addListener(ForgeClientBridge::onClientCommands);
        modBus.addListener(ForgeClientBridge::onKeyMappings);
        modBus.addListener(ForgeClientBridge::onClientReloadListeners);
    }

    private static void onClientTick(TickEvent.ClientTickEvent e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (e.phase == TickEvent.Phase.START) {
            for (ClientTickEvents.StartTick l : ClientTickEvents.START_CLIENT_TICK.listeners()) l.onStartTick(mc);
        } else {
            for (ClientTickEvents.EndTick l : ClientTickEvents.END_CLIENT_TICK.listeners()) l.onEndTick(mc);
        }
    }

    private static void onHud(RenderGuiEvent.Post e) {
        RenderTickCounter counter = RenderTickCounter.fixed(e.getPartialTick());
        for (HudRenderCallback l : HudRenderCallback.EVENT.listeners()) l.onHudRender(e.getGuiGraphics(), counter);
    }

    private static void onRenderStage(RenderLevelStageEvent e) {
        RenderLevelStageEvent.Stage stage = e.getStage();
        boolean start = stage == RenderLevelStageEvent.Stage.AFTER_SKY;
        boolean beforeEntities = stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS;
        boolean afterEntities = stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        boolean afterTranslucent = stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
        boolean last = stage == RenderLevelStageEvent.Stage.AFTER_WEATHER;
        boolean end = stage == RenderLevelStageEvent.Stage.AFTER_LEVEL;
        if (!(start || beforeEntities || afterEntities || afterTranslucent || last || end)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        WorldRenderContext ctx = new WorldRenderContext(e.getLevelRenderer(), e.getPoseStack(), RenderTickCounter.fixed(e.getPartialTick()),
                e.getCamera(), mc.gameRenderer, e.getProjectionMatrix(), mc.world,
                mc.getBufferBuilders().getEntityVertexConsumers(), e.getFrustum());
        if (start) for (WorldRenderEvents.Start l : WorldRenderEvents.START.listeners()) l.onStart(ctx);
        if (beforeEntities) for (WorldRenderEvents.BeforeEntities l : WorldRenderEvents.BEFORE_ENTITIES.listeners()) l.beforeEntities(ctx);
        if (afterEntities) for (WorldRenderEvents.AfterEntities l : WorldRenderEvents.AFTER_ENTITIES.listeners()) l.afterEntities(ctx);
        if (afterTranslucent) for (WorldRenderEvents.AfterTranslucent l : WorldRenderEvents.AFTER_TRANSLUCENT.listeners()) l.afterTranslucent(ctx);
        if (last) for (WorldRenderEvents.Last l : WorldRenderEvents.LAST.listeners()) l.onLast(ctx);
        if (end) for (WorldRenderEvents.End l : WorldRenderEvents.END.listeners()) l.onEnd(ctx);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        for (ClientPlayConnectionEvents.Join l : ClientPlayConnectionEvents.JOIN.listeners()) l.onPlayReady(handler, CoopNetwork::sendToServer, mc);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        for (ClientPlayConnectionEvents.Disconnect l : ClientPlayConnectionEvents.DISCONNECT.listeners()) l.onPlayDisconnect(handler, mc);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onClientCommands(RegisterClientCommandsEvent e) {
        // Forge's client command source (ClientCommandSourceStack) implements FabricClientCommandSource via mixin,
        // so the brigadier trees built against the Fabric type run unchanged.
        CommandDispatcher dispatcher = e.getDispatcher();
        for (ClientCommandRegistrationCallback l : ClientCommandRegistrationCallback.EVENT.listeners()) l.register(dispatcher, e.getBuildContext());
    }

    private static void onKeyMappings(RegisterKeyMappingsEvent e) {
        for (KeyBinding kb : KeyBindingHelper.PENDING) e.register(kb);
    }

    private static void onClientReloadListeners(RegisterClientReloadListenersEvent e) {
        for (ResourceReloader r : ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).listeners) e.registerReloadListener(r);
    }
}
