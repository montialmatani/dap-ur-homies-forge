package com.cooptest.client;

import com.cooptest.client.anim.PlayerAnimationController;
import com.cooptest.client.anim.PlayerAnimationAccess;
import com.cooptest.client.anim.FirstPersonConfiguration;
import com.cooptest.client.anim.FirstPersonMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;

public class FirstPersonAnimationTest {
    private static boolean isActive = false;
    private static FirstPersonAnimationTest.InteractionType currentInteraction = FirstPersonAnimationTest.InteractionType.NONE;

    public static void init() {
    }

    public static void playGrabReady() {
        setFirstPersonMode(FirstPersonAnimationTest.InteractionType.GRAB_READY, true, true, true, true);
    }

    public static void playDapCharge() {
        setFirstPersonMode(FirstPersonAnimationTest.InteractionType.DAP_CHARGE, true, false, true, false);
    }

    public static void playDapHit() {
        setFirstPersonMode(FirstPersonAnimationTest.InteractionType.DAP_HIT, true, false, true, false);
    }

    public static void playPerfectDap() {
        setFirstPersonMode(FirstPersonAnimationTest.InteractionType.PERFECT_DAP, true, false, true, false);
    }

    public static void playHighFiveStart() {
        setFirstPersonMode(FirstPersonAnimationTest.InteractionType.HIGHFIVE_START, true, false, true, false);
    }

    public static void playHighFiveHit() {
        setFirstPersonMode(FirstPersonAnimationTest.InteractionType.HIGHFIVE_HIT, true, false, true, false);
    }

    public static void playHighFiveCombo() {
        setFirstPersonMode(FirstPersonAnimationTest.InteractionType.HIGHFIVE_COMBO, true, true, true, true);
    }

    public static void playHug() {
        setFirstPersonMode(FirstPersonAnimationTest.InteractionType.HUG, true, true, true, true);
    }

    public static void playPush() {
        setFirstPersonMode(FirstPersonAnimationTest.InteractionType.PUSH, true, true, true, true);
    }

    public static void playThrow() {
        setFirstPersonMode(FirstPersonAnimationTest.InteractionType.THROW, true, true, true, true);
    }

    public static void showBothHands() {
        setFirstPersonMode(FirstPersonAnimationTest.InteractionType.HUG, true, true, true, true);
    }

    public static void playKick() {
        setFirstPersonMode(FirstPersonAnimationTest.InteractionType.KICK, true, true, false, false);
    }

    public static void playDropKick() {
        setFirstPersonMode(FirstPersonAnimationTest.InteractionType.DROP_KICK, true, true, false, false);
    }

    public static void stop() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && isActive) {
            ClientPlayerEntity controller = client.player;
            if (controller instanceof AbstractClientPlayerEntity) {
                AbstractClientPlayerEntity clientPlayer = controller;

                try {
                    PlayerAnimationController controllerx = getController(clientPlayer);
                    if (controllerx != null) {
                        controllerx.setFirstPersonMode(FirstPersonMode.NONE);
                    }

                    isActive = false;
                    currentInteraction = FirstPersonAnimationTest.InteractionType.NONE;
                } catch (Exception var3) {
                }
            }
        }
    }

    private static void setFirstPersonMode(
        FirstPersonAnimationTest.InteractionType type, boolean showRightArm, boolean showLeftArm, boolean showRightItem, boolean showLeftItem
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            ClientPlayerEntity e = client.player;
            if (e instanceof AbstractClientPlayerEntity) {
                AbstractClientPlayerEntity clientPlayer = e;

                try {
                    PlayerAnimationController controller = getController(clientPlayer);
                    if (controller == null) {
                        return;
                    }

                    FirstPersonConfiguration config = new FirstPersonConfiguration(showRightArm, showLeftArm, showRightItem, showLeftItem);
                    controller.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
                    controller.setFirstPersonConfiguration(config);
                    isActive = true;
                    currentInteraction = type;
                } catch (Exception var9) {
                    var9.printStackTrace();
                }
            }
        }
    }

    private static PlayerAnimationController getController(AbstractClientPlayerEntity player) {
        return (PlayerAnimationController)PlayerAnimationAccess.getPlayerAnimationLayer(player, CoopAnimationHandler.ANIMATION_LAYER_ID);
    }

    public static void playSlap() {
        setFirstPersonMode(FirstPersonAnimationTest.InteractionType.SLAP, true, false, false, false);
    }

    public static boolean isActive() {
        return isActive;
    }

    public static FirstPersonAnimationTest.InteractionType getCurrentInteraction() {
        return currentInteraction;
    }

    public static enum InteractionType {
        NONE,
        GRAB_READY,
        DAP_CHARGE,
        DAP_HIT,
        PERFECT_DAP,
        HIGHFIVE_START,
        HIGHFIVE_HIT,
        HIGHFIVE_COMBO,
        HUG,
        PUSH,
        THROW,
        KICK,
        DROP_KICK,
        SLAP;
    }
}
