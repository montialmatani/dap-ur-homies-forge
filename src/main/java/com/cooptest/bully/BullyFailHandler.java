package com.cooptest.bully;

import com.cooptest.ChargedDapHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.client.CoopAnimationHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.networking.v1.PlayerLookup;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;

public class BullyFailHandler {
    private static final double STAND_Y_OFFSET = -0.5;
    private static final Map<UUID, ArmorStandEntity> failStands = new HashMap<>();
    private static final Set<UUID> inFail = new HashSet<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            for (UUID id : new ArrayList<>(inFail)) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player == null) {
                    forceCleanup(id);
                } else if (player.isSneaking() || player.getVehicle() == null) {
                    exitFail(player);
                }
            }
        });
    }

    public static void enterFail(ServerPlayerEntity player, double peakHeight) {
        UUID id = player.getUuid();
        if (!inFail.contains(id)) {
            if (!BullyDapHandler.isInBullyDap(id)) {
                ServerWorld world = player.getServerWorld();
                Vec3d pos = player.getPos();
                ChargedDapHandler.chargeStartTime.remove(id);
                ChargedDapHandler.fireLevel.remove(id);
                ChargedDapHandler.ChargeSyncPayload cancel = new ChargedDapHandler.ChargeSyncPayload(id, 0.0F, 0.0F, false);

                for (ServerPlayerEntity pl : PlayerLookup.all(player.getServer())) {
                    ServerPlayNetworking.send(pl, cancel);
                }

                float extra = Math.max(0.0F, (float)peakHeight - 3.0F);
                if (extra > 0.0F) {
                    player.damage(world.getDamageSources().fall(), extra);
                }

                ArmorStandEntity stand = new ArmorStandEntity(EntityType.field_6131, world);
                stand.setPosition(pos.x, pos.y + -0.5, pos.z);
                stand.setInvisible(true);
                stand.setNoGravity(true);
                stand.setInvulnerable(true);
                stand.setSilent(true);
                stand.setYaw(player.getYaw());
                stand.setHeadYaw(player.getYaw());
                world.spawnEntity(stand);
                player.startRiding(stand, true);
                PoseNetworking.broadcastAnimState(player, CoopAnimationHandler.AnimState.BULLY_FAIL.ordinal());
                inFail.add(id);
                failStands.put(id, stand);
                player.sendMessage(Text.literal("§c§l⚡ Miss!  §rPress §nSHIFT§r to get up"), true);
            }
        }
    }

    private static void exitFail(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        inFail.remove(id);
        player.stopRiding();
        ArmorStandEntity stand = failStands.remove(id);
        if (stand != null && !stand.isRemoved()) {
            stand.discard();
        }

        PoseNetworking.broadcastAnimState(player, 0);
    }

    private static void forceCleanup(UUID id) {
        inFail.remove(id);
        ArmorStandEntity stand = failStands.remove(id);
        if (stand != null && !stand.isRemoved()) {
            stand.discard();
        }
    }

    public static void cleanup(UUID id) {
        forceCleanup(id);
    }

    public static boolean isInFail(UUID id) {
        return inFail.contains(id);
    }
}
