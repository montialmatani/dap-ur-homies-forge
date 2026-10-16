package com.cooptest.client.anim.mixin;

import com.cooptest.client.anim.CoopAnimLib;
import com.cooptest.client.anim.FirstPersonMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PAL LevelRendererMixin: briefly pretend the camera is detached so the local player model gets drawn. */
@Mixin(WorldRenderer.class)
public abstract class FirstPersonWorldRendererMixin {
    @Unique
    private boolean coopanim$defaultCameraState;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;isThirdPerson()Z"))
    private void coopanim$fakeThirdPerson(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmap, Matrix4f projection, CallbackInfo ci) {
        coopanim$defaultCameraState = camera.isThirdPerson();
        Entity focused = camera.getFocusedEntity();
        if (CoopAnimLib.thirdPersonModelActive(focused)) {
            FirstPersonMode.setFirstPersonPass(!camera.isThirdPerson() && (!(focused instanceof LivingEntity le) || !le.isSleeping()));
            ((CameraAccessor) camera).coopanim$setThirdPerson(true);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;isThirdPerson()Z", shift = At.Shift.AFTER))
    private void coopanim$resetThirdPerson(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmap, Matrix4f projection, CallbackInfo ci) {
        ((CameraAccessor) camera).coopanim$setThirdPerson(coopanim$defaultCameraState);
    }

    @Inject(method = "renderEntity", at = @At("TAIL"))
    private void coopanim$endPass(Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (entity == MinecraftClient.getInstance().gameRenderer.getCamera().getFocusedEntity()) {
            FirstPersonMode.setFirstPersonPass(false);
        }
    }
}
