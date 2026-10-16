package com.cooptest.mixin.client.impactframemixin;

import com.cooptest.client.CoopImpactHandler;
import com.cooptest.client.CoopImpactRenderType;
import com.cooptest.fabricshim.api.EnvType;
import com.cooptest.fabricshim.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({LivingEntityRenderer.class})
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
    @Inject(
        method = {"getRenderLayer"},
        at = {@At("RETURN")},
        cancellable = true
    )
    private void coopSwapRenderLayer(T entity, boolean showBody, boolean translucent, boolean showOutline, CallbackInfoReturnable<RenderLayer> cir) {
        if (CoopImpactHandler.playing) {
            if (CoopImpactRenderType.isReady()) {
                Identifier texture = ((EntityRenderer)this).getTexture(entity);
                switch (CoopImpactHandler.currentFrameType) {
                    case WHITE:
                    case RED:
                        cir.setReturnValue(CoopImpactRenderType.getBlackLayer(texture));
                        break;
                    case BLACK:
                    case CYAN:
                        cir.setReturnValue(CoopImpactRenderType.getWhiteLayer(texture));
                        break;
                    case INVERT:
                        cir.setReturnValue(CoopImpactRenderType.getInvertLayer(texture));
                }
            }
        }
    }
}
