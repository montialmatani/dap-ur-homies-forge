package com.cooptest.client;

import java.io.IOException;
import java.util.function.Supplier;
import com.cooptest.fabricshim.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.resource.ResourceManager;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.RenderLayer.MultiPhaseParameters;
import net.minecraft.client.render.VertexFormat.DrawMode;
import org.jetbrains.annotations.Nullable;

public class CoopImpactRenderType {
    @Nullable
    private static ShaderProgram entityWhiteProgram = null;
    @Nullable
    private static ShaderProgram entityBlackProgram = null;
    @Nullable
    private static ShaderProgram entityInvertProgram = null;

    public static void reload(ResourceManager manager) {
        closeAll();
        entityWhiteProgram = load(manager, "cooptest_entity_white");
        entityBlackProgram = load(manager, "cooptest_entity_black");
        entityInvertProgram = load(manager, "cooptest_entity_invert");
    }

    private static ShaderProgram load(ResourceManager manager, String name) {
        try {
            return new ShaderProgram(manager, name, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        } catch (IOException var3) {
            System.err.println("[CoopMod] " + name + " shader failed: " + var3.getMessage());
            return null;
        }
    }

    private static void closeAll() {
        if (entityWhiteProgram != null) {
            entityWhiteProgram.close();
            entityWhiteProgram = null;
        }

        if (entityBlackProgram != null) {
            entityBlackProgram.close();
            entityBlackProgram = null;
        }

        if (entityInvertProgram != null) {
            entityInvertProgram.close();
            entityInvertProgram = null;
        }
    }

    public static boolean isReady() {
        return entityWhiteProgram != null && entityBlackProgram != null;
    }

    @Nullable
    public static ShaderProgram getWhiteProgram() {
        return entityWhiteProgram;
    }

    @Nullable
    public static ShaderProgram getBlackProgram() {
        return entityBlackProgram;
    }

    @Nullable
    public static ShaderProgram getInvertProgram() {
        return entityInvertProgram != null ? entityInvertProgram : entityWhiteProgram;
    }

    public static RenderLayer getWhiteLayer(Identifier texture) {
        return buildLayer("cooptest_entity_white", texture, CoopImpactRenderType::getWhiteProgram);
    }

    public static RenderLayer getBlackLayer(Identifier texture) {
        return buildLayer("cooptest_entity_black", texture, CoopImpactRenderType::getBlackProgram);
    }

    public static RenderLayer getInvertLayer(Identifier texture) {
        return buildLayer("cooptest_entity_invert", texture, CoopImpactRenderType::getInvertProgram);
    }

    private static RenderLayer buildLayer(String name, Identifier texture, Supplier<ShaderProgram> program) {
        return program.get() == null ? RenderLayer.getEntityCutout(texture) : Layers.build(name, texture, program);
    }

    /** Subclass of RenderLayer purely to reach RenderPhase's protected phases (Fabric widens these, Forge doesn't). */
    private abstract static class Layers extends RenderLayer {
        private Layers() {
            super("coop_dummy", VertexFormats.POSITION, DrawMode.QUADS, 256, false, false, () -> {}, () -> {});
        }

        static RenderLayer build(String name, Identifier texture, Supplier<ShaderProgram> program) {
            return RenderLayer.of(
                name,
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                DrawMode.QUADS,
                1536,
                false,
                false,
                MultiPhaseParameters.builder()
                    .program(new RenderPhase.ShaderProgram(program))
                    .texture(new RenderPhase.Texture(texture, false, false))
                    .transparency(NO_TRANSPARENCY)
                    .lightmap(ENABLE_LIGHTMAP)
                    .overlay(ENABLE_OVERLAY_COLOR)
                    .build(false)
            );
        }
    }

    public static SimpleSynchronousResourceReloadListener createReloadListener() {
        return new SimpleSynchronousResourceReloadListener() {
            public Identifier getFabricId() {
                return new Identifier("cooptest", "impact_shaders");
            }

            public void reload(ResourceManager manager) {
                CoopImpactRenderType.reload(manager);
            }
        };
    }
}
