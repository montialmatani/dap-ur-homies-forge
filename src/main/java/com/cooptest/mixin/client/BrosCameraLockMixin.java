package com.cooptest.mixin.client;

import com.cooptest.bros.client.BrosClientHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Mouse.class})
public abstract class BrosCameraLockMixin {
    @Shadow
    private double cursorDeltaX;

    @Inject(
        method = {"updateMouse"},
        at = {@At("HEAD")}
    )
    private void cooptest$brosTurnVote(CallbackInfo ci) {
        if (BrosClientHandler.isEngaged()) {
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                double s = (Double)mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
                double deg = this.cursorDeltaX * s * s * s * 8.0 * 0.15;
                BrosClientHandler.addTurnVote(deg);
            } catch (Throwable var7) {
            }

            this.cursorDeltaX = 0.0;
        }
    }

    @Inject(
        method = {"updateMouse"},
        at = {@At("TAIL")}
    )
    private void cooptest$brosLockYaw(CallbackInfo ci) {
        try {
            BrosClientHandler.applyLocalYaw();
        } catch (Throwable var3) {
        }
    }
}
