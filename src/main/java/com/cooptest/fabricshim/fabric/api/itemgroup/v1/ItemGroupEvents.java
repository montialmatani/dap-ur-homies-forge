package com.cooptest.fabricshim.fabric.api.itemgroup.v1;
import com.cooptest.fabricshim.fabric.api.event.Event;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.RegistryKey;
public final class ItemGroupEvents {
    public static final Map<RegistryKey<ItemGroup>, Event<ModifyEntries>> EVENTS = new ConcurrentHashMap<>();
    public static Event<ModifyEntries> modifyEntriesEvent(RegistryKey<ItemGroup> key) { return EVENTS.computeIfAbsent(key, k -> new Event<>()); }
    @FunctionalInterface public interface ModifyEntries { void modifyEntries(FabricItemGroupEntries entries); }
}
