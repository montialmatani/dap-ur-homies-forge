package com.cooptest.fabricshim.mc;
import net.minecraft.client.MinecraftClient;
/** Backport of 1.21's RenderTickCounter, backed by MinecraftClient's 1.20.1 tick delta. */
public interface RenderTickCounter {
    float getTickDelta(boolean ignoreFreeze);
    float getLastFrameDuration();
    default float getLastDuration() { return getLastFrameDuration(); }

    RenderTickCounter CURRENT = new RenderTickCounter() {
        public float getTickDelta(boolean ignoreFreeze) {
            MinecraftClient mc = MinecraftClient.getInstance();
            return mc.getTickDelta();
        }
        public float getLastFrameDuration() { return MinecraftClient.getInstance().getLastFrameDuration(); }
    };
    static RenderTickCounter current() { return CURRENT; }
    static RenderTickCounter fixed(float tickDelta) {
        return new RenderTickCounter() {
            public float getTickDelta(boolean ignoreFreeze) { return tickDelta; }
            public float getLastFrameDuration() { return MinecraftClient.getInstance().getLastFrameDuration(); }
        };
    }
}
