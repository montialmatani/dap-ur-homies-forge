package com.cooptest.client;

import com.cooptest.ModSounds;
import com.cooptest.StrongSlapHandler;
import java.util.EnumMap;
import java.util.Map;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundCategory;
import com.cooptest.fabricshim.mc.RenderTickCounter;

public class StrongSlapClientHandler {
    private static boolean localPlayerFrozen = false;
    private static boolean neckBroken = false;
    private static long lockEndTime = 0L;
    private static final float LOCK_PITCH = 90.0F;
    private static boolean lookLocked = false;
    private static float lockedYaw = 0.0F;
    private static float lockedPitch = 0.0F;
    private static long flashEndTime = 0L;
    private static int flashColor = 16711680;
    private static final long FLASH_DURATION_MS = 600L;
    private static final Map<SoundCategory, Double> savedVolumes = new EnumMap<>(SoundCategory.class);
    private static boolean deafened = false;

    public static boolean isLocalPlayerFrozen() {
        return localPlayerFrozen;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(StrongSlapHandler.MoveFreezePayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            if (ctx.client().player != null) {
                if (ctx.client().player.getUuid().equals(payload.playerId())) {
                    localPlayerFrozen = payload.frozen();
                }
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(StrongSlapHandler.NeckBrokenPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            MinecraftClient client = ctx.client();
            if (client.player != null) {
                if (client.player.getUuid().equals(payload.playerId())) {
                    if (payload.durationMs() <= 0L) {
                        neckBroken = false;
                        lockEndTime = 0L;
                        localPlayerFrozen = false;
                        restoreSoundVolumes(client);
                    } else {
                        neckBroken = true;
                        lockEndTime = System.currentTimeMillis() + payload.durationMs();
                        client.player.setPitch(90.0F);
                        muteSoundVolumes(client);
                    }
                }
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(StrongSlapHandler.TortureTickPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            MinecraftClient client = ctx.client();
            if (client.player != null) {
                if (client.player.getUuid().equals(payload.playerId())) {
                    client.getSoundManager().play(PositionedSoundInstance.master(ModSounds.TORTURE, 1.0F));
                    flashColor = payload.randomColor();
                    flashEndTime = System.currentTimeMillis() + 600L;
                }
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(StrongSlapHandler.LookLockPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            MinecraftClient client = ctx.client();
            if (client.player != null) {
                if (client.player.getUuid().equals(payload.playerId())) {
                    lookLocked = payload.locked();
                    if (lookLocked) {
                        lockedYaw = payload.targetYaw();
                        lockedPitch = client.player.getPitch();
                        client.player.setYaw(lockedYaw);
                    }
                }
            }
        }));
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
            if (client.player != null) {
                if (lookLocked) {
                    client.player.setYaw(lockedYaw);
                    client.player.setPitch(lockedPitch);
                }

                if (neckBroken) {
                    long now = System.currentTimeMillis();
                    if (now >= lockEndTime) {
                        neckBroken = false;
                        localPlayerFrozen = false;
                        restoreSoundVolumes(client);
                        return;
                    }

                    float currentPitch = client.player.getPitch();
                    if (currentPitch < 90.0F) {
                        client.player.setPitch(90.0F);
                        ClientPlayNetworking.send(new StrongSlapHandler.NeckResistPayload(client.player.getUuid()));
                    }
                }
            }
        });
        HudRenderCallback.EVENT.register(StrongSlapClientHandler::renderHUD);
    }

    private static void muteSoundVolumes(MinecraftClient client) {
        if (!deafened) {
            deafened = true;

            for (SoundCategory cat : SoundCategory.values()) {
                if (cat != SoundCategory.MASTER) {
                    double current = (Double)client.options.getSoundVolumeOption(cat).getValue();
                    savedVolumes.put(cat, current);
                    client.options.getSoundVolumeOption(cat).setValue(0.0);
                }
            }

            client.getSoundManager().stopAll();
        }
    }

    private static void restoreSoundVolumes(MinecraftClient client) {
        if (deafened) {
            deafened = false;

            for (SoundCategory cat : SoundCategory.values()) {
                if (cat != SoundCategory.MASTER) {
                    Double saved = savedVolumes.get(cat);
                    if (saved != null) {
                        client.options.getSoundVolumeOption(cat).setValue(saved);
                    }
                }
            }

            savedVolumes.clear();
        }
    }

    private static void renderHUD(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            int screenW = ctx.getScaledWindowWidth();
            int screenH = ctx.getScaledWindowHeight();
            long now = System.currentTimeMillis();
            if (neckBroken) {
                long remaining = lockEndTime - now;
                if (remaining > 0L) {
                    ctx.fill(0, 0, screenW, screenH, -872415232);
                }
            }

            if (now < flashEndTime) {
                long remaining = flashEndTime - now;
                int alpha = (int)((float)remaining / 600.0F * 255.0F);
                alpha = Math.min(255, Math.max(0, alpha));
                int color = alpha << 24 | flashColor & 16777215;
                ctx.fill(0, 0, screenW, screenH, color);
            }

            if (neckBroken) {
                long remaining = lockEndTime - now;
                if (remaining <= 0L) {
                    return;
                }

                int vig = 130;
                int t = 18;
                ctx.fill(0, 0, screenW, t, vig << 24 | 12255232);
                ctx.fill(0, screenH - t, screenW, screenH, vig << 24 | 12255232);
                ctx.fill(0, 0, t, screenH, vig << 24 | 12255232);
                ctx.fill(screenW - t, 0, screenW, screenH, vig << 24 | 12255232);
            }
        }
    }

    public static boolean isNeckBroken() {
        return neckBroken;
    }
}
