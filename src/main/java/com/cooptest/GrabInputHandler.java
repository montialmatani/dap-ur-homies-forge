package com.cooptest;

import com.cooptest.bros.client.BrosClientHandler;
import com.cooptest.client.ChargedDapClientHandler;
import com.cooptest.client.CoopAnimationHandler;
import com.cooptest.client.FirstPersonAnimationTest;
import com.cooptest.client.GrabClientState;
import com.cooptest.client.GroundPoundClientHandler;
import com.cooptest.client.HighFiveClientHandler;
import com.cooptest.client.HighfiveDapClientHandler;
import com.cooptest.client.KickClientHandler;
import com.cooptest.client.SpearStrikeClientHandler;
import com.cooptest.client.SpinClientHandler;
import com.cooptest.spin.client.HandSpinClientHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ElytraItem;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil.Type;
import org.lwjgl.glfw.GLFW;

public class GrabInputHandler {
    private static final long MAX_CHARGE_TIME_MS = 1500L;
    private static final boolean BLOCK_CHARGE_IN_SHIELD_MODE = true;
    private static KeyBinding grabKey;
    private static KeyBinding throwKey;
    private static KeyBinding shieldKey;
    private static boolean wasGrabKeyPressed = false;
    private static boolean spearHoldSent = false;
    private static boolean wasThrowKeyPressed = false;
    private static boolean wasSneakPressed = false;
    private static boolean wasJumpPressed = false;
    private static boolean wasShieldKeyPressed = false;
    private static boolean wasYeetPressed = false;
    private static boolean wasShieldClickPressed = false;
    private static boolean wasOnGround = true;
    private static boolean spinWasActive = false;
    private static long spinStopTime = 0L;
    private static boolean isChargingThrow = false;
    private static long throwChargeStartTime = 0L;
    private static float lastSentChargeProgress = -1.0F;
    public static final Map<UUID, Boolean> clientShieldMode = new HashMap<>();

    public static boolean isLocalPlayerInGrabOrReady() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return false;
        } else {
            PoseState pose = PoseNetworking.poseStates.getOrDefault(client.player.getUuid(), PoseState.NONE);
            return pose == PoseState.GRAB_READY || pose == PoseState.GRAB_HOLDING || pose == PoseState.GRABBED;
        }
    }

    public static boolean isLocalPlayerInGrab() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return false;
        } else {
            PoseState pose = PoseNetworking.poseStates.getOrDefault(client.player.getUuid(), PoseState.NONE);
            return pose == PoseState.GRAB_HOLDING || pose == PoseState.GRABBED;
        }
    }

    private static void suppressVanillaClicks(MinecraftClient client) {
        client.options.useKey.setPressed(false);

        while (client.options.useKey.wasPressed()) {
        }

        client.options.attackKey.setPressed(false);

        while (client.options.attackKey.wasPressed()) {
        }
    }

    private static boolean isRightMouseHeld(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        return GLFW.glfwGetMouseButton(window, 1) == 1;
    }

    private static boolean isLeftMouseHeld(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        return GLFW.glfwGetMouseButton(window, 0) == 1;
    }

    private static boolean inCoopMove(UUID playerId) {
        return CoopAnimationHandler.isInHuddleAnim(playerId)
            || CoopAnimationHandler.isInDapOrHighFiveAnim(playerId)
            || ChargedDapClientHandler.isInFaceDapSession()
            || BrosClientHandler.blocksGrab()
            || HandSpinClientHandler.isLocalPlayerSpinning()
            || HandSpinClientHandler.isLocalPlayerMonkeFlying()
            || SpearStrikeClientHandler.isSteering();
    }

    public static void register() {
        grabKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.coopmoves.grab", Type.KEYSYM, 82, "category.coopmoves"));
        throwKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.coopmoves.throw", Type.KEYSYM, 84, "category.coopmoves"));
        shieldKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.coopmoves.shield", Type.KEYSYM, 86, "category.coopmoves"));
        ClientPlayNetworking.registerGlobalReceiver(GrabMechanic.ShieldModePayload.ID, (payload, context) -> context.client().execute(() -> {
            clientShieldMode.put(payload.holderId(), payload.enabled());
            if (!payload.enabled()) {
                clientShieldMode.remove(payload.holderId());
            }
        }));
        ClientTickEvents.END_CLIENT_TICK
            .register(
                (EndTick)client -> {
                    if (client.player != null) {
                        UUID playerId = client.player.getUuid();
                        PoseState pose = PoseNetworking.poseStates.getOrDefault(playerId, PoseState.NONE);
                        boolean isJumpPressed = client.options.jumpKey.isPressed();
                        if (isJumpPressed
                            && !wasJumpPressed
                            && pose == PoseState.GRABBED
                            && !client.player.hasVehicle()
                            && client.player.getEquippedStack(EquipmentSlot.CHEST).getItem() instanceof ElytraItem) {
                            ClientPlayNetworking.send(new GrabNetworking.ElytraBoostRequestPayload());
                        }

                        wasJumpPressed = isJumpPressed;
                        if (pose == PoseState.GRABBED && !client.player.hasVehicle()) {
                            float forward = 0.0F;
                            float strafe = 0.0F;
                            if (client.options.forwardKey.isPressed()) {
                                forward++;
                            }

                            if (client.options.backKey.isPressed()) {
                                forward--;
                            }

                            if (client.options.leftKey.isPressed()) {
                                strafe++;
                            }

                            if (client.options.rightKey.isPressed()) {
                                strafe--;
                            }

                            if (Math.abs(forward) > 0.01F || Math.abs(strafe) > 0.01F) {
                                ClientPlayNetworking.send(new GrabNetworking.AirMovementPayload(forward, strafe));
                            }
                        }

                        boolean isHolding = pose == PoseState.GRAB_HOLDING || GrabClientState.isHolding(playerId);
                        boolean isBeingHeld = pose == PoseState.GRABBED && client.player.hasVehicle() || GrabClientState.isBeingHeld(playerId);
                        if (isHolding || isBeingHeld) {
                            suppressVanillaClicks(client);
                        }

                        boolean spearHold = !client.player.isOnGround()
                            && !client.player.hasVehicle()
                            && client.currentScreen == null
                            && client.player.getMainHandStack().isEmpty()
                            && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), 1) == 1;
                        if (spearHold != spearHoldSent) {
                            ClientPlayNetworking.send(new SpearStrikeHandler.SpearHoldPayload(spearHold));
                            spearHoldSent = spearHold;
                        }

                        boolean isGrabKeyPressed = grabKey.isPressed();
                        boolean rBlocked = CoopAnimationHandler.isInDapOrHighFiveAnim(playerId)
                            || CoopAnimationHandler.isInHuddleAnim(playerId)
                            || ChargedDapClientHandler.isInFaceDapSession()
                            || BrosClientHandler.blocksGrab()
                            || SpearStrikeClientHandler.isSteering();
                        if (isGrabKeyPressed && !wasGrabKeyPressed && HighfiveDapClientHandler.isActive()) {
                            HighfiveDapClientHandler.requestCancel();
                            wasGrabKeyPressed = isGrabKeyPressed;
                            rBlocked = true;
                        }

                        if (isGrabKeyPressed && !wasGrabKeyPressed && !rBlocked && !CoopAnimationHandler.isLocalPlayerCuffed()) {
                            if (isHolding) {
                                ClientPlayNetworking.send(new GrabNetworking.DropRequestPayload());
                            } else if (pose == PoseState.GRAB_READY) {
                                if (!HandSpinClientHandler.isLocalPlayerSpinning()) {
                                    PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                                    PoseNetworking.sendPoseToServer(playerId, PoseState.NONE);
                                }
                            } else if (pose == PoseState.NONE && handsEmpty(client)) {
                                PoseNetworking.poseStates.put(playerId, PoseState.GRAB_READY);
                                PoseNetworking.sendPoseToServer(playerId, PoseState.GRAB_READY);
                            }
                        }

                        wasGrabKeyPressed = isGrabKeyPressed;
                        boolean isShieldKeyPressed = shieldKey.isPressed();
                        if (isShieldKeyPressed && !wasShieldKeyPressed) {
                            boolean blocked = isHolding
                                || isBeingHeld
                                || pose == PoseState.GRAB_READY
                                || pose == PoseState.PUSH_IDLE
                                || ChargedDapClientHandler.isLocalPlayerCharging()
                                || HighFiveClientHandler.isLocalPlayerInHighFive()
                                || CoopAnimationHandler.isLocalPlayerCuffed()
                                || inCoopMove(playerId);
                            if (!blocked && CoopMovesConfig.get().enableClap && client.player.getMainHandStack().isEmpty()) {
                                FirstPersonAnimationTest.showBothHands();
                                ClientPlayNetworking.send(new ClapHandler.ClapRequestPayload());
                            }
                        }

                        wasShieldKeyPressed = isShieldKeyPressed;
                        boolean isSneakPressed = client.options.sneakKey.isPressed();
                        boolean sneakJustPressed = isSneakPressed && !wasSneakPressed;
                        boolean sneakJustReleased = !isSneakPressed && wasSneakPressed;
                        boolean isThrownAirborne = pose == PoseState.GRABBED && !client.player.hasVehicle() && !client.player.isOnGround();
                        boolean isHelicopterMode = isThrownAirborne && SpinClientHandler.isLocalPlayerSpinning() && client.player.getFirstPassenger() != null;
                        if (isHelicopterMode && !GroundPoundClientHandler.isLocalPlayerDiving()) {
                            if (sneakJustReleased) {
                                spinWasActive = false;
                                ClientPlayNetworking.send(new SpinHandler.SpinStopPayload());
                                SpinClientHandler.forceStopLocalSpin();
                                ClientPlayNetworking.send(new GroundPoundHandler.GroundPoundStartPayload());
                            }
                        } else if (isThrownAirborne && !GroundPoundClientHandler.isLocalPlayerDiving()) {
                            if (sneakJustReleased && SpinClientHandler.isLocalPlayerSpinning()) {
                                spinWasActive = true;
                                spinStopTime = System.currentTimeMillis();
                                ClientPlayNetworking.send(new SpinHandler.SpinStopPayload());
                                SpinClientHandler.forceStopLocalSpin();
                            }

                            if (sneakJustPressed) {
                                if (SpinClientHandler.isLocalPlayerSpinning()) {
                                    spinWasActive = false;
                                    ClientPlayNetworking.send(new SpinHandler.SpinStopPayload());
                                    SpinClientHandler.forceStopLocalSpin();
                                    ClientPlayNetworking.send(new GroundPoundHandler.GroundPoundStartPayload());
                                } else if (spinWasActive && System.currentTimeMillis() - spinStopTime < 300L) {
                                    spinWasActive = false;
                                    ClientPlayNetworking.send(new GroundPoundHandler.GroundPoundStartPayload());
                                } else {
                                    spinWasActive = false;
                                    ClientPlayNetworking.send(new SpinHandler.SpinStartPayload());
                                }
                            }
                        } else if (!isThrownAirborne || !GroundPoundClientHandler.isLocalPlayerDiving()) {
                            if (sneakJustPressed && isBeingHeld) {
                                ClientPlayNetworking.send(new GrabNetworking.EscapeRequestPayload());
                            }

                            if (!isThrownAirborne) {
                                spinWasActive = false;
                            }
                        }

                        wasSneakPressed = isSneakPressed;
                        boolean isShieldClickPressed = isHolding && isLeftMouseHeld(client);
                        if (isShieldClickPressed && !wasShieldClickPressed) {
                            ClientPlayNetworking.send(new GrabNetworking.ShieldTogglePayload());
                        }

                        wasShieldClickPressed = isShieldClickPressed;
                        boolean isYeetPressed = isHolding && isRightMouseHeld(client);
                        if (clientShieldMode.getOrDefault(playerId, false)) {
                            isYeetPressed = false;
                        }

                        if (isHolding) {
                            KickClientHandler.cancelIfCharging();
                            if (isYeetPressed && !wasYeetPressed) {
                                isChargingThrow = true;
                                throwChargeStartTime = System.currentTimeMillis();
                                lastSentChargeProgress = 0.0F;
                                CoopAnimationHandler.startGrabCharge(client.player);
                            } else if (isYeetPressed && isChargingThrow) {
                                float currentProgress = getThrowChargeProgress();
                                GrabClientState.setChargeProgress(playerId, currentProgress);
                                if (Math.abs(currentProgress - lastSentChargeProgress) >= 0.1F) {
                                    PoseNetworking.sendChargeProgress(playerId, currentProgress);
                                    lastSentChargeProgress = currentProgress;
                                }
                            } else if (!isYeetPressed && wasYeetPressed && isChargingThrow) {
                                long chargeTime = System.currentTimeMillis() - throwChargeStartTime;
                                float power = Math.min(1.0F, (float)chargeTime / 1500.0F);
                                ClientPlayNetworking.send(new GrabNetworking.ThrowRequestPayload(power));
                                isChargingThrow = false;
                                CoopAnimationHandler.playThrowAnimation(client.player);
                                ArmPoseTracker.throwAnimationStart.put(playerId, System.currentTimeMillis());
                                PoseNetworking.sendThrowAnimation(playerId);
                                GrabClientState.setChargeProgress(playerId, 0.0F);
                                PoseNetworking.sendChargeProgress(playerId, -1.0F);
                                lastSentChargeProgress = -1.0F;
                            }
                        } else if (isChargingThrow) {
                            isChargingThrow = false;
                            GrabClientState.setChargeProgress(playerId, 0.0F);
                            PoseNetworking.sendChargeProgress(playerId, -1.0F);
                            lastSentChargeProgress = -1.0F;
                        }

                        wasYeetPressed = isYeetPressed;
                        boolean isThrowKeyPressed = throwKey.isPressed();
                        if (!isHolding) {
                            boolean canKick = !client.player.hasVehicle()
                                && pose == PoseState.NONE
                                && !CoopAnimationHandler.isLocalPlayerCuffed()
                                && !inCoopMove(playerId);
                            if (canKick) {
                                KickClientHandler.handleKickTick(client, isThrowKeyPressed, client.player.isSprinting());
                            } else {
                                KickClientHandler.cancelIfCharging();
                            }
                        }

                        wasThrowKeyPressed = isThrowKeyPressed;
                        if (pose == PoseState.GRAB_READY && !handsEmpty(client) && !HandSpinClientHandler.isLocalPlayerSpinning()) {
                            PoseNetworking.poseStates.put(playerId, PoseState.NONE);
                            PoseNetworking.sendPoseToServer(playerId, PoseState.NONE);
                        }
                    }
                }
            );
    }

    private static boolean handsEmpty(MinecraftClient client) {
        return client.player.getMainHandStack().isEmpty();
    }

    public static float getThrowChargeProgress() {
        if (!isChargingThrow) {
            return -1.0F;
        } else {
            long chargeTime = System.currentTimeMillis() - throwChargeStartTime;
            return Math.min(1.0F, (float)chargeTime / 1500.0F);
        }
    }

    public static float getChargeProgressFor(UUID playerId) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && client.player.getUuid().equals(playerId)
            ? getThrowChargeProgress()
            : PoseNetworking.chargeProgress.getOrDefault(playerId, -1.0F);
    }
}
