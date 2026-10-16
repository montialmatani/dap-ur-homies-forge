package com.cooptest.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Random;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat.DrawMode;
import org.joml.Matrix4f;

public class CoopSpeedLinesRenderer {
    private static final int LINE_COUNT = 80;
    private static final float MIN_LENGTH = 0.08F;
    private static final float MAX_LENGTH = 0.35F;
    private static final float LINE_WIDTH = 1.5F;
    private static final long DURATION_MS = 300L;
    private static final Random RANDOM = new Random();
    private static final float[] ANGLES = new float[80];
    private static final float[] LENGTHS = new float[80];
    private static final float[] OFFSETS = new float[80];
    private static boolean linesGenerated = false;
    private static boolean active = false;
    private static long startMs = 0L;

    public static void start() {
        active = true;
        startMs = System.currentTimeMillis();
        generateLines();
    }

    private static void generateLines() {
        for (int i = 0; i < 80; i++) {
            ANGLES[i] = RANDOM.nextFloat() * 360.0F;
            LENGTHS[i] = 0.08F + RANDOM.nextFloat() * 0.26999998F;
            OFFSETS[i] = 0.15F + RANDOM.nextFloat() * 0.25F;
        }

        linesGenerated = true;
    }

    public static void tick() {
        if (active && System.currentTimeMillis() - startMs > 300L) {
            active = false;
        }
    }

    public static void render(DrawContext context) {
        if (active) {
            long elapsed = System.currentTimeMillis() - startMs;
            float progress = (float)elapsed / 300.0F;
            float alpha = (1.0F - progress) * 0.85F;
            int w = context.getScaledWindowWidth();
            int h = context.getScaledWindowHeight();
            float cx = w * 0.5F;
            float cy = h * 0.5F;
            float diag = (float)Math.sqrt(w * w + h * h) * 0.5F;
            MatrixStack matrices = context.getMatrices();
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.lineWidth(1.5F);
            float r = 1.0F;
            float g = 1.0F;
            float b = 1.0F;
            if (CoopImpactHandler.playing) {
                switch (CoopImpactHandler.currentFrameType) {
                    case WHITE:
                    case RED:
                        r = 0.0F;
                        g = 0.0F;
                        b = 0.0F;
                        break;
                    case BLACK:
                    case CYAN:
                        r = 1.0F;
                        g = 1.0F;
                        b = 1.0F;
                        break;
                    case INVERT:
                        r = 1.0F;
                        g = 1.0F;
                        b = 0.0F;
                }
            }

            Tessellator tess = Tessellator.getInstance();

            for (int i = 0; i < 80; i++) {
                double angleRad = Math.toRadians(ANGLES[i]);
                float dx = (float)Math.cos(angleRad);
                float dy = (float)Math.sin(angleRad);
                float startDist = OFFSETS[i] * diag;
                float endDist = (OFFSETS[i] + LENGTHS[i]) * diag;
                float animStart = startDist + progress * diag * 0.3F;
                float animEnd = endDist + progress * diag * 0.3F;
                float x1 = cx + dx * animStart;
                float y1 = cy + dy * animStart;
                float x2 = cx + dx * animEnd;
                float y2 = cy + dy * animEnd;
                BufferBuilder buf = tess.getBuffer();
                buf.begin(DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                buf.vertex(matrix, x1, y1, 0.0F).color(r, g, b, alpha).next();
                buf.vertex(matrix, x2, y2, 0.0F).color(r, g, b, 0.0F).next();
                BufferRenderer.drawWithGlobalProgram(buf.end());
            }

            RenderSystem.lineWidth(1.0F);
            RenderSystem.disableBlend();
        }
    }

    public static boolean isActive() {
        return active;
    }
}
