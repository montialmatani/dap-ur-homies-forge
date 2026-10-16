package com.cooptest.fabricshim.fabric.api.event.player;
import com.cooptest.fabricshim.fabric.api.event.Event;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
@FunctionalInterface
public interface UseItemCallback {
    Event<UseItemCallback> EVENT = new Event<>();
    TypedActionResult<ItemStack> interact(PlayerEntity player, World world, Hand hand);
}
