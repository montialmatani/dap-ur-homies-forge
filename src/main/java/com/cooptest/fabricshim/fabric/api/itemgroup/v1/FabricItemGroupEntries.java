package com.cooptest.fabricshim.fabric.api.itemgroup.v1;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
public interface FabricItemGroupEntries {
    void add(ItemStack stack);
    default void add(ItemConvertible item) { add(new ItemStack(item)); }
}
