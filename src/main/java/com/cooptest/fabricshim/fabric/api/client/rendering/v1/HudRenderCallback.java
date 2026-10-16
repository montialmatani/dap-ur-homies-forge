package com.cooptest.fabricshim.fabric.api.client.rendering.v1;
import com.cooptest.fabricshim.fabric.api.event.Event;
import com.cooptest.fabricshim.mc.RenderTickCounter;
import net.minecraft.client.gui.DrawContext;
@FunctionalInterface
public interface HudRenderCallback {
    Event<HudRenderCallback> EVENT = new Event<>();
    void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter);
}
