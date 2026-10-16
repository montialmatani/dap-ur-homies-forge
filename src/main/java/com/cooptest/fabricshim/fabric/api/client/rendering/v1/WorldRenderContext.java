package com.cooptest.fabricshim.fabric.api.client.rendering.v1;
import com.cooptest.fabricshim.mc.RenderTickCounter;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
/** Snapshot of Forge's RenderLevelStageEvent exposed with Fabric's WorldRenderContext accessors. */
public record WorldRenderContext(WorldRenderer worldRenderer, MatrixStack matrixStack, RenderTickCounter tickCounter,
                                 Camera camera, GameRenderer gameRenderer, Matrix4f projectionMatrix,
                                 ClientWorld world, VertexConsumerProvider consumers, Frustum frustum) {
    public Matrix4f positionMatrix() { return matrixStack.peek().getPositionMatrix(); }
}
