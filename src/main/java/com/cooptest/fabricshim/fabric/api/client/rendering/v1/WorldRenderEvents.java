package com.cooptest.fabricshim.fabric.api.client.rendering.v1;
import com.cooptest.fabricshim.fabric.api.event.Event;
public final class WorldRenderEvents {
    public static final Event<Start> START = new Event<>();
    public static final Event<BeforeEntities> BEFORE_ENTITIES = new Event<>();
    public static final Event<AfterEntities> AFTER_ENTITIES = new Event<>();
    public static final Event<AfterTranslucent> AFTER_TRANSLUCENT = new Event<>();
    public static final Event<Last> LAST = new Event<>();
    public static final Event<End> END = new Event<>();
    @FunctionalInterface public interface Start { void onStart(WorldRenderContext context); }
    @FunctionalInterface public interface BeforeEntities { void beforeEntities(WorldRenderContext context); }
    @FunctionalInterface public interface AfterEntities { void afterEntities(WorldRenderContext context); }
    @FunctionalInterface public interface AfterTranslucent { void afterTranslucent(WorldRenderContext context); }
    @FunctionalInterface public interface Last { void onLast(WorldRenderContext context); }
    @FunctionalInterface public interface End { void onEnd(WorldRenderContext context); }
}
