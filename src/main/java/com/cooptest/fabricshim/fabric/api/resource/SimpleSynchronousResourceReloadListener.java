package com.cooptest.fabricshim.fabric.api.resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;
public interface SimpleSynchronousResourceReloadListener extends SynchronousResourceReloader {
    Identifier getFabricId();
    @Override void reload(ResourceManager manager);
}
