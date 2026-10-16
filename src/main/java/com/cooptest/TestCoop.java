package com.cooptest;

import com.cooptest.bros.BrosHandler;
import com.cooptest.bully.BullyDapHandler;
import com.cooptest.bully.BullyFailHandler;
import com.cooptest.highfive.HighFiveNormalHandler;
import com.cooptest.highfive.ReadyFiveHandler;
import com.cooptest.highfive.ReadyHugHandler;
import com.cooptest.highfive.ReadyPushHandler;
import com.cooptest.meme.SpinYeetConfig;
import com.cooptest.meme.SpinYeetHandler;
import com.cooptest.spin.HandSpinArmorStandTest;
import com.cooptest.spin.HandSpinHandler;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.cooptest.fabricshim.api.ModInitializer;
import com.cooptest.fabricshim.fabric.api.command.v2.CommandRegistrationCallback;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.network.ServerPlayerEntity;

public class TestCoop implements ModInitializer {
    public void onInitialize() {
        try {
            CoopMovesConfig.load();
            CoopMovesConfig cfg = CoopMovesConfig.get();
            ModSounds.register();
            ModEffects.register();
            MahitoItems.register();
            PoseNetworking.registerPayloads();
            NormalFacingDapHandler.registerPayloads();
            SitHandler.registerPayloads();
            DuoPoseHandler.registerPayloads();
            PoseNetworking.registerServerReceiver();
            GrabNetworking.registerPayloads();
            HandSpinHandler.registerPayloads();
            HighFiveHandler.registerPayloads();
            HighFivePassHandler.registerPayloads();
            ReadyFiveHandler.registerPayloads();
            ReadyHugHandler.registerPayloads();
            HuddleHandler.registerPayloads();
            ChargedDapHandler.registerPayloads();
            DapFusionHandler.registerPayloads();
            DapPositioning.registerPayloads();
            DapFlair.registerPayloads();
            DapRunHandler.registerPayloads();
            MeteorStrikeHandler.registerPayloads();
            BullyDapHandler.registerPayloads();
            PushInteractionHandler.registerPayloads();
            ReadyPushHandler.registerPayloads();
            FallCatchHandler.registerPayloads();
            MarioJumpHandler.registerPayloads();
            HeavenDapPayloads.registerPayloads();
            ClapHandler.registerPayloads();
            SikeFollowUpHandler.registerPayloads();
            BrosHandler.registerPayloads();
            SpearStrikeHandler.registerPayloads();
            KickHandler.registerPayloads();
            DapHoldHandler.registerPayloads();
            SlapHandler.registerPayloads();
            StrongSlapHandler.registerPayloads();
            if (cfg.enableGrab) {
                GrabNetworking.registerServerReceivers();
                GrabMechanic.ShieldModePayload.register();
                GrabInteractionHandler.register();
                GrabMechanic.registerShieldDamageEvent();
                GrabActionLock.register();
                if (cfg.enableSpin) {
                    SpinHandler.register();
                }

                if (cfg.enableGroundPound) {
                    GroundPoundHandler.register();
                }

                HandSpinHandler.register();
            }

            if (cfg.enableHighFive) {
                HighFiveHandler.register();
                if (cfg.enableHighfiveDap) {
                    HighfiveDapHandler.register();
                }

                HighFivePassHandler.register();
                HighFiveNormalHandler.register();
                ReadyFiveHandler.register();
            }

            if (cfg.enableHighFiveHug) {
                ReadyHugHandler.register();
            }

            if (cfg.enableHighFive) {
                HuddleHandler.register();
            }

            if (cfg.enableDap) {
                ChargedDapHandler.register();
                DapSessionManager.register();
                DapFusionHandler.register();
                DapPositioning.register();
                DapFlair.register();
                DapRunHandler.register();
                MeteorStrikeHandler.register();
                PerfectDapComboHandler.register();
                FacingDapHandler.register();
                NormalFacingDapHandler.register();
                SitHandler.register();
                DuoPoseHandler.register();
                BullyDapHandler.register();
                BullyFailHandler.register();
            }

            if (cfg.enableDapHold) {
                DapHoldHandler.register();
            }

            if (cfg.enablePush) {
                ReadyPushHandler.register();
            }

            if (cfg.enableCatch) {
                FallCatchHandler.register();
            }

            if (cfg.enableMarioJump) {
                MarioJumpHandler.register();
            }

            if (cfg.enableHeavenDap) {
            }

            if (cfg.enableFallDap) {
                FallDapHandler.register();
            }

            if (cfg.enableClap) {
                ClapHandler.register();
            }

            MahitoTrollHandler.register();
            AnimationTickHandler.register();
            LaunchedPlayerTracker.register();
            CarryingSlowdown.register();
            SpinYeetConfig.load();
            SpinYeetHandler.registerPayloads();
            SpinYeetHandler.register();
            PactHandler.register();
            RallyBeaconHandler.register();
            FlairBarrierHandler.register();
            BrosHandler.register();
            SpearStrikeHandler.register();
            HighFiveStreakHandler.register();
            CoopSlowCleanup.register();
            PlayerCleanupHandler.register();
            BlackHoodNetworking.registerPayloads();
            BlackHoodHandler.register();
            CuffHandler.register();
            if (cfg.enableKick) {
                KickHandler.register();
            }

            if (cfg.enableSlap) {
                SlapHandler.register();
                StrongSlapHandler.register();
                SikeFollowUpHandler.register();
            }

            CommandRegistrationCallback.EVENT
                .register(
                    (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> {
                        dispatcher.register((LiteralArgumentBuilder)CommandManager.literal("sit").executes(SitHandler::executeSit));
                        StrongSlapCommand.register(dispatcher);
                        HeavenDapCommand.register(dispatcher);
                        dispatcher.register(
                            (LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("testpass").requires(src -> src.hasPermissionLevel(2)))
                                .then(
                                    CommandManager.argument("target", EntityArgumentType.player())
                                        .executes(
                                            ctx -> {
                                                ServerPlayerEntity p1 = ((ServerCommandSource)ctx.getSource()).getPlayerOrThrow();
                                                ServerPlayerEntity p2 = EntityArgumentType.getPlayer(ctx, "target");
                                                HighFivePassHandler.executePassHighFive(
                                                    p1,
                                                    p2,
                                                    HighFiveHandler.slotPoint(p2)
                                                        .subtract(HighFiveHandler.horizontalForward(p1).multiply(0.1))
                                                        .add(HighFiveHandler.horizontalLeft(p1).multiply(0.2)),
                                                    HighFiveHandler.slotPoint(p1)
                                                        .subtract(HighFiveHandler.horizontalForward(p2).multiply(0.1))
                                                        .add(HighFiveHandler.horizontalLeft(p2).multiply(0.2))
                                                );
                                                return 1;
                                            }
                                        )
                                )
                        );
                        dispatcher.register(
                            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                "testfive"
                                            )
                                            .requires(src -> src.hasPermissionLevel(2)))
                                        .executes(ctx -> triggerTestFive(((ServerCommandSource)ctx.getSource()).getPlayerOrThrow(), 0)))
                                    .then(CommandManager.literal("fast").executes(ctx -> triggerTestFive(((ServerCommandSource)ctx.getSource()).getPlayerOrThrow(), 1))))
                                .then(CommandManager.literal("mixed").executes(ctx -> triggerTestFive(((ServerCommandSource)ctx.getSource()).getPlayerOrThrow(), 2)))
                        );
                        dispatcher.register(
                            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                    "testspin"
                                                )
                                                .requires(src -> src.hasPermissionLevel(2)))
                                            .executes(ctx -> HandSpinArmorStandTest.start(((ServerCommandSource)ctx.getSource()).getPlayerOrThrow())))
                                        .then(
                                            ((LiteralArgumentBuilder)CommandManager.literal("cancel")
                                                    .executes(ctx -> HandSpinArmorStandTest.cancel(((ServerCommandSource)ctx.getSource()).getPlayerOrThrow(), false)))
                                                .then(
                                                    CommandManager.literal("slow")
                                                        .executes(ctx -> HandSpinArmorStandTest.cancel(((ServerCommandSource)ctx.getSource()).getPlayerOrThrow(), true))
                                                )
                                        ))
                                    .then(
                                        CommandManager.literal("damage")
                                            .executes(ctx -> HandSpinArmorStandTest.damage(((ServerCommandSource)ctx.getSource()).getPlayerOrThrow()))
                                    ))
                                .then(CommandManager.literal("stop").executes(ctx -> HandSpinArmorStandTest.stop(((ServerCommandSource)ctx.getSource()).getPlayerOrThrow())))
                        );
                    }
                );
            ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
                if (cfg.enableGrab) {
                    if (cfg.enableGroundPound) {
                        GroundPoundHandler.tick(server);
                    }

                    GrabMechanic.tick(server);
                    if (cfg.enableSpin) {
                        SpinHandler.tick(server);
                    }
                }

                if (cfg.enableDap) {
                    ChargedDapHandler.checkTickSpeedRestore(server);
                    QTEManager.tick(server);
                    if (cfg.enableDapCombo) {
                        DapComboChain.tick(server);
                    }
                }

                if (cfg.enablePush && server.getTicks() % 20 == 0) {
                    PushInteractionHandler.cleanupExpiredImmunity();
                }
            });
        } catch (Exception var2) {
            System.err.println("[TestCoop] CRASH during initialization!");
            var2.printStackTrace();
            throw new RuntimeException("TestCoop initialization failed", var2);
        }
    }

    private static int triggerTestFive(ServerPlayerEntity player, int mode) {
        Vec3d fwd = HighFiveHandler.horizontalForward(player);
        double spawnDist = 1.5;
        double sx = player.getX() + fwd.x * spawnDist;
        double sy = player.getY();
        double sz = player.getZ() + fwd.z * spawnDist;
        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, player.getServerWorld());
        stand.setPos(sx, sy, sz);
        float yaw = (float)Math.toDegrees(Math.atan2(-(sx - player.getX()), sz - player.getZ()));
        stand.setYaw(yaw);
        stand.setHeadYaw(yaw);
        stand.setBodyYaw(yaw);
        stand.setInvulnerable(true);
        player.getServerWorld().spawnEntity(stand);
        long now = System.currentTimeMillis();
        switch (mode) {
            case 1:
                PoseNetworking.broadcastAnimState(player, 112);
                HighFiveNormalHandler.executeFastSolo(player, now);
                break;
            case 2:
                PoseNetworking.broadcastAnimState(player, 112);
                HighFiveNormalHandler.executeMixedSoloFast(player, now);
                break;
            default:
                PoseNetworking.broadcastAnimState(player, 20);
                HighFiveNormalHandler.executeNormalSolo(player, now);
        }

        new Thread(() -> {
            try {
                Thread.sleep(2200L);
            } catch (InterruptedException var3x) {
            }

            player.getServer().execute(stand::discard);
        }).start();
        return 1;
    }
}
