package com.cooptest.client;

import com.cooptest.GrabInputHandler;
import com.cooptest.HighFiveHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.cooptest.spin.client.HandSpinClientHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.util.Hand;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import com.cooptest.fabricshim.mc.RenderTickCounter;
import net.minecraft.client.util.InputUtil.Type;

public class HighFiveClientHandler {
    private static KeyBinding highFiveKey;
    private static boolean wasKeyPressed = false;
    private static long flashStartTime = 0L;
    private static int currentTier = 0;
    private static final Map<UUID, Boolean> raisedHands = new HashMap<>();
    private static final Map<UUID, Long> highFiveAnimStart = new HashMap<>();
    public static final long HIGH_FIVE_ANIM_DURATION = 1458L;
    private static long comboWindowStart = 0L;
    private static boolean inComboWindow = false;
    private static final long COMBO_WINDOW_MS = 750L;
    private static float lockedHugPitch = 0.0F;
    private static float lockedHugYaw = 0.0F;
    private static boolean hugCameraLocked = false;
    private static final Map<UUID, Boolean> frozenPlayers = new HashMap<>();

    public static void register() {
        highFiveKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.coopmoves.highfive", Type.field_1668, 72, "category.coopmoves"));
        ClientPlayNetworking.registerGlobalReceiver(HighFiveHandler.HandRaisedSyncPayload.ID, (payload, context) -> context.client().execute(() -> {
            raisedHands.put(payload.playerId(), payload.raised());
            MinecraftClient client = context.client();
            if (client.player != null && client.player.getUuid().equals(payload.playerId())) {
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(HighFiveHandler.HighFiveAnimPayload.ID, (payload, context) -> context.client().execute(() -> {
            UUID playerId = payload.playerId();
            int animState = payload.animState();
            MinecraftClient client = context.client();
            if (client.world != null) {
                boolean found = false;

                label24:
                for (PlayerEntity player : client.world.getPlayers()) {
                    if (player.getUuid().equals(playerId)) {
                        found = true;
                        switch (animState) {
                            case 1:
                                CoopAnimationHandler.playHighFiveStart(player);
                                break label24;
                            case 2:
                                CoopAnimationHandler.playHighFiveEnd(player);
                                break label24;
                            case 3:
                                CoopAnimationHandler.playHighFiveHit(player);
                                break label24;
                            case 4:
                                CoopAnimationHandler.playHighFiveSike(player);
                            default:
                                break label24;
                        }
                    }
                }

                if (!found) {
                }
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(
            HighFiveHandler.HighFiveSuccessPayload.ID,
            (payload, context) -> context.client()
                .execute(() -> onHighFiveSuccess(payload.x(), payload.y(), payload.z(), payload.player1(), payload.player2(), payload.tier()))
        );
        ClientPlayNetworking.registerGlobalReceiver(HighFiveHandler.ComboWindowPayload.ID, (payload, context) -> context.client().execute(() -> {
            comboWindowStart = System.currentTimeMillis();
            inComboWindow = true;
        }));
        ClientPlayNetworking.registerGlobalReceiver(HighFiveHandler.ComboWindowClosePayload.ID, (payload, context) -> context.client().execute(() -> {
            inComboWindow = false;
            MinecraftClient client = context.client();
            if (client.player != null) {
                UUID myId = client.player.getUuid();
                CoopAnimationHandler.syncAnimState(myId, CoopAnimationHandler.AnimState.NONE);
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(
            HighFiveHandler.FreezeStatePayload.ID,
            (payload, context) -> context.client().execute(() -> frozenPlayers.put(payload.playerId(), payload.frozen()))
        );
        ClientTickEvents.END_CLIENT_TICK
            .register(
                (EndTick)client -> {
                    if (client.player != null) {
                        if (GrabInputHandler.isLocalPlayerInGrab()) {
                            wasKeyPressed = highFiveKey.isPressed();
                        } else if (!HandSpinClientHandler.isLocalPlayerSpinning() && !HandSpinClientHandler.isLocalPlayerMonkeFlying()) {
                            UUID hugCheckId = client.player.getUuid();
                            boolean inHug = CoopAnimationHandler.isInHugAnim(hugCheckId);
                            boolean inHuddle = CoopAnimationHandler.isInHuddleAnim(hugCheckId);
                            if (!inHug && !inHuddle) {
                                hugCameraLocked = false;
                            } else {
                                if (!hugCameraLocked) {
                                    lockedHugPitch = client.player.getPitch();
                                    lockedHugYaw = client.player.getYaw();
                                    hugCameraLocked = true;
                                }

                                client.player.setPitch(lockedHugPitch);
                                client.player.setYaw(lockedHugYaw);
                                if (client.player.prevPitch != lockedHugPitch) {
                                    client.player.prevPitch = lockedHugPitch;
                                }

                                if (client.player.prevYaw != lockedHugYaw) {
                                    client.player.prevYaw = lockedHugYaw;
                                }
                            }

                            UUID myId = client.player.getUuid();
                            Long animStart = highFiveAnimStart.get(myId);
                            if (animStart != null) {
                                long elapsed = System.currentTimeMillis() - animStart;
                                if (elapsed > 1458L) {
                                    CoopAnimationHandler.AnimState currentState = CoopAnimationHandler.getAnimState(myId);
                                    if (currentState == CoopAnimationHandler.AnimState.HIGHFIVE_HIT) {
                                        highFiveAnimStart.remove(myId);
                                        raisedHands.put(myId, false);
                                        CoopAnimationHandler.syncAnimState(myId, CoopAnimationHandler.AnimState.NONE);
                                    } else if (currentState == CoopAnimationHandler.AnimState.HIGHFIVE_HIT_COMBO) {
                                        highFiveAnimStart.remove(myId);
                                    }
                                }
                            }

                            boolean isKeyPressed = highFiveKey.isPressed();
                            if (isKeyPressed && !wasKeyPressed) {
                                if (CoopAnimationHandler.isLocalPlayerCuffed()) {
                                    wasKeyPressed = true;
                                    return;
                                }

                                if (ChargedDapClientHandler.isPlayerFrozen() && !QTEClientHandler.isActive()) {
                                    wasKeyPressed = true;
                                    return;
                                }

                                if (ChargedDapClientHandler.isDapBadBlocking()) {
                                    wasKeyPressed = true;
                                    return;
                                }
                            }

                            if (isKeyPressed && !wasKeyPressed) {
                                if (FusionClientHandler.isQTEOpen()) {
                                    FusionClientHandler.handleQTEHPress();
                                    wasKeyPressed = true;
                                    return;
                                }

                                if (QTEClientHandler.isActive()) {
                                    if (!inComboWindow) {
                                        QTEClientHandler.handleKeyPress("H");
                                        ChargedDapClientHandler.postQTEBlockEndMs = System.currentTimeMillis() + 1000L;
                                        wasKeyPressed = true;
                                        return;
                                    }

                                    if ("H".equals(QTEClientHandler.getExpectedButton())) {
                                        QTEClientHandler.handleKeyPress("H");
                                        ChargedDapClientHandler.postQTEBlockEndMs = System.currentTimeMillis() + 1000L;
                                        wasKeyPressed = true;
                                        return;
                                    }
                                }
                            }

                            if (inComboWindow && isKeyPressed && !wasKeyPressed) {
                                ClientPlayNetworking.send(new HighFiveHandler.ComboRequestPayload());
                                inComboWindow = false;
                            }

                            if (inComboWindow && System.currentTimeMillis() - comboWindowStart > 750L) {
                                inComboWindow = false;
                            }

                            if (CoopAnimationHandler.isInDapAnim(client.player.getUuid())) {
                                wasKeyPressed = isKeyPressed;
                            } else {
                                if (isKeyPressed) {
                                    ClientPlayNetworking.send(new HighFiveHandler.HighFiveHoldPayload());
                                }

                                if (!inComboWindow && isKeyPressed && !wasKeyPressed) {
                                    if (ChargedDapClientHandler.isLocalPlayerCharging()) {
                                        client.player.sendMessage(Text.literal("§cCan't high five while charging dap!"), true);
                                    } else if (!client.player.getMainHandStack().isEmpty()) {
                                        client.player.sendMessage(Text.literal("§cHands must be empty for high five!"), true);
                                    } else {
                                        raisedHands.put(client.player.getUuid(), true);
                                        boolean inReadyForm = PoseNetworking.poseStates.getOrDefault(client.player.getUuid(), PoseState.NONE)
                                            == PoseState.GRAB_READY;
                                        boolean rightClickHeld = !inReadyForm && client.options.useKey.isPressed();
                                        if (rightClickHeld) {
                                            ClientPlayNetworking.send(new HighFiveHandler.SikeRequestPayload());
                                        } else {
                                            ClientPlayNetworking.send(new HighFiveHandler.HighFiveRequestPayload());
                                        }
                                    }
                                }

                                wasKeyPressed = isKeyPressed;
                            }
                        } else {
                            wasKeyPressed = highFiveKey.isPressed();
                        }
                    }
                }
            );
        HudRenderCallback.EVENT.register(HighFiveClientHandler::renderHUD);
    }

    private static void onHighFiveSuccess(double x, double y, double z, UUID player1, UUID player2, int tier) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            UUID myId = client.player.getUuid();
            if (myId.equals(player1) || myId.equals(player2)) {
                boolean now = raisedHands.getOrDefault(myId, false);
            }

            raisedHands.put(player1, false);
            raisedHands.put(player2, false);
            if (myId.equals(player1) || myId.equals(player2)) {
                boolean var14 = raisedHands.getOrDefault(myId, false);
            }

            long now = System.currentTimeMillis();
            highFiveAnimStart.put(player1, now);
            highFiveAnimStart.put(player2, now);
            if (myId.equals(player1) || myId.equals(player2)) {
                flashStartTime = now;
                currentTier = tier;
                client.player.swingHand(Hand.field_5808);

                String message = switch (tier) {
                    case 0 -> "§6 High Five!";
                    case 1 -> "§e Nice High Five! ";
                    case 2 -> "§a§l BIG HIGH FIVE! ";
                    case 3 -> "§c§l⚡ EXPLOSIVE HIGH FIVE! ⚡";
                    default -> "§6 High Five!";
                };
                client.player.sendMessage(Text.literal(message), true);
            }
        }
    }

    private static void renderHUD(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            long flashDuration = switch (currentTier) {
                case 0 -> 200L;
                case 1 -> 250L;
                case 2 -> 350L;
                case 3 -> 500L;
                default -> 200L;
            };
            long timeSinceFlash = System.currentTimeMillis() - flashStartTime;
            if (timeSinceFlash < flashDuration) {
                float progress = (float)timeSinceFlash / (float)flashDuration;

                int baseAlpha = switch (currentTier) {
                    case 0 -> 120;
                    case 1 -> 150;
                    case 2 -> 200;
                    case 3 -> 255;
                    default -> 120;
                };
                int alpha = (int)((1.0F - progress) * baseAlpha);

                int flashColor = switch (currentTier) {
                    case 0 -> alpha << 24 | 16777113;
                    case 1 -> alpha << 24 | 16763904;
                    case 2 -> alpha << 24 | 65416;
                    case 3 -> alpha << 24 | 16777215;
                    default -> alpha << 24 | 16777113;
                };
                context.fill(0, 0, screenWidth, screenHeight, flashColor);
            }

            UUID myId = client.player.getUuid();
            boolean handRaised = raisedHands.getOrDefault(myId, false);
            if (handRaised) {
                if (System.currentTimeMillis() % 2000L < 50L) {
                }

                String text = " Ready for High Five!";
                int textWidth = client.textRenderer.getWidth(text);
                int textX = (screenWidth - textWidth) / 2;
                int textY = screenHeight / 2 - 40;
                float pulse = (float)(Math.sin(System.currentTimeMillis() / 150.0) * 0.3 + 0.7);
                int alpha = (int)(pulse * 255.0F);
                int color = alpha << 24 | 16776960;
                context.drawText(client.textRenderer, text, textX, textY, color, true);
            }

            if (inComboWindow && !FusionClientHandler.isQTEOpen() && !FusionClientHandler.isGWindowOpen()) {
                long elapsed = System.currentTimeMillis() - comboWindowStart;
                long remaining = 750L - elapsed;
                if (remaining > 0L) {
                    String gKey = "G";
                    String hKey = "H";

                    try {
                        KeyBinding ck = ChargedDapClientHandler.getChargeKey();
                        if (ck != null) {
                            gKey = ck.getBoundKeyLocalizedText().getString().toUpperCase();
                        }
                    } catch (Exception var23) {
                    }

                    try {
                        if (highFiveKey != null) {
                            hKey = highFiveKey.getBoundKeyLocalizedText().getString().toUpperCase();
                        }
                    } catch (Exception var22) {
                    }

                    float pulse = (float)(Math.sin(System.currentTimeMillis() / 80.0) * 0.4 + 0.6);
                    int alpha = (int)(pulse * 255.0F);
                    int color = (float)elapsed / 750.0F < 0.5F ? alpha << 24 | 16776960 : alpha << 24 | 16729088;
                    String text = "[" + hKey + "] Combo";
                    int tw = client.textRenderer.getWidth(text);
                    context.drawText(client.textRenderer, text, (screenWidth - tw) / 2, screenHeight / 2 + 10, color, true);
                }
            }
        }
    }

    public static boolean isInComboWindow() {
        return inComboWindow;
    }

    public static void clearComboWindow() {
        inComboWindow = false;
    }

    public static boolean isInHugOpportunityWindow() {
        return inComboWindow;
    }

    public static boolean hasHandRaised(UUID playerId) {
        return raisedHands.getOrDefault(playerId, false);
    }

    public static KeyBinding getHighFiveKey() {
        return highFiveKey;
    }

    public static String getHighFiveBlockReason() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return "unknown";
        } else {
            UUID myId = client.player.getUuid();
            if (highFiveKey != null && highFiveKey.isPressed()) {
                return "H key pressed";
            } else if (raisedHands.getOrDefault(myId, false)) {
                return "raisedHands map = true";
            } else {
                Long animStart = highFiveAnimStart.get(myId);
                if (animStart != null) {
                    long elapsed = System.currentTimeMillis() - animStart;
                    if (elapsed <= 1458L) {
                        return "Animation playing (" + elapsed + "ms / 1458ms)";
                    }
                }

                CoopAnimationHandler.AnimState animState = CoopAnimationHandler.getAnimState(myId);
                return animState != CoopAnimationHandler.AnimState.HIGHFIVE_START && animState != CoopAnimationHandler.AnimState.HIGHFIVE_HIT
                    ? "unknown"
                    : "Animation state = " + animState;
            }
        }
    }

    public static boolean isLocalPlayerInHighFive() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return false;
        } else {
            UUID myId = client.player.getUuid();
            if (highFiveKey != null && highFiveKey.isPressed()) {
                return true;
            } else if (raisedHands.getOrDefault(myId, false)) {
                return true;
            } else {
                Long animStart = highFiveAnimStart.get(myId);
                if (animStart != null) {
                    long elapsed = System.currentTimeMillis() - animStart;
                    if (elapsed <= 1458L) {
                        return true;
                    }

                    highFiveAnimStart.remove(myId);
                    raisedHands.put(myId, false);
                }

                CoopAnimationHandler.AnimState animState = CoopAnimationHandler.getAnimState(myId);
                boolean inAnim = animState == CoopAnimationHandler.AnimState.HIGHFIVE_START || animState == CoopAnimationHandler.AnimState.HIGHFIVE_HIT;
                if (!inAnim && animState == CoopAnimationHandler.AnimState.HIGHFIVE_END) {
                    raisedHands.put(myId, false);
                    highFiveAnimStart.remove(myId);
                }

                return inAnim;
            }
        }
    }

    public static float getHighFiveAnimProgress(UUID playerId) {
        Long startTime = highFiveAnimStart.get(playerId);
        if (startTime == null) {
            return -1.0F;
        } else {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > 1458L) {
                highFiveAnimStart.remove(playerId);
                return -1.0F;
            } else {
                return (float)elapsed / 1458.0F;
            }
        }
    }

    public static void cleanup(UUID playerId) {
        raisedHands.remove(playerId);
        highFiveAnimStart.remove(playerId);
        frozenPlayers.remove(playerId);
    }

    public static boolean isLocalPlayerFrozen() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? false : frozenPlayers.getOrDefault(client.player.getUuid(), false);
    }

    public static boolean isPlayerFrozen(UUID playerId) {
        return frozenPlayers.getOrDefault(playerId, false);
    }
}
