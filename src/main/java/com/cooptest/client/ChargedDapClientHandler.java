package com.cooptest.client;

import com.cooptest.ChargedDapHandler;
import com.cooptest.CoopMovesConfig;
import com.cooptest.NormalFacingDapHandler;
import com.cooptest.bros.client.BrosClientHandler;
import com.cooptest.spin.client.HandSpinClientHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.HudRenderCallback;
import com.cooptest.fabricshim.loader.api.FabricLoader;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import com.cooptest.fabricshim.mc.RenderTickCounter;
import net.minecraft.client.util.InputUtil.Type;

public class ChargedDapClientHandler {
    private static KeyBinding chargedDapKey;
    private static boolean wasKeyPressed = false;
    private static boolean isCharging = false;
    private static boolean wasFireCharging = false;
    private static boolean fireChargeComplete = false;
    private static float lastFireLevel = 0.0F;
    private static long chargeStartTime = 0L;
    private static long whiffCooldownEnd = 0L;
    public static long postQTEBlockEndMs = 0L;
    private static final Map<UUID, Float> otherPlayerCharges = new HashMap<>();
    private static final Map<UUID, Float> otherPlayerFire = new HashMap<>();
    private static final Map<UUID, Boolean> otherPlayerCharging = new HashMap<>();
    private static float localFireLevel = 0.0F;
    private static boolean isHeavenReady = false;
    private static long heavenReadyStartTime = 0L;
    private static long flashStartTime = 0L;
    private static int resultTier = 0;
    private static boolean resultPerfect = false;
    private static long perfectImpactStartTime = 0L;
    private static boolean isPerfectDapFrozen = false;
    private static boolean perfectImpactActive = false;
    private static int perfectDapImpactFrame = 0;
    private static long perfectDapImpactFrameStartTime = 0L;
    private static boolean facingDapImpactActive = false;
    private static long facingDapImpactStartMs = 0L;
    private static Identifier IMPAC7_TEXTURE;
    private static Identifier IMPAC8_TEXTURE;
    private static Identifier IMPAC9_TEXTURE;
    public static boolean dropKickImpactActive = false;
    private static long dropKickImpactStartMs = 0L;
    private static final long DK_FADE_IN = 50L;
    private static final long DK_FRAME1 = 150L;
    private static final long DK_FRAME2 = 230L;
    private static final long DK_FRAME3 = 310L;
    private static final long DK_FADE_OUT = 430L;
    private static long dapBadBlockEnd = 0L;
    private static boolean inFaceDapSession = false;
    private static float faceDapLockedYaw = Float.NaN;
    private static final long WHITE_FADE_DURATION = 30L;
    private static final long IMPACT1_END = 80L;
    private static final long IMPACT2_END = 130L;
    private static final long IMPACT3_END = 180L;
    private static Identifier IMPACT1_TEXTURE;
    private static Identifier IMPACT2_TEXTURE;
    private static Identifier IMPACT3_TEXTURE;
    private static Identifier PERFECT_FRAME0_TEXTURE;
    private static Identifier PERFECT_FRAME1_TEXTURE;
    private static Identifier PERFECT_FRAME2_TEXTURE;
    private static Identifier PERFECT_FRAME3_TEXTURE;
    private static KeyBinding fireDapComboKey;
    private static boolean fireDapWasKeyPressed = false;
    private static long fireDapComboWindowStart = 0L;
    private static boolean inFireDapComboWindow = false;
    private static final long FIRE_DAP_COMBO_WINDOW_MS = 2200L;
    private static final Map<UUID, Boolean> fireDapFrozenPlayers = new HashMap<>();
    private static final Set<UUID> fireDapFirstPersonPlayers = new HashSet<>();
    private static final long CHARGE_TIME_MS = 250L;
    private static final long FLASH_DURATION = 500L;

    public static boolean isDapBadBlocking() {
        return System.currentTimeMillis() < dapBadBlockEnd;
    }

    public static void triggerDapBadBlock() {
        dapBadBlockEnd = System.currentTimeMillis() + 1667L;
    }

    public static void setInFaceDapSession(boolean v) {
        inFaceDapSession = v;
        if (v) {
            faceDapLockedYaw = Float.NaN;
        }
    }

    public static boolean isFaceDapYawLocked() {
        return inFaceDapSession;
    }

    public static boolean isInFaceDapSession() {
        return inFaceDapSession;
    }

    public static void tickFaceDapYawLock() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            faceDapLockedYaw = Float.NaN;
        } else if (!inFaceDapSession) {
            faceDapLockedYaw = Float.NaN;
        } else {
            if (Float.isNaN(faceDapLockedYaw)) {
                faceDapLockedYaw = mc.player.getYaw();
            }

            mc.player.setYaw(faceDapLockedYaw);
            mc.player.setHeadYaw(faceDapLockedYaw);
            mc.player.setBodyYaw(faceDapLockedYaw);
        }
    }

    public static void triggerDropKickImpact() {
        dropKickImpactActive = true;
        dropKickImpactStartMs = System.currentTimeMillis();
    }

    public static boolean isFireDapJKeyHeld() {
        return fireDapComboKey != null && fireDapComboKey.isPressed();
    }

    public static KeyBinding getChargedDapKey() {
        return chargedDapKey;
    }

    public static void cancelChargeForBros() {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean wasCharging = isCharging;
        forceStopCharging();
        if (wasCharging && client.player != null) {
            CoopAnimationHandler.stopDapCharge(client.player);
        }
    }

    public static void forceStopCharging() {
        isCharging = false;
        localFireLevel = 0.0F;
        wasFireCharging = false;
        fireChargeComplete = false;
        lastFireLevel = 0.0F;
    }

    public static boolean isLocalPlayerCharging() {
        return isCharging;
    }

    public static float getLocalChargePercent() {
        if (isCharging && chargeStartTime != 0L) {
            long elapsed = System.currentTimeMillis() - chargeStartTime;
            return Math.min(1.0F, (float)elapsed / 250.0F);
        } else {
            return 0.0F;
        }
    }

    private static boolean isGolemDapBusy() {
        return FabricLoader.getInstance().getObjectShare().get("myhomie:golem_dap_active") instanceof BooleanSupplier b && b.getAsBoolean();
    }

    private static boolean tryGolemDapRelease(boolean fullBar) {
        return !fullBar
            ? false
            : FabricLoader.getInstance().getObjectShare().get("myhomie:golem_dap_try_release") instanceof BooleanSupplier b && b.getAsBoolean();
    }

    public static void register() {
        chargedDapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.coopmoves.dap", Type.KEYSYM, 71, "category.coopmoves"));
        FabricLoader.getInstance().getObjectShare().put("testcoop:play_golem_dap", (java.util.function.Consumer<UUID>)CoopAnimationHandler::playGolemDapByUuid);
        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.ChargeSyncPayload.ID, (payload, context) -> context.client().execute(() -> {
            UUID playerId = payload.playerId();
            MinecraftClient client = MinecraftClient.getInstance();
            if (payload.isCharging()) {
                otherPlayerCharges.put(playerId, payload.chargePercent());
                otherPlayerFire.put(playerId, payload.firePercent());
                otherPlayerCharging.put(playerId, true);
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    float newFireLevel = CoopMovesConfig.get().enableFireDap ? payload.firePercent() : 0.0F;
                    if (newFireLevel < localFireLevel - 0.1F && wasFireCharging) {
                        wasFireCharging = false;
                        fireChargeComplete = false;
                        CoopAnimationHandler.playDapChargeIdle(client.player);
                    }

                    localFireLevel = newFireLevel;
                }
            } else {
                otherPlayerCharges.remove(playerId);
                otherPlayerFire.remove(playerId);
                otherPlayerCharging.remove(playerId);
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                    localFireLevel = 0.0F;
                }
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.WhiffCooldownPayload.ID, (payload, context) -> context.client().execute(() -> {
            long duration = payload.cooldownDurationMs();
            whiffCooldownEnd = System.currentTimeMillis() + duration;
            isCharging = false;
            localFireLevel = 0.0F;
            wasFireCharging = false;
            fireChargeComplete = false;
            lastFireLevel = 0.0F;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                CoopAnimationHandler.stopDapCharge(client.player);
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.ImpactFramePayload.ID, (payload, context) -> context.client().execute(() -> {
            if (payload.grayscale()) {
                perfectImpactStartTime = System.currentTimeMillis();
                perfectImpactActive = true;
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(
            ChargedDapHandler.DapResultPayload.ID,
            (payload, context) -> context.client()
                .execute(() -> onDapResult(payload.x(), payload.y(), payload.z(), payload.player1(), payload.player2(), payload.tier(), payload.perfectHit()))
        );
        ClientPlayNetworking.registerGlobalReceiver(
            ChargedDapHandler.PerfectDapFreezePayload.ID, (payload, context) -> context.client().execute(() -> isPerfectDapFrozen = payload.frozen())
        );
        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.PerfectDapImpactFramePayload.ID, (payload, context) -> context.client().execute(() -> {
            if (isPerfectDapFrozen) {
                long shakeDuration = CoopImpactHandler.PERFECT_DAP_SEQUENCE.length * 33L;
                CoopImpactHandler.start(CoopImpactHandler.PERFECT_DAP_SEQUENCE, 33L, true);
                CoopCameraShakeHandler.shake(1.2F, shakeDuration);
                CoopChromaHandler.start();
                CoopRadialBlurHandler.start();
                CoopSpeedLinesRenderer.start();
                CoopScreenSquishHandler.trigger();
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.FacingDapImpactPayload.ID, (payload, context) -> context.client().execute(() -> {
            if (IMPAC7_TEXTURE == null) {
                IMPAC7_TEXTURE = new Identifier("testcoop", "textures/gui/impact/impac7.png");
                IMPAC8_TEXTURE = new Identifier("testcoop", "textures/gui/impact/impac8.png");
                IMPAC9_TEXTURE = new Identifier("testcoop", "textures/gui/impact/impac9.png");
            }

            facingDapImpactActive = true;
            facingDapImpactStartMs = System.currentTimeMillis();
        }));
        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.HeavenReadyPayload.ID, (payload, context) -> context.client().execute(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                if (client.player.getUuid().equals(payload.playerId())) {
                    if (payload.ready()) {
                        isHeavenReady = true;
                        heavenReadyStartTime = System.currentTimeMillis();
                    } else {
                        isHeavenReady = false;
                    }
                }
            }
        }));
        fireDapComboKey = new KeyBinding("key.coopmoves.unused_combo", Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), "category.coopmoves");
        ClientPlayNetworking.registerGlobalReceiver(
            ChargedDapHandler.FireDapWindowPayload.ID,
            (payload, context) -> context.client().execute(() -> FireComboClientHandler.onWindow(payload.open(), payload.armedMs(), payload.promptDelayMs()))
        );
        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.FireDapFreezePayload.ID, (payload, context) -> context.client().execute(() -> {
            fireDapFrozenPlayers.put(payload.playerId(), payload.frozen());
            CoopAnimationHandler.setFireDapLocked(payload.playerId(), payload.frozen());
        }));
        ClientPlayNetworking.registerGlobalReceiver(ChargedDapHandler.FireDapFirstPersonPayload.ID, (payload, context) -> context.client().execute(() -> {
            UUID playerId = payload.playerId();
            boolean show = payload.showBothHands();
            if (show) {
                fireDapFirstPersonPlayers.add(playerId);
                MinecraftClient client = context.client();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                }
            } else {
                fireDapFirstPersonPlayers.remove(playerId);
                MinecraftClient client = context.client();
                if (client.player != null && client.player.getUuid().equals(playerId)) {
                }
            }
        }));
        ClientTickEvents.END_CLIENT_TICK
            .register(
                (EndTick)client -> {
                    tickFaceDapYawLock();
                    if (client.player != null && CoopAnimationHandler.isInHuddleAnim(client.player.getUuid())) {
                        isCharging = false;
                        wasKeyPressed = chargedDapKey.isPressed();
                    } else if (client.player != null) {
                        if (HighfiveDapClientHandler.isActive()) {
                            wasKeyPressed = chargedDapKey.isPressed();
                        } else if (DapFlairClientHandler.isArmed()) {
                            wasKeyPressed = chargedDapKey.isPressed();
                        } else if (!FireComboClientHandler.isArmed() && !isLocalPlayerFireDapFrozen()) {
                            if (SikeFollowUpClientHandler.isArmed()) {
                                wasKeyPressed = chargedDapKey.isPressed();
                            } else if (BrosClientHandler.isEngaged()) {
                                isCharging = false;
                                wasKeyPressed = chargedDapKey.isPressed();
                            } else if (HandSpinClientHandler.isInputBlockedBySpin(client.player.getUuid())) {
                                isCharging = false;
                                wasKeyPressed = chargedDapKey.isPressed();
                            } else if (isGolemDapBusy()) {
                                wasKeyPressed = chargedDapKey.isPressed();
                            } else {
                                boolean isKeyPressed = chargedDapKey.isPressed();
                                boolean onCooldown = System.currentTimeMillis() < whiffCooldownEnd;
                                boolean inHighFive = HighFiveClientHandler.isLocalPlayerInHighFive();
                                boolean inBlocking = CoopAnimationHandler.isInBlockingState(client.player.getUuid());
                                if (isKeyPressed && !wasKeyPressed) {
                                    if (CoopAnimationHandler.isLocalPlayerCuffed()) {
                                        wasKeyPressed = true;
                                        return;
                                    }

                                    if (isPerfectDapFrozen && !QTEClientHandler.isActive()) {
                                        wasKeyPressed = true;
                                        return;
                                    }
                                }

                                if (isKeyPressed && !wasKeyPressed && QTEClientHandler.isActive()) {
                                    QTEClientHandler.handleKeyPress("G");
                                    wasKeyPressed = true;
                                } else {
                                    if (isKeyPressed && !wasKeyPressed && !inBlocking) {
                                        if (inHighFive || HighFiveClientHandler.isInComboWindow()) {
                                            wasKeyPressed = true;
                                            return;
                                        }

                                        if (inFaceDapSession) {
                                            wasKeyPressed = true;
                                            return;
                                        }

                                        if (isDapBadBlocking()) {
                                            wasKeyPressed = true;
                                            return;
                                        }

                                        if (CoopAnimationHandler.isInDapOrHighFiveAnim(client.player.getUuid())
                                            || CoopAnimationHandler.isInHighFiveStartAnim(client.player.getUuid())) {
                                            wasKeyPressed = true;
                                            return;
                                        }

                                        if (onCooldown) {
                                            long remaining = (whiffCooldownEnd - System.currentTimeMillis()) / 100L;
                                            client.player.sendMessage(Text.literal("§cDap on cooldown! " + remaining / 10.0 + "s"), true);
                                        } else if (!client.player.getMainHandStack().isEmpty()) {
                                            client.player.sendMessage(Text.literal("§cMain hand must be empty for charged dap!"), true);
                                        } else {
                                            isCharging = true;
                                            chargeStartTime = System.currentTimeMillis();
                                            localFireLevel = 0.0F;
                                            wasFireCharging = false;
                                            fireChargeComplete = false;
                                            lastFireLevel = 0.0F;
                                            ClientPlayNetworking.send(new ChargedDapHandler.ChargeStartPayload());
                                            CoopAnimationHandler.startDapCharge(client.player);
                                        }
                                    }

                                    if (isCharging && inBlocking) {
                                        isCharging = false;
                                        localFireLevel = 0.0F;
                                        wasFireCharging = false;
                                        fireChargeComplete = false;
                                        lastFireLevel = 0.0F;
                                        CoopAnimationHandler.stopDapCharge(client.player);
                                    }

                                    if (isCharging && !onCooldown && !inBlocking) {
                                        if (localFireLevel > 0.05F && !wasFireCharging) {
                                            wasFireCharging = true;
                                            fireChargeComplete = false;
                                            CoopAnimationHandler.startFireDapCharge(client.player);
                                        }

                                        if (wasFireCharging && localFireLevel >= 0.99F && !fireChargeComplete) {
                                            fireChargeComplete = true;
                                            CoopAnimationHandler.playFireDapChargeIdle(client.player);
                                        }

                                        if (wasFireCharging && localFireLevel < 0.05F) {
                                            wasFireCharging = false;
                                            fireChargeComplete = false;
                                            CoopAnimationHandler.playDapChargeIdle(client.player);
                                        }

                                        lastFireLevel = localFireLevel;
                                    }

                                    if (isCharging && onCooldown) {
                                        isCharging = false;
                                        localFireLevel = 0.0F;
                                        wasFireCharging = false;
                                        fireChargeComplete = false;
                                        lastFireLevel = 0.0F;
                                        CoopAnimationHandler.stopDapCharge(client.player);
                                    }

                                    if (!isKeyPressed && wasKeyPressed && isCharging) {
                                        boolean golemFullBar = System.currentTimeMillis() - chargeStartTime >= 250L;
                                        if (tryGolemDapRelease(golemFullBar)) {
                                            isCharging = false;
                                            localFireLevel = 0.0F;
                                            wasFireCharging = false;
                                            fireChargeComplete = false;
                                            lastFireLevel = 0.0F;
                                            CoopAnimationHandler.playDapChargeIdle(client.player);
                                            wasKeyPressed = isKeyPressed;
                                            return;
                                        }

                                        isCharging = false;
                                        localFireLevel = 0.0F;
                                        wasFireCharging = false;
                                        fireChargeComplete = false;
                                        lastFireLevel = 0.0F;
                                        ClientPlayNetworking.send(new ChargedDapHandler.ChargeReleasePayload());
                                    }

                                    if (isKeyPressed && inFaceDapSession) {
                                        ClientPlayNetworking.send(new NormalFacingDapHandler.DapLoopHoldPayload());
                                    }

                                    wasKeyPressed = isKeyPressed;
                                }
                            }
                        } else {
                            if (isCharging) {
                                isCharging = false;
                                localFireLevel = 0.0F;
                                wasFireCharging = false;
                                fireChargeComplete = false;
                                lastFireLevel = 0.0F;
                            }

                            wasKeyPressed = chargedDapKey.isPressed();
                        }
                    }
                }
            );
        HudRenderCallback.EVENT.register(ChargedDapClientHandler::renderHUD);
    }

    private static void onDapResult(double x, double y, double z, UUID player1, UUID player2, int tier, boolean perfectHit) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            UUID myId = client.player.getUuid();
            boolean iAmInDap = myId.equals(player1) || myId.equals(player2);
            isCharging = false;
            localFireLevel = 0.0F;
            wasFireCharging = false;
            fireChargeComplete = false;
            lastFireLevel = 0.0F;
            otherPlayerCharges.clear();
            otherPlayerFire.clear();
            otherPlayerCharging.clear();
            if (iAmInDap) {
                CoopAnimationHandler.stopDapChargeLocalOnly(client.player);
                flashStartTime = System.currentTimeMillis();
                resultTier = tier;
                resultPerfect = perfectHit;
            }

            spawnTierParticles(client, x, y, z, tier, perfectHit);
        }
    }

    private static void spawnTierParticles(MinecraftClient client, double x, double y, double z, int tier, boolean perfect) {
        if (client.world != null) {
            ParticleEffect particle;
            int particleCount;
            switch (tier) {
                case 0:
                    particle = ParticleTypes.SMOKE;
                    particleCount = 5;
                    break;
                case 1:
                    particle = ParticleTypes.CRIT;
                    particleCount = 10;
                    break;
                case 2:
                    particle = ParticleTypes.HAPPY_VILLAGER;
                    particleCount = 15;
                    break;
                case 3:
                    particle = ParticleTypes.ENCHANT;
                    particleCount = 20;
                    break;
                case 4:
                    particle = ParticleTypes.TOTEM_OF_UNDYING;
                    particleCount = 25;
                    break;
                case 5:
                    particle = ParticleTypes.FLAME;
                    particleCount = 30;
                    break;
                default:
                    particle = ParticleTypes.CRIT;
                    particleCount = 5;
            }

            for (int i = 0; i < particleCount; i++) {
                double offsetX = (Math.random() - 0.5) * 0.5;
                double offsetY = (Math.random() - 0.5) * 0.5;
                double offsetZ = (Math.random() - 0.5) * 0.5;
                double velX = (Math.random() - 0.5) * 0.3;
                double velY = Math.random() * 0.2;
                double velZ = (Math.random() - 0.5) * 0.3;
                client.world.addParticle(particle, x + offsetX, y + offsetY, z + offsetZ, velX, velY, velZ);
            }

            if (perfect) {
                for (int i = 0; i < 8; i++) {
                    double angle = i / 8.0 * Math.PI * 2.0;
                    double offsetX = Math.cos(angle) * 0.3;
                    double offsetZ = Math.sin(angle) * 0.3;
                    client.world.addParticle(ParticleTypes.ENCHANT, x + offsetX, y + 0.5, z + offsetZ, 0.0, 0.1, 0.0);
                }
            }
        }
    }

    private static void renderHUD(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            if (!inFaceDapSession && !CoopAnimationHandler.isInHuddleAnim(client.player.getUuid())) {
                int screenWidth = client.getWindow().getScaledWidth();
                int screenHeight = client.getWindow().getScaledHeight();
                long timeSinceFlash = System.currentTimeMillis() - flashStartTime;
                if (timeSinceFlash < 500L) {
                    float progress = (float)timeSinceFlash / 500.0F;

                    int baseAlpha = switch (resultTier) {
                        case 0 -> 30;
                        case 1 -> 40;
                        case 2 -> 50;
                        case 3 -> 60;
                        case 4 -> 80;
                        case 5 -> 100;
                        default -> 30;
                    };
                    if (resultPerfect) {
                        baseAlpha = Math.min(baseAlpha + 40, 140);
                    }

                    int alpha = (int)((1.0F - progress) * baseAlpha);

                    int flashColor = switch (resultTier) {
                        case 0 -> alpha << 24 | 8947848;
                        case 1 -> alpha << 24 | 16776960;
                        case 2 -> alpha << 24 | 0xFF00;
                        case 3 -> alpha << 24 | 16755200;
                        case 4 -> alpha << 24 | 16711935;
                        case 5 -> alpha << 24 | 16729088;
                        default -> alpha << 24 | 16777215;
                    };
                    context.fill(0, 0, screenWidth, screenHeight, flashColor);
                }

                if (perfectImpactActive) {
                    long elapsed = System.currentTimeMillis() - perfectImpactStartTime;
                    if (elapsed < 30L) {
                        float fadeProgress = (float)elapsed / 30.0F;
                        int alpha = (int)(255.0F * fadeProgress);
                        context.fill(0, 0, screenWidth, screenHeight, alpha << 24 | 16777215);
                    } else if (elapsed < 80L) {
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        context.drawTexture(IMPACT1_TEXTURE, 0, 0, screenWidth, screenHeight, 0.0F, 0.0F, 1920, 1080, 1920, 1080);
                    } else if (elapsed < 130L) {
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        context.drawTexture(IMPACT2_TEXTURE, 0, 0, screenWidth, screenHeight, 0.0F, 0.0F, 1920, 1080, 1920, 1080);
                    } else if (elapsed < 180L) {
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        context.drawTexture(IMPACT3_TEXTURE, 0, 0, screenWidth, screenHeight, 0.0F, 0.0F, 1920, 1080, 1920, 1080);
                    } else {
                        perfectImpactActive = false;
                    }
                }

                if (facingDapImpactActive) {
                    long elapsed = System.currentTimeMillis() - facingDapImpactStartMs;
                    Identifier facingFrame;
                    if (elapsed < 50L) {
                        facingFrame = IMPAC7_TEXTURE;
                    } else if (elapsed < 100L) {
                        facingFrame = IMPAC8_TEXTURE;
                    } else if (elapsed < 150L) {
                        facingFrame = IMPAC9_TEXTURE;
                    } else if (elapsed < 200L) {
                        facingFrame = PERFECT_FRAME0_TEXTURE;
                    } else {
                        facingDapImpactActive = false;
                        facingFrame = null;
                    }

                    if (facingFrame != null) {
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        context.drawTexture(facingFrame, 0, 0, screenWidth, screenHeight, 0.0F, 0.0F, 1920, 1080, 1920, 1080);
                    }
                }

                if (dropKickImpactActive) {
                    long dke = System.currentTimeMillis() - dropKickImpactStartMs;
                    if (dke >= 430L) {
                        dropKickImpactActive = false;
                    } else {
                        Identifier dkTex;
                        float dkAlpha;
                        if (dke < 50L) {
                            dkTex = IMPACT1_TEXTURE;
                            dkAlpha = (float)dke / 50.0F;
                        } else if (dke < 150L) {
                            dkTex = IMPACT1_TEXTURE;
                            dkAlpha = 1.0F;
                        } else if (dke < 230L) {
                            dkTex = IMPACT2_TEXTURE;
                            dkAlpha = 1.0F;
                        } else if (dke < 310L) {
                            dkTex = IMPACT3_TEXTURE;
                            dkAlpha = 1.0F;
                        } else {
                            dkTex = IMPACT3_TEXTURE;
                            dkAlpha = 1.0F - (float)(dke - 310L) / 120.0F;
                        }

                        RenderSystem.enableBlend();
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, dkAlpha);
                        context.drawTexture(dkTex, 0, 0, screenWidth, screenHeight, 0.0F, 0.0F, 1920, 1080, 1920, 1080);
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        RenderSystem.disableBlend();
                    }
                }

                boolean onCooldown = System.currentTimeMillis() < whiffCooldownEnd;
                if (onCooldown && !isCharging) {
                    long remaining = whiffCooldownEnd - System.currentTimeMillis();
                    float cooldownProgress = (float)remaining / 800.0F;
                    int barWidth = 40;
                    int barHeight = 3;
                    int barX = (screenWidth - barWidth) / 2;
                    int barY = screenHeight / 2 + 20;
                    context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 1140850688);
                    int fillWidth = (int)(barWidth * cooldownProgress);
                    context.fill(barX, barY, barX + fillWidth, barY + barHeight, -1140916224);
                }

                if (isCharging && !onCooldown) {
                    long elapsedx = System.currentTimeMillis() - chargeStartTime;
                    float chargePercent = Math.min(1.0F, (float)elapsedx / 250.0F);
                    float myFire = CoopMovesConfig.get().enableFireDap ? localFireLevel : 0.0F;
                    int barWidth = 40;
                    int barHeight = 3;
                    int barX = (screenWidth - barWidth) / 2;
                    int barY = screenHeight / 2 + 20;
                    context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 1140850688);
                    if (!(myFire > 0.05F)) {
                        int fillWidth = (int)(barWidth * chargePercent);
                        int fillColor;
                        if (chargePercent >= 0.99F) {
                            fillColor = -1157562624;
                        } else {
                            fillColor = -1140872704;
                        }

                        context.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);
                    } else {
                        int greenWidth = (int)(barWidth * chargePercent);
                        context.fill(barX, barY, barX + greenWidth, barY + barHeight, -1157562624);
                        int redWidth = (int)(barWidth * myFire);
                        int shakeX = 0;
                        int shakeY = 0;
                        if (!isHeavenReady) {
                            if (myFire >= 0.99F) {
                                shakeX = (int)((Math.random() - 0.5) * 4.0);
                                shakeY = (int)((Math.random() - 0.5) * 2.0);
                                context.fill(barX + shakeX, barY + shakeY, barX + redWidth + shakeX, barY + barHeight + shakeY, -570482176);
                            } else {
                                context.fill(barX, barY, barX + redWidth, barY + barHeight, -570482176);
                            }
                        } else {
                            long timeSinceReady = System.currentTimeMillis() - heavenReadyStartTime;
                            shakeX = (int)((Math.random() - 0.5) * 8.0);
                            shakeY = (int)((Math.random() - 0.5) * 6.0);
                            if (timeSinceReady % 500L < 250L) {
                                context.fill(barX + shakeX, barY + shakeY, barX + redWidth + shakeX, barY + barHeight + shakeY, -65281);
                            } else {
                                context.fill(barX + shakeX, barY + shakeY, barX + redWidth + shakeX, barY + barHeight + shakeY, -570482176);
                            }

                            context.fill(barX - 5 + shakeX, barY + 1 + shakeY, barX + barWidth + 5 + shakeX, barY + 2 + shakeY, -1);

                            for (int i = 0; i < barWidth; i++) {
                                int crackY = barY + i % 2 + shakeY;
                                context.fill(barX + i + shakeX, crackY, barX + i + 1 + shakeX, crackY + 1, -1996488705);
                            }

                            if (timeSinceReady % 100L < 50L) {
                                context.fill(barX - 2 + shakeX, barY - 3 + shakeY, barX - 1 + shakeX, barY - 2 + shakeY, -8960);
                                context.fill(barX + barWidth + 1 + shakeX, barY - 3 + shakeY, barX + barWidth + 2 + shakeX, barY - 2 + shakeY, -8960);
                            }
                        }
                    }

                    int partnerY = barY + 8;

                    for (Entry<UUID, Float> entry : otherPlayerCharges.entrySet()) {
                        if (otherPlayerCharging.getOrDefault(entry.getKey(), false) && !entry.getKey().equals(client.player.getUuid())) {
                            boolean inRange = false;
                            if (client.world != null) {
                                for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
                                    if (player.getUuid().equals(entry.getKey())) {
                                        if (client.player.distanceTo(player) <= 20.0) {
                                            inRange = true;
                                        }
                                        break;
                                    }
                                }
                            }

                            if (inRange) {
                                float partnerCharge = entry.getValue();
                                float partnerFire = otherPlayerFire.getOrDefault(entry.getKey(), 0.0F);
                                int pBarWidth = 30;
                                int pBarHeight = 2;
                                int pBarX = (screenWidth - pBarWidth) / 2;
                                context.fill(pBarX - 1, partnerY - 1, pBarX + pBarWidth + 1, partnerY + pBarHeight + 1, 855638016);
                                int pFillWidth = (int)(pBarWidth * partnerCharge);
                                int pFillColor = partnerFire > 0.1F ? -1426111488 : (partnerCharge >= 0.99F ? -1442775296 : -1426085376);
                                context.fill(pBarX, partnerY, pBarX + pFillWidth, partnerY + pBarHeight, pFillColor);
                                partnerY += 6;
                            }
                        }
                    }
                }

                QTEClientHandler.renderHUD(context, screenWidth, screenHeight);
            } else {
                isCharging = false;
                localFireLevel = 0.0F;
            }
        }
    }

    public static boolean isCurrentlyCharging() {
        return isCharging;
    }

    public static KeyBinding getChargeKey() {
        return chargedDapKey;
    }

    public static KeyBinding getComboKey() {
        return fireDapComboKey;
    }

    public static float getChargePercent() {
        if (!isCharging) {
            return 0.0F;
        } else {
            long elapsed = System.currentTimeMillis() - chargeStartTime;
            return Math.min(1.0F, (float)elapsed / 250.0F);
        }
    }

    public static float getFireLevel() {
        return localFireLevel;
    }

    public static boolean isPlayerCharging(UUID playerId) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && client.player.getUuid().equals(playerId) ? isCharging : otherPlayerCharging.getOrDefault(playerId, false);
    }

    public static float getPlayerChargePercent(UUID playerId) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && client.player.getUuid().equals(playerId)
            ? getChargePercent()
            : otherPlayerCharges.getOrDefault(playerId, 0.0F);
    }

    public static float getPlayerFireLevel(UUID playerId) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && client.player.getUuid().equals(playerId) ? localFireLevel : otherPlayerFire.getOrDefault(playerId, 0.0F);
    }

    public static boolean isOnWhiffCooldown() {
        return System.currentTimeMillis() < whiffCooldownEnd;
    }

    public static boolean isImpactFrameActive() {
        return perfectImpactActive;
    }

    public static boolean isLocalPlayerPerfectDapFrozen() {
        return isPerfectDapFrozen;
    }

    public static boolean isLocalPlayerFireDapFrozen() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? false : fireDapFrozenPlayers.getOrDefault(client.player.getUuid(), false);
    }

    public static boolean shouldShowFireDapFirstPerson() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? false : fireDapFirstPersonPlayers.contains(client.player.getUuid());
    }

    public static boolean isInQTEWindow() {
        return QTEClientHandler.isActive();
    }

    public static boolean isPlayerFrozen() {
        return isPerfectDapFrozen;
    }

    public static String getQTEExpectedButton() {
        return QTEClientHandler.getExpectedButton();
    }

    public static long getQTEWindowStart() {
        return QTEClientHandler.getWindowStart();
    }

    public static long getQTEWindowEnd() {
        return QTEClientHandler.getWindowEnd();
    }

    public static int getQTEStage() {
        return QTEClientHandler.getStage();
    }

    public static void cleanup(UUID playerId) {
        otherPlayerCharges.remove(playerId);
        otherPlayerFire.remove(playerId);
        otherPlayerCharging.remove(playerId);
        fireDapFrozenPlayers.remove(playerId);
        fireDapFirstPersonPlayers.remove(playerId);
        CoopAnimationHandler.setFireDapLocked(playerId, false);
    }

    public static void clearFireDapState() {
        fireDapFrozenPlayers.clear();
        fireDapFirstPersonPlayers.clear();
        CoopAnimationHandler.clearFireDapLocks();
    }
}
