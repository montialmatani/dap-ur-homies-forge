package com.cooptest.fabricshim.fabric.api.resource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.resource.ResourceType;
public final class ResourceManagerHelper {
    private static final ResourceManagerHelper CLIENT = new ResourceManagerHelper();
    private static final ResourceManagerHelper SERVER = new ResourceManagerHelper();
    public final List<ResourceReloader> listeners = new CopyOnWriteArrayList<>();
    public static ResourceManagerHelper get(ResourceType type) { return type == ResourceType.CLIENT_RESOURCES ? CLIENT : SERVER; }
    public void registerReloadListener(ResourceReloader listener) { listeners.add(listener); }
}
