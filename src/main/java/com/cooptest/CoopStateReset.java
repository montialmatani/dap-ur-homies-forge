package com.cooptest;

import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;

public final class CoopStateReset {
    public static final boolean DEBUG_LOG = false;

    private CoopStateReset() {
    }

    public static void hardResetPose(MinecraftServer server, UUID id) {
        if (server != null && id != null) {
            PoseNetworking.poseStates.remove(id);
            PoseNetworking.chargeProgress.remove(id);
            PoseNetworking.PoseSyncPayload posePkt = new PoseNetworking.PoseSyncPayload(id, PoseState.NONE.ordinal());
            PoseNetworking.AnimStateSyncPayload animPkt = new PoseNetworking.AnimStateSyncPayload(id, 0);

            for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                try {
                    ServerPlayNetworking.send(other, posePkt);
                    ServerPlayNetworking.send(other, animPkt);
                } catch (Exception var7) {
                }
            }
        }
    }

    public static void safe(String label, Runnable step) {
        try {
            step.run();
        } catch (Exception var3) {
            System.err.println("[COOP-CLEANUP] step failed: " + label + " -> " + var3);
        }
    }
}
