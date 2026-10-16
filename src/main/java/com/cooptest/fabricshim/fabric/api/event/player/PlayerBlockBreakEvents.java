package com.cooptest.fabricshim.fabric.api.event.player;
import com.cooptest.fabricshim.fabric.api.event.Event;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
public final class PlayerBlockBreakEvents {
    public static final Event<Before> BEFORE = new Event<>();
    @FunctionalInterface public interface Before { boolean beforeBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity); }
}
