package com.cooptest.client;

import com.cooptest.HuddleHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
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
import net.minecraft.text.Text;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.InputUtil.Type;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class HuddleClientHandler {
    private static final int BAR_WIDTH = 120;
    private static final int BAR_HEIGHT = 8;
    private static final int BAR_ABOVE_HOTBAR = 84;
    private static boolean fWasHeld = false;
    private static boolean shiftWasHeld = false;
    private static boolean gWasHeld = false;
    private static boolean suppressSneak = false;
    private static boolean barActive = false;
    private static float barFill = 0.0F;
    private static int barPlayers = 0;
    private static boolean yawLocked = false;
    private static float lockedYaw = 0.0F;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HuddleHandler.HuddleEndPayload.ID, (payload, ctx) -> ctx.client().execute(HuddleClientHandler::clearLocal));
        ClientPlayNetworking.registerGlobalReceiver(HuddleHandler.HuddleBarPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            barFill = payload.fill();
            barPlayers = payload.players();
            barActive = payload.active();
            if (!payload.active()) {
                yawLocked = false;
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(
            HuddleHandler.HuddleShakePayload.ID,
            (payload, ctx) -> ctx.client().execute(() -> CoopCameraShakeHandler.shake(payload.amount(), payload.durationMs()))
        );
        ClientTickEvents.END_CLIENT_TICK
            .register(
                (EndTick)client -> {
                    if (client.player == null) {
                        clearLocal();
                    } else {
                        long win = client.getWindow().getHandle();
                        boolean inHuddle = CoopAnimationHandler.isInHuddleAnim(client.player.getUuid());
                        if (inHuddle) {
                            if (!yawLocked) {
                                lockedYaw = client.player.getYaw();
                                yawLocked = true;
                            }

                            client.player.setYaw(lockedYaw);
                            client.player.setHeadYaw(lockedYaw);
                            client.player.setBodyYaw(lockedYaw);
                        } else {
                            yawLocked = false;
                        }

                        boolean gHeld = GLFW.glfwGetKey(win, 71) == 1;
                        if (inHuddle && gHeld && !gWasHeld) {
                            ClientPlayNetworking.send(new HuddleHandler.HuddleTapPayload());
                        }

                        gWasHeld = gHeld;
                        boolean shiftHeld = isBoundKeyDown(win, client.options.sneakKey);
                        boolean gForCharge = isBoundKeyDown(win, ChargedDapClientHandler.getChargedDapKey());
                        suppressSneak = shiftHeld && (inHuddle || gForCharge);
                        PoseState pose = PoseNetworking.poseStates.getOrDefault(client.player.getUuid(), PoseState.NONE);
                        boolean poseBlocked = pose == PoseState.GRAB_READY
                            || pose == PoseState.GRAB_HOLDING
                            || pose == PoseState.GRABBED
                            || pose == PoseState.PUSH_IDLE
                            || pose == PoseState.PUSH_ACTION;
                        if (!inHuddle && poseBlocked) {
                            if (shiftWasHeld) {
                                ClientPlayNetworking.send(new HuddleHandler.HuddleShiftPayload(false));
                                shiftWasHeld = false;
                            }
                        } else if (shiftHeld != shiftWasHeld) {
                            ClientPlayNetworking.send(new HuddleHandler.HuddleShiftPayload(shiftHeld));
                            shiftWasHeld = shiftHeld;
                        }

                        boolean fHeld = GLFW.glfwGetKey(win, 70) == 1;
                        if (fHeld != fWasHeld) {
                            ClientPlayNetworking.send(new HuddleHandler.HuddleFHoldPayload(fHeld));
                            fWasHeld = fHeld;
                        }
                    }
                }
            );
        HudRenderCallback.EVENT.register((HudRenderCallback)(ctx, tickDelta) -> {
            if (barActive) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null && !client.options.hudHidden) {
                    int screenW = ctx.getScaledWindowWidth();
                    int screenH = ctx.getScaledWindowHeight();
                    int x = (screenW - 120) / 2;
                    int y = screenH - 84;
                    ctx.fill(x - 2, y - 2, x + 120 + 2, y + 8 + 2, -872415232);
                    ctx.fill(x, y, x + 120, y + 8, -14540254);
                    int fillW = (int)(120.0F * Math.max(0.0F, Math.min(1.0F, barFill)));
                    int colour = barFill >= 0.68F ? -12288 : (barFill >= 0.34F ? -16718337 : -16736064);
                    if (fillW > 0) {
                        ctx.fill(x, y, x + fillW, y + 8, colour);
                    }

                    int t2 = x + 40;
                    int t3 = x + 81;
                    ctx.fill(t2, y, t2 + 1, y + 8, -1996488705);
                    ctx.fill(t3, y, t3 + 1, y + 8, -1996488705);
                    long t = System.currentTimeMillis();
                    int jx = (int)(Math.sin(t / 45.0) * 2.0);
                    int jy = (int)(Math.cos(t / 37.0) * 2.0);
                    String g = "G";
                    int gw = client.textRenderer.getWidth(g);
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate((screenW - gw) / 2.0F + jx, y - 26.0F + jy, 0.0F);
                    ctx.getMatrices().scale(1.6F, 1.6F, 1.0F);
                    ctx.drawText(client.textRenderer, Text.literal(g), 0, 0, -15360, true);
                    ctx.getMatrices().pop();
                    String label = "§7×" + barPlayers;
                    int lw = client.textRenderer.getWidth(label);
                    ctx.drawText(client.textRenderer, Text.literal(label), (screenW - lw) / 2, y - 11, 16777215, true);
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(h, c) -> clearLocal());
        ClientPlayConnectionEvents.JOIN.register((Join)(h, s, c) -> clearLocal());
    }

    private static void clearLocal() {
        barActive = false;
        barFill = 0.0F;
        barPlayers = 0;
        yawLocked = false;
        fWasHeld = false;
        shiftWasHeld = false;
        gWasHeld = false;
        suppressSneak = false;
    }

    public static boolean isBarActive() {
        return barActive;
    }

    public static boolean isSuppressingSneak() {
        return suppressSneak;
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
                return key.getCategory() == Type.MOUSE ? GLFW.glfwGetMouseButton(win, code) == 1 : InputUtil.isKeyPressed(win, code);
            }
        }
    }
}
