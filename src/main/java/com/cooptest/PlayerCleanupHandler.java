package com.cooptest;

import com.cooptest.bros.BrosHandler;
import com.cooptest.bully.BullyDapHandler;
import com.cooptest.bully.BullyFailHandler;
import com.cooptest.highfive.ReadyFiveHandler;
import com.cooptest.highfive.ReadyHugHandler;
import com.cooptest.highfive.ReadyPushHandler;
import com.cooptest.meme.SpinYeetHandler;
import com.cooptest.spin.HandSpinHandler;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AfterPlayerChange;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDeath;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;

public class PlayerCleanupHandler {
    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            runFullCleanup(server, player, player.getUuid(), "DISCONNECT");
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((AfterDeath)(entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                MinecraftServer server = player.getServer();
                if (server != null) {
                    runFullCleanup(server, player, player.getUuid(), "DEATH");
                }
            }
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((AfterPlayerChange)(player, origin, destination) -> {
            MinecraftServer server = player.getServer();
            if (server != null) {
                runFullCleanup(server, player, player.getUuid(), "DIMENSION");
            }
        });
    }

    private static void runFullCleanup(MinecraftServer server, ServerPlayerEntity player, UUID uuid, String reason) {
        CoopStateReset.safe("HighfiveDap", () -> HighfiveDapHandler.onPlayerLeave(uuid, server));
        CoopStateReset.safe("Bros", () -> BrosHandler.cleanup(server, uuid, "DISCONNECT".equals(reason)));
        CoopStateReset.safe("SlowCleanup", () -> CoopSlowCleanup.clear(player));
        CoopStateReset.safe("Spear", () -> SpearStrikeHandler.cleanup(uuid));
        CoopStateReset.safe("HypeStreak", () -> HighFiveStreakHandler.cleanup(uuid));
        CoopStateReset.safe("DapRunLegBoost", () -> DapRunHandler.clearLegBoost(uuid));
        CoopStateReset.safe("Grab", () -> GrabMechanic.fullCleanup(uuid));
        CoopStateReset.safe("HandSpin", () -> HandSpinHandler.cleanup(uuid, server));
        CoopStateReset.safe("HighFive", () -> HighFiveHandler.cleanup(uuid));
        CoopStateReset.safe("ReadyFive", () -> ReadyFiveHandler.cleanup(uuid));
        CoopStateReset.safe("ReadyHug", () -> ReadyHugHandler.cleanup(uuid));
        CoopStateReset.safe("ReadyPush", () -> ReadyPushHandler.cleanup(uuid));
        CoopStateReset.safe("ChargedDap", () -> ChargedDapHandler.cleanup(uuid));
        CoopStateReset.safe("DapFlair", () -> DapFlair.cleanup(uuid));
        CoopStateReset.safe("DapPositioning", () -> DapPositioning.cleanup(uuid));
        CoopStateReset.safe("DapRun", () -> DapRunHandler.cleanup(uuid));
        CoopStateReset.safe("NormalFacingDap", () -> NormalFacingDapHandler.cleanup(uuid));
        CoopStateReset.safe("PerfectDapCombo", () -> PerfectDapComboHandler.cancelCombo(uuid));
        CoopStateReset.safe("Pact", () -> PactHandler.cleanup(uuid));
        CoopStateReset.safe("QTEManager", () -> QTEManager.cancelQTE(uuid));
        CoopStateReset.safe("DapComboChain", () -> DapComboChain.cancelCombo(uuid));
        CoopStateReset.safe("MeteorStrike", () -> MeteorStrikeHandler.cleanup(uuid));
        CoopStateReset.safe("Huddle", () -> HuddleHandler.cleanup(uuid, server));
        if (!"DEATH".equals(reason)) {
            CoopStateReset.safe("RallyBeacon", () -> RallyBeaconHandler.cleanup(uuid));
        }

        CoopStateReset.safe("DuoPose", () -> DuoPoseHandler.onPlayerLeave(uuid));
        CoopStateReset.safe("Push immunity", () -> PushInteractionHandler.pushImmunity.remove(uuid));
        CoopStateReset.safe("Push legacy", () -> PushInteractionHandler.cleanup(uuid));
        CoopStateReset.safe("MahitoTroll", () -> MahitoTrollHandler.cleanup(uuid));
        CoopStateReset.safe("FallDap", () -> FallDapHandler.cleanup(uuid));
        CoopStateReset.safe("ArmPoseTracker", () -> ArmPoseTracker.cleanup(uuid));
        CoopStateReset.safe("MarioJump", () -> MarioJumpHandler.cleanup(uuid));
        CoopStateReset.safe("DivineFlame", () -> DivineFlamCombo.cleanup(uuid));
        CoopStateReset.safe("Kick", () -> KickHandler.cleanup(uuid));
        CoopStateReset.safe("Bonk", () -> BonkHandler.cleanup(uuid));
        CoopStateReset.safe("Sit", () -> SitHandler.cleanup(uuid));
        CoopStateReset.safe("StrongSlap", () -> StrongSlapHandler.onPlayerLeave(uuid));
        CoopStateReset.safe("SikeFollowUp", () -> SikeFollowUpHandler.onPlayerLeave(uuid, server));
        CoopStateReset.safe("AirSpin", () -> SpinHandler.cleanup(uuid));
        CoopStateReset.safe("GroundPound", () -> GroundPoundHandler.cleanup(uuid));
        CoopStateReset.safe("BullyDap", () -> BullyDapHandler.cleanup(uuid));
        CoopStateReset.safe("BullyFail", () -> BullyFailHandler.cleanup(uuid));
        if (player != null) {
            CoopStateReset.safe("SpinYeet", () -> SpinYeetHandler.onPlayerDisconnect(player));
        }

        CoopStateReset.safe("hardResetPose", () -> CoopStateReset.hardResetPose(server, uuid));
    }
}
