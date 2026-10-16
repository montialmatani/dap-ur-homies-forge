package com.cooptest.client;

import com.cooptest.GrabInputHandler;
import com.cooptest.PoseNetworking;
import com.cooptest.PoseState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.WorldRenderContext;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat.DrawMode;
import org.joml.Matrix4f;

public class TrajectoryRenderer {
    private static final int TRAJECTORY_POINTS = 30;
    private static final float TIME_STEP = 0.1F;
    private static final float GRAVITY = 0.08F;
    private static final float DRAG = 0.02F;
    private static final float DOT_SIZE = 0.08F;
    private static final float MIN_POWER_MULT = 1.5F;
    private static final float MAX_POWER_MULT = 3.5F;

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(TrajectoryRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.world != null) {
            PoseState pose = PoseNetworking.poseStates.getOrDefault(client.player.getUuid(), PoseState.NONE);
            if (pose == PoseState.GRAB_HOLDING) {
                float chargeProgress = GrabInputHandler.getThrowChargeProgress();
                if (!(chargeProgress <= 0.0F)) {
                    float power = 1.5F + 2.0F * chargeProgress;
                    Vec3d lookVec = client.player.getRotationVec(context.tickCounter().getTickDelta(true));
                    Vec3d startPos = client.player.getEyePos().add(0.0, 0.5, 0.0);
                    Vec3d velocity = lookVec.multiply(power);
                    Vec3d[] points = new Vec3d[30];
                    Vec3d pos = startPos;
                    Vec3d vel = velocity;

                    for (int i = 0; i < 30; i++) {
                        points[i] = pos;
                        Vec3d var12 = vel.add(0.0, -0.08F, 0.0);
                        vel = var12.multiply(0.9800000004470348);
                        pos = pos.add(vel);
                        if (pos.y < client.player.getY() - 10.0) {
                            break;
                        }
                    }

                    renderTrajectoryDots(context, points, chargeProgress);
                }
            }
        }
    }

    private static void renderTrajectoryDots(WorldRenderContext context, Vec3d[] points, float charge) {
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = context.camera();
        Vec3d camPos = camera.getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int r = (int)(charge * 255.0F);
        int g = (int)((1.0F - charge) * 255.0F);
        int b = 50;
        int alpha = 200;

        for (int i = 0; i < points.length && points[i] != null; i++) {
            Vec3d point = points[i];
            float fadeAlpha = alpha * (1.0F - (float)i / points.length);
            float size = 0.08F * (1.0F - (float)i / points.length * 0.5F);
            float x = (float)point.x;
            float y = (float)point.y;
            float z = (float)point.z;
            buffer.vertex(matrix, x - size, y - size, z + size).color(r, g, b, (int)fadeAlpha).next();
            buffer.vertex(matrix, x + size, y - size, z + size).color(r, g, b, (int)fadeAlpha).next();
            buffer.vertex(matrix, x + size, y + size, z + size).color(r, g, b, (int)fadeAlpha).next();
            buffer.vertex(matrix, x - size, y + size, z + size).color(r, g, b, (int)fadeAlpha).next();
            buffer.vertex(matrix, x + size, y - size, z - size).color(r, g, b, (int)fadeAlpha).next();
            buffer.vertex(matrix, x - size, y - size, z - size).color(r, g, b, (int)fadeAlpha).next();
            buffer.vertex(matrix, x - size, y + size, z - size).color(r, g, b, (int)fadeAlpha).next();
            buffer.vertex(matrix, x + size, y + size, z - size).color(r, g, b, (int)fadeAlpha).next();
            buffer.vertex(matrix, x - size, y + size, z - size).color(r, g, b, (int)fadeAlpha).next();
            buffer.vertex(matrix, x - size, y + size, z + size).color(r, g, b, (int)fadeAlpha).next();
            buffer.vertex(matrix, x + size, y + size, z + size).color(r, g, b, (int)fadeAlpha).next();
            buffer.vertex(matrix, x + size, y + size, z - size).color(r, g, b, (int)fadeAlpha).next();
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        matrices.pop();
    }
}
