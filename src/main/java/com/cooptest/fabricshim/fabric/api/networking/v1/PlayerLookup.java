package com.cooptest.fabricshim.fabric.api.networking.v1;
import com.cooptest.fabricshim.forge.mixin.EntityTrackerAccessor;
import com.cooptest.fabricshim.forge.mixin.ThreadedAnvilChunkStorageAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.EntityTrackingListener;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
public final class PlayerLookup {
    public static Collection<ServerPlayerEntity> all(MinecraftServer server) {
        return server == null ? Collections.emptyList() : Collections.unmodifiableList(server.getPlayerManager().getPlayerList());
    }
    public static Collection<ServerPlayerEntity> world(ServerWorld world) { return Collections.unmodifiableList(world.getPlayers()); }
    public static Collection<ServerPlayerEntity> tracking(Entity entity) {
        if (!(entity.getWorld() instanceof ServerWorld sw)) return Collections.emptyList();
        Object tracker = ((ThreadedAnvilChunkStorageAccessor) sw.getChunkManager().threadedAnvilChunkStorage).coop$getEntityTrackers().get(entity.getId());
        if (tracker == null) return Collections.emptyList();
        List<ServerPlayerEntity> out = new ArrayList<>();
        for (EntityTrackingListener l : ((EntityTrackerAccessor) tracker).coop$getListeners()) out.add(l.getPlayer());
        return out;
    }
    public static Collection<ServerPlayerEntity> tracking(ServerWorld world, ChunkPos pos) {
        return world.getChunkManager().threadedAnvilChunkStorage.getPlayersWatchingChunk(pos, false);
    }
    public static Collection<ServerPlayerEntity> tracking(ServerWorld world, BlockPos pos) { return tracking(world, new ChunkPos(pos)); }
    public static Collection<ServerPlayerEntity> around(ServerWorld world, Vec3d pos, double radius) {
        double r2 = radius * radius;
        List<ServerPlayerEntity> out = new ArrayList<>();
        for (ServerPlayerEntity p : world.getPlayers()) if (p.squaredDistanceTo(pos) <= r2) out.add(p);
        return out;
    }
    public static Collection<ServerPlayerEntity> around(ServerWorld world, BlockPos pos, double radius) { return around(world, Vec3d.ofCenter(pos), radius); }
}
