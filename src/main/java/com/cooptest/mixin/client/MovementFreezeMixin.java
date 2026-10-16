package com.cooptest.mixin.client;

import com.cooptest.client.ChargedDapClientHandler;
import com.cooptest.client.CoopAnimationHandler;
import com.cooptest.client.DapHoldClientHandler;
import com.cooptest.client.DivineFlamComboClient;
import com.cooptest.client.DuoPoseClientHandler;
import com.cooptest.client.HighFiveClientHandler;
import com.cooptest.client.HuddleClientHandler;
import com.cooptest.client.StrongSlapClientHandler;
import com.cooptest.highfive.client.ReadyHugClientHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.input.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({KeyboardInput.class})
public class MovementFreezeMixin {
    @Inject(
        method = {"tick"},
        at = {@At("TAIL")}
    )
    private void onTick(boolean slowDown, float f, CallbackInfo ci) {
        boolean shouldFreeze = HighFiveClientHandler.isLocalPlayerFrozen()
            || DivineFlamComboClient.isLocalPlayerInCombo()
            || ChargedDapClientHandler.isLocalPlayerFireDapFrozen()
            || ChargedDapClientHandler.isLocalPlayerPerfectDapFrozen()
            || DapHoldClientHandler.isLocalPlayerFrozen()
            || ChargedDapClientHandler.isDapBadBlocking()
            || StrongSlapClientHandler.isLocalPlayerFrozen()
            || DuoPoseClientHandler.isLocalPlayerFrozen()
            || ReadyHugClientHandler.isLocalPlayerInHug()
            || isInHuddle()
            || isLocalPlayerCuffed()
            || isInHandSpin();
        if (shouldFreeze) {
            Input input = (Input)(Object)this;
            input.movementForward = 0.0F;
            input.movementSideways = 0.0F;
            input.jumping = false;
            input.sneaking = false;
        } else {
            if (HuddleClientHandler.isSuppressingSneak()) {
                Input input = (Input)(Object)this;
                input.sneaking = false;
            }
        }
    }

    private static boolean isInHuddle() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? false : CoopAnimationHandler.isInHuddleAnim(client.player.getUuid());
    }

    private static boolean isInHandSpin() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? false : CoopAnimationHandler.isInHandSpinAnim(client.player.getUuid());
    }

    private static boolean isLocalPlayerCuffed() {
        return CoopAnimationHandler.isLocalPlayerCuffed();
    }
}
