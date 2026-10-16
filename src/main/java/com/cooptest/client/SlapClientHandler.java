package com.cooptest.client;

import com.cooptest.SlapHandler;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import com.cooptest.fabricshim.mc.RenderTickCounter;

public class SlapClientHandler {
    private static long fishFlashEndTime = 0L;
    private static final long FISH_FLASH_MS = 3000L;
    private static long slapCooldownEnd = 0L;

    public static boolean isOnSlapCooldown() {
        return System.currentTimeMillis() < slapCooldownEnd;
    }

    public static void triggerSlapCooldown() {
        slapCooldownEnd = System.currentTimeMillis() + 500L;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SlapHandler.CameraFlickPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            MinecraftClient client = ctx.client();
            if (client.player != null) {
                if (client.player.getUuid().equals(payload.playerId())) {
                    float newPitch = Math.min(90.0F, client.player.getPitch() + payload.pitchDelta());
                    client.player.setPitch(newPitch);
                    triggerSlapCooldown();
                }
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(SlapHandler.CameraYawFlickPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            MinecraftClient client = ctx.client();
            if (client.player != null) {
                if (client.player.getUuid().equals(payload.playerId())) {
                    client.player.setYaw(client.player.getYaw() + payload.yawDelta());
                }
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(SlapHandler.ScreenClosePayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            MinecraftClient client = ctx.client();
            if (client.player != null) {
                if (client.player.getUuid().equals(payload.playerId())) {
                    if (client.currentScreen != null) {
                        client.setScreen(null);
                    }
                }
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(SlapHandler.FishSlapPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            MinecraftClient client = ctx.client();
            if (client.player != null) {
                if (client.player.getUuid().equals(payload.playerId())) {
                    fishFlashEndTime = System.currentTimeMillis() + 3000L;
                }
            }
        }));
        HudRenderCallback.EVENT.register(SlapClientHandler::renderHUD);
    }

    private static void renderHUD(DrawContext ctx, RenderTickCounter tickCounter) {
        if (fishFlashEndTime > 0L) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                long now = System.currentTimeMillis();
                long remaining = fishFlashEndTime - now;
                if (remaining <= 0L) {
                    fishFlashEndTime = 0L;
                } else {
                    int screenW = ctx.getScaledWindowWidth();
                    int screenH = ctx.getScaledWindowHeight();
                    int alpha = (int)((float)remaining / 3000.0F * 230.0F);
                    alpha = Math.min(230, Math.max(0, alpha));
                    ctx.fill(0, 0, screenW, screenH, alpha << 24 | 17578);
                    int textAlpha = Math.min(255, alpha + 25);
                    ctx.drawCenteredTextWithShadow(
                        client.textRenderer, Text.literal("§b§l\ud83d\udc1f FISH SLAPPED"), screenW / 2, screenH / 2 - 10, textAlpha << 24 | 5636095
                    );
                }
            }
        }
    }
}
