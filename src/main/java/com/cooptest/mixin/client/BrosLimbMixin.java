package com.cooptest.mixin.client;

import com.cooptest.bros.client.BrosClientHandler;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LivingEntity.class})
public abstract class BrosLimbMixin {
    @Inject(
        method = {"updateLimbs(F)V"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private void cooptest$brosLegs(float posDelta, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.getWorld().isClient()) {
            if (BrosClientHandler.isLegDriven(self.getId())) {
                ci.cancel();
            }
        }
    }
}
