package com.cooptest.mixin.client.impactframemixin;

import com.cooptest.client.CoopChromaHandler;
import com.cooptest.client.CoopRadialBlurHandler;
import com.cooptest.fabricshim.api.EnvType;
import com.cooptest.fabricshim.api.Environment;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({GameRenderer.class})
public class CoopGameRendererMixin {
    @Shadow
    @Nullable
    PostEffectProcessor postProcessor;
    @Shadow
    @Final
    MinecraftClient client;
    @Shadow
    private boolean postProcessorEnabled;
    private static final Identifier CHROMA_ID = new Identifier("minecraft", "shaders/post/cooptest_chroma.json");
    private static final Identifier RADIAL_ID = new Identifier("minecraft", "shaders/post/cooptest_radialblur.json");
    private static boolean chromaLoaded = false;
    private static boolean radialLoaded = false;

    @Inject(
        method = {"render"},
        at = {@At("HEAD")}
    )
    private void coopHandlePostEffects(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        if (CoopRadialBlurHandler.isActive()) {
            if (!radialLoaded || this.postProcessor == null) {
                this.loadPost(RADIAL_ID);
                radialLoaded = true;
                chromaLoaded = false;
            }
        } else if (CoopChromaHandler.isActive()) {
            if (!chromaLoaded || this.postProcessor == null) {
                this.loadPost(CHROMA_ID);
                chromaLoaded = true;
                radialLoaded = false;
            }
        } else if ((chromaLoaded || radialLoaded) && this.postProcessor != null) {
            this.postProcessor.close();
            this.postProcessor = null;
            chromaLoaded = false;
            radialLoaded = false;
        }
    }

    private void loadPost(Identifier id) {
        if (this.postProcessor != null) {
            this.postProcessor.close();
            this.postProcessor = null;
        }

        try {
            this.postProcessor = new PostEffectProcessor(this.client.getTextureManager(), this.client.getResourceManager(), this.client.getFramebuffer(), id);
            this.postProcessor.setupDimensions(this.client.getWindow().getFramebufferWidth(), this.client.getWindow().getFramebufferHeight());
            this.postProcessorEnabled = true;
        } catch (Exception var3) {
            System.err.println("[CoopMod] Post shader failed: " + id + " — " + var3.getMessage());
        }
    }
}
