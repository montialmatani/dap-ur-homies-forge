package com.cooptest.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat.DrawMode;
import org.joml.Matrix4f;

public class CoopShockwaveRenderer {
    private static final long DURATION_MS = 450L;
    private static final float MAX_RADIUS = 2.5F;
    private static final int RINGS = 3;
    private static final int SEGMENTS = 48;
    private static Vec3d center = null;
    private static long startMs = 0L;

    public static void start(Vec3d position) {
        center = position;
        startMs = System.currentTimeMillis();
    }

    public static void render(WorldRenderContext context) {
        if (center != null) {
            long elapsed = System.currentTimeMillis() - startMs;
            if (elapsed > 450L) {
                center = null;
            } else {
                float progress = (float)elapsed / 450.0F;
                float alpha = (1.0F - progress) * (1.0F - progress);
                Vec3d camPos = context.camera().getPos();
                MatrixStack matrices = new MatrixStack();
                matrices.translate(center.x - camPos.x, center.y - camPos.y, center.z - camPos.z);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableDepthTest();
                RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                RenderSystem.lineWidth(2.0F);
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                Tessellator tess = Tessellator.getInstance();

                for (int ring = 0; ring < 3; ring++) {
                    float ringProgress = (progress + ring * 0.15F) % 1.0F;
                    float radius = ringProgress * 2.5F;
                    float ringAlpha = alpha * (1.0F - ring * 0.25F);
                    if (!(ringAlpha <= 0.0F)) {
                        BufferBuilder buf = tess.getBuffer();
                        buf.begin(DrawMode.field_29345, VertexFormats.POSITION_COLOR);

                        for (int i = 0; i <= 48; i++) {
                            double angle = i / 48.0 * Math.PI * 2.0;
                            float x = (float)(Math.cos(angle) * radius);
                            float z = (float)(Math.sin(angle) * radius);
                            buf.vertex(matrix, x, 0.0F, z).color(1.0F, 1.0F, 1.0F, ringAlpha).next();
                        }

                        BufferRenderer.drawWithGlobalProgram(buf.end());
                    }
                }

                RenderSystem.enableDepthTest();
                RenderSystem.disableBlend();
                RenderSystem.lineWidth(1.0F);
            }
        }
    }
}
