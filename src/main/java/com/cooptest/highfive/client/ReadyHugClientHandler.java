package com.cooptest.highfive.client;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.cooptest.highfive.ReadyHugHandler;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.InputUtil.Type;
import org.lwjgl.glfw.GLFW;

public class ReadyHugClientHandler {
    private static boolean shiftWasHeld = false;
    private static boolean inHug = false;
    private static UUID partnerId = null;
    private static final int WATCHDOG_TICKS = 100;
    private static int ticksSinceSignal = 0;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ReadyHugHandler.HugSessionPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            ticksSinceSignal = 0;
            inHug = payload.active();
            partnerId = payload.active() ? payload.partner() : null;
            if (!payload.active()) {
                shiftWasHeld = false;
            }
        }));
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
            if (client.player == null) {
                forceRelease();
            } else {
                if (inHug) {
                    if (++ticksSinceSignal > 100) {
                        forceRelease();
                    }
                } else {
                    ticksSinceSignal = 0;
                }

                UUID id = client.player.getUuid();
                PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
                if (pose != PoseState.GRAB_READY && !inHug) {
                    if (shiftWasHeld) {
                        ClientPlayNetworking.send(new ReadyHugHandler.HugShiftPayload(false, false));
                        shiftWasHeld = false;
                    }
                } else {
                    long win = client.getWindow().getHandle();
                    boolean held = isBoundKeyDown(win, client.options.sneakKey);
                    boolean rightClick = GLFW.glfwGetMouseButton(win, 1) == 1;
                    if (held != shiftWasHeld) {
                        ClientPlayNetworking.send(new ReadyHugHandler.HugShiftPayload(held, rightClick));
                        shiftWasHeld = held;
                    } else if (held && client.player.age % 5 == 0) {
                        ClientPlayNetworking.send(new ReadyHugHandler.HugShiftPayload(true, rightClick));
                    }
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(h, c) -> forceRelease());
        ClientPlayConnectionEvents.JOIN.register((Join)(h, s, c) -> forceRelease());
    }

    public static void forceRelease() {
        inHug = false;
        partnerId = null;
        shiftWasHeld = false;
        ticksSinceSignal = 0;
    }

    public static boolean isLocalPlayerInHug() {
        return inHug;
    }

    public static UUID getPartnerId() {
        return partnerId;
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
}
