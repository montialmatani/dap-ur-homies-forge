package com.cooptest.fabricshim.fabric.api.event.player;
import com.cooptest.fabricshim.fabric.api.event.Event;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
@FunctionalInterface
public interface UseEntityCallback {
    Event<UseEntityCallback> EVENT = new Event<>();
    ActionResult interact(PlayerEntity player, World world, Hand hand, Entity entity, @Nullable EntityHitResult hitResult);
}
