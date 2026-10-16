package com.cooptest.client;

import com.cooptest.DapFlair;
import com.cooptest.fabricshim.api.EnvType;
import com.cooptest.fabricshim.api.Environment;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.InputUtil.Type;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class DapFlairClientHandler {
    private static final int PROMPT_COLOR = -24576;
    private static final float PROMPT_SCALE = 1.0F;
    private static final int PROMPT_ABOVE_HOTBAR = 62;
    private static long armedUntil = 0L;
    private static long promptFrom = 0L;
    private static boolean sentThisWindow = false;
    private static boolean wasPressed = false;

    public static boolean isArmed() {
        return System.currentTimeMillis() < armedUntil;
    }

    private static boolean armed() {
        return System.currentTimeMillis() < armedUntil;
    }

    private static boolean promptVisible() {
        long now = System.currentTimeMillis();
        return now >= promptFrom && now < armedUntil;
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DapFlair.FlairWindowPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
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
        ClientPlayNetworking.registerGlobalReceiver(DapFlair.FlairSuccessPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            CoopCameraShakeHandler.shake(payload.shake(), payload.shakeMs());
            MinecraftClient c = ctx.client();
            if (c.player != null) {
                c.player.playSound((SoundEvent)SoundEvents.field_14725.comp_349(), 1.0F, 2.0F);
                c.player.playSound(SoundEvents.field_14627, 0.8F, 1.4F);
            }

            armedUntil = 0L;
            promptFrom = 0L;
        }));
        ClientPlayNetworking.registerGlobalReceiver(
            DapFlair.FlairShakePayload.ID, (payload, ctx) -> ctx.client().execute(() -> CoopCameraShakeHandler.shake(payload.shake(), payload.shakeMs()))
        );
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
            if (client.player == null) {
                armedUntil = 0L;
                promptFrom = 0L;
            } else {
                boolean pressed = isDapKeyDown(client);
                if (pressed && !wasPressed && armed()) {
                    ClientPlayNetworking.send(new DapFlair.FlairPressPayload());
                    sentThisWindow = true;
                }

                wasPressed = pressed;
            }
        });
        HudRenderCallback.EVENT.register((HudRenderCallback)(ctx, tickDelta) -> {
            if (promptVisible()) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    int cx = client.getWindow().getScaledWidth() / 2;
                    int cy = client.getWindow().getScaledHeight() - 62;
                    MatrixStack matrices = ctx.getMatrices();
                    matrices.push();
                    matrices.translate(cx, cy, 1000.0F);
                    matrices.scale(1.0F, 1.0F, 1.0F);
                    String label = dapKeyLabel();
                    int w = client.textRenderer.getWidth(label);
                    ctx.drawText(client.textRenderer, label, -w / 2, 0, sentThisWindow ? -10027162 : -24576, true);
                    matrices.pop();
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(h, c) -> {
            armedUntil = 0L;
            promptFrom = 0L;
        });
    }

    private static Key dapKey() {
        KeyBinding kb = ChargedDapClientHandler.getChargedDapKey();
        return kb == null ? Type.field_1668.createFromCode(71) : KeyBindingHelper.getBoundKeyOf(kb);
    }

    private static boolean isDapKeyDown(MinecraftClient client) {
        if (client != null && client.getWindow() != null) {
            Key key = dapKey();
            int code = key.getCode();
            if (code == InputUtil.UNKNOWN_KEY.getCode()) {
                return false;
            } else {
                long win = client.getWindow().getHandle();
                return key.getCategory() == Type.field_1672 ? GLFW.glfwGetMouseButton(win, code) == 1 : InputUtil.isKeyPressed(win, code);
            }
        } else {
            return false;
        }
    }

    private static String dapKeyLabel() {
        Key key = dapKey();
        if (key.getCode() == InputUtil.UNKNOWN_KEY.getCode()) {
            return "?";
        } else {
            String s = key.getLocalizedText().getString();
            return s.isEmpty() ? "?" : s.toUpperCase();
        }
    }
}
