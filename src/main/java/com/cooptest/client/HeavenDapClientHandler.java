package com.cooptest.client;

import com.cooptest.HeavenDapPayloads;
import com.mojang.blaze3d.platform.GlStateManager.DstFactor;
import com.mojang.blaze3d.platform.GlStateManager.SrcFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.cooptest.fabricshim.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.cooptest.fabricshim.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.HudRenderCallback;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.WorldRenderEvents;
import com.cooptest.fabricshim.fabric.api.client.rendering.v1.WorldRenderEvents.BeforeEntities;
import com.cooptest.fabricshim.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.resource.ResourceType;
import net.minecraft.client.render.GameRenderer;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.RegistryByteBuf;
import com.cooptest.fabricshim.mc.PacketCodecs;
import com.cooptest.fabricshim.mc.PacketCodec;
import net.minecraft.client.render.VertexFormat.DrawMode;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import org.joml.Matrix4f;

public class HeavenDapClientHandler {
    private static boolean wasPlayingLastTick = false;

    public static void register() {
        ResourceManagerHelper.get(ResourceType.field_14188).registerReloadListener(CoopImpactRenderType.createReloadListener());
        WorldRenderEvents.BEFORE_ENTITIES.register((BeforeEntities)ctx -> {
            if (CoopImpactHandler.playing) {
                switch (CoopImpactHandler.currentFrameType) {
                    case BLACK:
                        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
                        break;
                    case INVERT:
                        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
                        break;
                    case WHITE:
                        RenderSystem.clearColor(1.0F, 1.0F, 1.0F, 1.0F);
                        break;
                    case RED:
                        RenderSystem.clearColor(1.0F, 0.0F, 0.0F, 1.0F);
                        break;
                    case CYAN:
                        RenderSystem.clearColor(0.0F, 1.0F, 1.0F, 1.0F);
                }

                RenderSystem.clear(16384, false);
            }
        });
        WorldRenderEvents.LAST.register(CoopShockwaveRenderer::render);
        HudRenderCallback.EVENT.register((HudRenderCallback)(context, tickCounter) -> {
            HeavenWhiteOverlay.render(context, tickCounter.getLastFrameDuration());
            if (CoopImpactHandler.playing) {
                int w = context.getScaledWindowWidth();
                int h = context.getScaledWindowHeight();
                Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
                switch (CoopImpactHandler.currentFrameType) {
                    case BLACK:
                        context.fill(0, 0, w, h, -16777216);
                        break;
                    case INVERT:
                        RenderSystem.enableBlend();
                        RenderSystem.blendFuncSeparate(SrcFactor.ONE_MINUS_DST_COLOR, DstFactor.ZERO, SrcFactor.ZERO, DstFactor.ONE);
                        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        BufferBuilder buf = Tessellator.getInstance().getBuffer();
                        buf.begin(DrawMode.field_27382, VertexFormats.POSITION_COLOR);
                        buf.vertex(matrix, 0.0F, h, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).next();
                        buf.vertex(matrix, w, h, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).next();
                        buf.vertex(matrix, w, 0.0F, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).next();
                        buf.vertex(matrix, 0.0F, 0.0F, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).next();
                        BufferRenderer.drawWithGlobalProgram(buf.end());
                        RenderSystem.defaultBlendFunc();
                        RenderSystem.disableBlend();
                }

                CoopSpeedLinesRenderer.render(context);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
            boolean wasPlaying = wasPlayingLastTick;
            wasPlayingLastTick = CoopImpactHandler.playing;
            CoopImpactHandler.tick();
            HeavenWhiteOverlay.tick();
            CoopCameraShakeHandler.tick();
            CoopChromaHandler.tick();
            CoopRadialBlurHandler.tick();
            CoopSpeedLinesRenderer.tick();
            if (wasPlaying && !CoopImpactHandler.playing && !CoopImpactHandler.suppressOverlay) {
                HeavenWhiteOverlay.start();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(h, c) -> c.execute(HeavenWhiteOverlay::stop));
        ClientPlayNetworking.registerGlobalReceiver(HeavenDapPayloads.HeavenDapStartPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            if (!HeavenWhiteOverlay.isActive()) {
                HeavenWhiteOverlay.start();
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(HeavenDapPayloads.HeavenDapEndPayload.ID, (payload, ctx) -> ctx.client().execute(HeavenWhiteOverlay::stop));
        ClientPlayNetworking.registerGlobalReceiver(HeavenDapPayloads.HeavenImpactPayload.ID, (payload, ctx) -> ctx.client().execute(() -> {
            CoopImpactHandler.start(CoopImpactHandler.HEAVEN_DAP_SEQUENCE, 33L, false);
            CoopCameraShakeHandler.shake(1.5F, CoopImpactHandler.HEAVEN_DAP_SEQUENCE.length * 33L);
            CoopChromaHandler.start();
            CoopRadialBlurHandler.start();
            CoopSpeedLinesRenderer.start();
            CoopScreenSquishHandler.trigger();
        }));
    }

    public record QTEButtonPressPayload(String button) implements CustomPayload {
        public static final Identifier QTE_BUTTON_PRESS_ID = new Identifier("cooptest", "qte_button_press");
        public static final Id<HeavenDapClientHandler.QTEButtonPressPayload> ID = new Id(QTE_BUTTON_PRESS_ID);
        public static final PacketCodec<RegistryByteBuf, HeavenDapClientHandler.QTEButtonPressPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, HeavenDapClientHandler.QTEButtonPressPayload::button, HeavenDapClientHandler.QTEButtonPressPayload::new
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
