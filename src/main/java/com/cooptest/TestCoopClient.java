package com.cooptest;

import com.cooptest.bros.client.BrosClientHandler;
import com.cooptest.client.BlackHoodClientHandler;
import com.cooptest.client.BullyDapClientHandler;
import com.cooptest.client.CatchClientHandler;
import com.cooptest.client.ChargedDapClientHandler;
import com.cooptest.client.ClapClientHandler;
import com.cooptest.client.CoopAnimationHandler;
import com.cooptest.client.CoopCameraShakeHandler;
import com.cooptest.client.CoopChromaHandler;
import com.cooptest.client.CoopClientSettings;
import com.cooptest.client.CoopImpactHandler;
import com.cooptest.client.CoopRadialBlurHandler;
import com.cooptest.client.CoopScreenSquishHandler;
import com.cooptest.client.CoopShockwaveRenderer;
import com.cooptest.client.CoopSpeedLinesRenderer;
import com.cooptest.client.DapFlairClientHandler;
import com.cooptest.client.DapHoldClientHandler;
import com.cooptest.client.DapRunClientHandler;
import com.cooptest.client.DuoPoseClientHandler;
import com.cooptest.client.FallDapClientHandler;
import com.cooptest.client.FireComboClientHandler;
import com.cooptest.client.FusionClientHandler;
import com.cooptest.client.GrabClientEffects;
import com.cooptest.client.GrabClientNetworking;
import com.cooptest.client.GroundPoundClientHandler;
import com.cooptest.client.HeavenDapClientHandler;
import com.cooptest.client.HighFiveClientHandler;
import com.cooptest.client.HighFivePassClientHandler;
import com.cooptest.client.HuddleClientHandler;
import com.cooptest.client.KickClientHandler;
import com.cooptest.client.MahitoClientHandler;
import com.cooptest.client.MarioJumpClientHandler;
import com.cooptest.client.MeteorStrikeClientHandler;
import com.cooptest.client.PushClientHandler;
import com.cooptest.client.QTEClientHandler;
import com.cooptest.client.SikeFollowUpClientHandler;
import com.cooptest.client.SitClientHandler;
import com.cooptest.client.SlapClientHandler;
import com.cooptest.client.SpearStrikeClientHandler;
import com.cooptest.client.SpinClientHandler;
import com.cooptest.client.StrongSlapClientHandler;
import com.cooptest.client.ThrowPowerHUD;
import com.cooptest.client.TrajectoryRenderer;
import com.cooptest.highfive.client.ReadyFiveClientHandler;
import com.cooptest.highfive.client.ReadyHugClientHandler;
import com.cooptest.highfive.client.ReadyPushClientHandler;
import com.cooptest.spin.client.HandSpinClientHandler;
import com.cooptest.spin.client.HandSpinRenderSync;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.UUID;
import com.cooptest.fabricshim.api.ClientModInitializer;
import com.cooptest.fabricshim.fabric.api.client.command.v2.ClientCommandManager;
import com.cooptest.fabricshim.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.Registries;
import org.lwjgl.glfw.GLFW;

public class TestCoopClient implements ClientModInitializer {
    public void onInitializeClient() {
        PoseNetworking.registerClientReceiver();
        GrabClientNetworking.register();
        GrabInputHandler.register();
        GrabClientEffects.register();
        ThrowPowerHUD.register();
        TrajectoryRenderer.register();
        HighFiveClientHandler.register();
        HighFivePassClientHandler.register();
        ReadyFiveClientHandler.register();
        ReadyHugClientHandler.register();
        ChargedDapClientHandler.register();
        PushClientHandler.register();
        ReadyPushClientHandler.register();
        CatchClientHandler.register();
        MahitoClientHandler.register();
        FallDapClientHandler.register();
        DapHoldClientHandler.register();
        MarioJumpClientHandler.register();
        BullyDapClientHandler.register();
        HeavenDapClientHandler.register();
        QTEClientHandler.registerReceivers();
        ClapClientHandler.register();
        DapFusionHandler.registerClientPayloads();
        FusionClientHandler.register();
        MeteorStrikeHandler.registerClientPayloads();
        MeteorStrikeClientHandler.register();
        KickClientHandler.register();
        SpinClientHandler.register();
        GroundPoundClientHandler.register();
        DapRunClientHandler.register();
        DapFlairClientHandler.register();
        FireComboClientHandler.register();
        SlapClientHandler.register();
        HuddleClientHandler.register();
        StrongSlapClientHandler.register();
        SikeFollowUpClientHandler.register();
        BrosClientHandler.register();
        SpearStrikeClientHandler.register();
        CoopClientSettings.register();
        DuoPoseClientHandler.register();
        HandSpinClientHandler.register();
        HandSpinRenderSync.register();
        ClientTickEvents.END_CLIENT_TICK
            .register(
                (EndTick)client -> {
                    if (client.player != null) {
                        if (client.player.getMainHandStack().isEmpty()) {
                            boolean hasTodo = client.player
                                .hasStatusEffect(com.cooptest.ModEffects.TODO);
                            if (hasTodo) {
                                long win = client.getWindow().getHandle();
                                boolean rmbHeld = GLFW.glfwGetMouseButton(win, 1) == 1;
                                ClientPlayNetworking.send(new ClapHandler.TodoRightClickPayload(rmbHeld));
                            }
                        }
                    }
                }
            );
        CoopAnimationHandler.register();
        SitClientHandler.register();
        BlackHoodClientHandler.register();
        ClientPlayNetworking.registerGlobalReceiver(
            NormalFacingDapHandler.FaceDapSessionPayload.ID,
            (payload, context) -> context.client().execute(() -> ChargedDapClientHandler.setInFaceDapSession(payload.active()))
        );
        ClientPlayNetworking.registerGlobalReceiver(
            NormalFacingDapHandler.FaceDapShakePayload.ID,
            (payload, context) -> context.client().execute(() -> CoopCameraShakeHandler.shake(payload.amount(), payload.durationMs()))
        );
        // Entity Model Features compat dropped in the Forge 1.20.1 port.

        ClientCommandRegistrationCallback.EVENT
            .register(
                (ClientCommandRegistrationCallback)(dispatcher, registryAccess) -> {
                    dispatcher.register(
                        (LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("impacttest")
                                .requires(src -> src.getPlayer() != null && src.getPlayer().hasPermissionLevel(2)))
                            .executes(ctx -> {
                                CoopImpactHandler.start(CoopImpactHandler.REGULAR_DAP_SEQUENCE, 33L, true);
                                CoopCameraShakeHandler.shake(0.6F, CoopImpactHandler.REGULAR_DAP_SEQUENCE.length * 33L);
                                return 1;
                            })
                    );
                    dispatcher.register(
                        (LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("impactperfect")
                                .requires(src -> src.getPlayer() != null && src.getPlayer().hasPermissionLevel(2)))
                            .executes(ctx -> {
                                CoopImpactHandler.start(CoopImpactHandler.PERFECT_DAP_SEQUENCE, 33L, true);
                                CoopCameraShakeHandler.shake(0.6F, CoopImpactHandler.REGULAR_DAP_SEQUENCE.length * 33L);
                                CoopChromaHandler.start();
                                CoopRadialBlurHandler.start();
                                CoopSpeedLinesRenderer.start();
                                CoopScreenSquishHandler.trigger();
                                MinecraftClient mc = MinecraftClient.getInstance();
                                if (mc.player != null) {
                                    CoopShockwaveRenderer.start(mc.player.getPos().add(0.0, 1.0, 0.0));
                                }

                                return 1;
                            })
                    );
                    dispatcher.register(
                        (LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("impactheaven")
                                .requires(src -> src.getPlayer() != null && src.getPlayer().hasPermissionLevel(2)))
                            .executes(ctx -> {
                                CoopImpactHandler.start(CoopImpactHandler.HEAVEN_DAP_SEQUENCE, 33L, false);
                                CoopCameraShakeHandler.shake(0.6F, CoopImpactHandler.REGULAR_DAP_SEQUENCE.length * 33L);
                                return 1;
                            })
                    );
                }
            );
    }
}
