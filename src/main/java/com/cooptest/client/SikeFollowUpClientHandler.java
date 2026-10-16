package com.cooptest.client;

import com.cooptest.SikeFollowUpHandler;
import java.util.Random;
import com.cooptest.fabricshim.api.EnvType;
import com.cooptest.fabricshim.api.Environment;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.InputUtil.Type;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class SikeFollowUpClientHandler {
    private static final Random RNG = new Random();
    private static final int PROMPT_COLOR = -24576;
    private static final float PROMPT_SCALE = 1.0F;
    private static final int PROMPT_ABOVE_HOTBAR = 62;
    private static long armedUntil = 0L;
    private static long promptFrom = 0L;
    private static boolean sentThisWindow = false;
    private static boolean gWasPressed = false;
    private static boolean flickering = false;
    private static long flickerEndMs = 0L;
    private static long lastFlickMs = 0L;
    private static float baseYaw = 0.0F;
    private static float basePitch = 0.0F;
    private static final long FLICKER_INTERVAL_MS = 90L;
    private static final float YAW_MAX_OFFSET = 55.0F;
    private static final float PITCH_MAX_OFFSET = 45.0F;

    public static boolean isArmed() {
        return System.currentTimeMillis() < armedUntil;
    }

    private static boolean promptVisible() {
        long now = System.currentTimeMillis();
        return now >= promptFrom && now < armedUntil;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SikeFollowUpHandler.SikeWindowPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            if (payload.open()) {
                long now = System.currentTimeMillis();
                armedUntil = now + payload.durationMs();
                promptFrom = now + payload.promptDelayMs();
                sentThisWindow = false;
            } else {
                armedUntil = 0L;
                promptFrom = 0L;
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(SikeFollowUpHandler.NoyaFlickerPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            MinecraftClient client = ctx.client();
            if (client.player != null) {
                if (client.player.getUuid().equals(payload.victimId())) {
                    if (payload.start()) {
                        baseYaw = client.player.getYaw();
                        basePitch = client.player.getPitch();
                        flickerEndMs = System.currentTimeMillis() + payload.durationMs();
                        lastFlickMs = 0L;
                        flickering = true;
                    } else {
                        flickering = false;
                        client.player.setYaw(baseYaw);
                        client.player.setPitch(basePitch);
                        float finalPitch = Math.max(-90.0F, basePitch - 55.0F);
                        client.player.setPitch(finalPitch);
                    }
                }
            }
        }));
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
            if (client.player == null) {
                armedUntil = 0L;
                promptFrom = 0L;
                flickering = false;
            } else {
                long win = client.getWindow().getHandle();
                boolean gPressed = isBoundKeyDown(win, ChargedDapClientHandler.getChargedDapKey());
                if (gPressed && !gWasPressed && isArmed()) {
                    ClientPlayNetworking.send(new SikeFollowUpHandler.SikePressPayload());
                    sentThisWindow = true;
                }

                gWasPressed = gPressed;
                if (flickering) {
                    long now = System.currentTimeMillis();
                    if (now >= flickerEndMs) {
                        flickering = false;
                        client.player.setYaw(baseYaw);
                        client.player.setPitch(basePitch);
                    } else {
                        if (now - lastFlickMs >= 90L) {
                            lastFlickMs = now;
                            float yawOffset = (RNG.nextFloat() - 0.5F) * 2.0F * 55.0F;
                            float pitchOffset = (RNG.nextFloat() - 0.3F) * 45.0F;
                            float newPitch = Math.max(-90.0F, Math.min(90.0F, basePitch + pitchOffset));
                            client.player.setYaw(baseYaw + yawOffset);
                            client.player.setPitch(newPitch);
                        } else {
                            client.player.setYaw(client.player.getYaw());
                            client.player.setPitch(client.player.getPitch());
                        }
                    }
                }
            }
        });
        HudRenderCallback.EVENT.register((HudRenderCallback)(ctx, tickDelta) -> {
            if (promptVisible()) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && !client.options.hudHidden) {
                    int cx = client.getWindow().getScaledWidth() / 2;
                    int cy = client.getWindow().getScaledHeight() - 62;
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(cx, cy, 1000.0F);
                    ctx.getMatrices().scale(1.0F, 1.0F, 1.0F);
                    String label = dapKeyLabel();
                    int w = client.textRenderer.getWidth(label);
                    ctx.drawText(client.textRenderer, label, -w / 2, 0, sentThisWindow ? -10027162 : -24576, true);
                    ctx.getMatrices().pop();
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(h, c) -> forceClear());
        ClientPlayConnectionEvents.JOIN.register((Join)(h, s, c) -> forceClear());
    }

    private static void forceClear() {
        armedUntil = 0L;
        promptFrom = 0L;
        sentThisWindow = false;
        gWasPressed = false;
        flickering = false;
    }

    public static boolean isFlickering() {
        return flickering;
    }

    private static boolean isBoundKeyDown(long win, KeyBinding kb) {
        if (kb == null) {
            return false;
        } else {
            Key key = KeyBindingHelper.getBoundKeyOf(kb);
            int code = key.getCode();
            if (code == InputUtil.UNKNOWN_KEY.getCode()) {
                return false;
            } else {
                return key.getCategory() == Type.field_1672 ? GLFW.glfwGetMouseButton(win, code) == 1 : InputUtil.isKeyPressed(win, code);
            }
        }
    }

    private static String dapKeyLabel() {
        KeyBinding kb = ChargedDapClientHandler.getChargedDapKey();
        if (kb == null) {
            return "G";
        } else {
            Key key = KeyBindingHelper.getBoundKeyOf(kb);
            if (key.getCode() == InputUtil.UNKNOWN_KEY.getCode()) {
                return "?";
            } else {
                String t = key.getLocalizedText().getString();
                return t.isEmpty() ? "?" : t.toUpperCase();
            }
        }
    }
}
