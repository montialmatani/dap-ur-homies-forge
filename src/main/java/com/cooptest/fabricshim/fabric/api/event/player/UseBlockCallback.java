package com.cooptest.fabricshim.fabric.api.event.player;
import com.cooptest.fabricshim.fabric.api.event.Event;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
@FunctionalInterface
public interface UseBlockCallback {
    Event<UseBlockCallback> EVENT = new Event<>();
    ActionResult interact(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult);
}
