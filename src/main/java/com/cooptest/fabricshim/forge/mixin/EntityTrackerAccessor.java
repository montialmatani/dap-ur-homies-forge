package com.cooptest.fabricshim.forge.mixin;

import java.util.Set;
import net.minecraft.server.world.EntityTrackingListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.server.world.ThreadedAnvilChunkStorage$EntityTracker")
public interface EntityTrackerAccessor {
    @Accessor("listeners")
    Set<EntityTrackingListener> coop$getListeners();
}
