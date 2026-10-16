package com.cooptest.client;

import com.mojang.blaze3d.platform.GlStateManager.DstFactor;
import com.mojang.blaze3d.platform.GlStateManager.SrcFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Random;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat.DrawMode;
import org.joml.Matrix4f;

public class ImpactFrameOverlay {
    private static final Identifier[] FRAMES = new Identifier[]{
        new Identifier("testcoop", "textures/impact/frame1.png"), new Identifier("testcoop", "textures/impact/frame2.png")
    };
    private static final Random RANDOM = new Random();
    private static int currentFrame = 0;
    private static int lastFlipFrame = -1;

    public static void render(DrawContext context) {
        if (CoopClientSettings.get().impactFramesEnabled) {
            if (CoopImpactHandler.playing) {
                int frameIdx = (int)((System.currentTimeMillis() - CoopImpactHandler.getStartMs()) / 33L);
                if (frameIdx != lastFlipFrame) {
                    lastFlipFrame = frameIdx;
                    currentFrame = RANDOM.nextInt(FRAMES.length);
                }

                int w = context.getScaledWindowWidth();
                int h = context.getScaledWindowHeight();
                Identifier tex = FRAMES[currentFrame % FRAMES.length];
                RenderSystem.enableBlend();
                if (CoopImpactHandler.whiteFrame) {
                    RenderSystem.blendFuncSeparate(SrcFactor.DST_COLOR, DstFactor.ZERO, SrcFactor.ONE, DstFactor.ZERO);
                } else {
                    RenderSystem.blendFuncSeparate(SrcFactor.ONE, DstFactor.ONE, SrcFactor.ONE, DstFactor.ZERO);
                }

                RenderSystem.setShader(GameRenderer::getPositionTexProgram);
                RenderSystem.setShaderTexture(0, tex);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
                BufferBuilder buf = Tessellator.getInstance().getBuffer();
                buf.begin(DrawMode.field_27382, VertexFormats.POSITION_TEXTURE);
                buf.vertex(matrix, 0.0F, h, 0.0F).texture(0.0F, 1.0F).next();
                buf.vertex(matrix, w, h, 0.0F).texture(1.0F, 1.0F).next();
                buf.vertex(matrix, w, 0.0F, 0.0F).texture(1.0F, 0.0F).next();
                buf.vertex(matrix, 0.0F, 0.0F, 0.0F).texture(0.0F, 0.0F).next();
                BufferRenderer.drawWithGlobalProgram(buf.end());
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableBlend();
            }
        }
    }
}
