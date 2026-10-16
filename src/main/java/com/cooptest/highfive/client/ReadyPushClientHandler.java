package com.cooptest.highfive.client;

import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.cooptest.highfive.ReadyPushHandler;
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

public class ReadyPushClientHandler {
    private static boolean lastShift = false;
    private static boolean lastRight = false;
    private static int refreshTicks = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
            if (client.player != null) {
                UUID id = client.player.getUuid();
                PoseState pose = PoseNetworking.poseStates.getOrDefault(id, PoseState.NONE);
                boolean relevant = pose == PoseState.GRAB_READY || pose == PoseState.PUSH_IDLE;
                if (!relevant) {
                    if (lastShift || lastRight) {
                        ClientPlayNetworking.send(new ReadyPushHandler.PushInputPayload(false, false));
                        lastShift = false;
                        lastRight = false;
                    }
                } else {
                    long win = client.getWindow().getHandle();
                    boolean shift = isBoundKeyDown(win, client.options.sneakKey);
                    boolean right = GLFW.glfwGetMouseButton(win, 1) == 1;
                    boolean changed = shift != lastShift || right != lastRight;
                    if (changed || shift && ++refreshTicks >= 5) {
                        if (!changed) {
                            refreshTicks = 0;
                        }

                        ClientPlayNetworking.send(new ReadyPushHandler.PushInputPayload(shift, right));
                        lastShift = shift;
                        lastRight = right;
                    }
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(h, c) -> reset());
        ClientPlayConnectionEvents.JOIN.register((Join)(h, s, c) -> reset());
    }

    private static void reset() {
        lastShift = false;
        lastRight = false;
        refreshTicks = 0;
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
