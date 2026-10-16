package com.cooptest.mixin.client;

import com.cooptest.client.HighFivePassClientHandler;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Mouse.class})
public class HighFivePassCameraLockMixin {
    @Inject(
        method = {"updateMouse"},
        at = {@At("TAIL")}
    )
    private void onUpdateMouseTail(CallbackInfo ci) {
        HighFivePassClientHandler.clampCameraYaw();
    }
}
