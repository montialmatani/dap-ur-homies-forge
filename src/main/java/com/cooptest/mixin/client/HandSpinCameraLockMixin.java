package com.cooptest.mixin.client;

import com.cooptest.highfive.client.ReadyHugClientHandler;
import com.cooptest.spin.client.HandSpinClientHandler;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Mouse.class})
public class HandSpinCameraLockMixin {
    @Shadow
    private double cursorDeltaX;
    @Unique
    private static long coopmod$lastFlyNanos = -1L;
    @Unique
    private static final float FLY_SPIN_DEG_PER_SEC = 360.0F;

    @Inject(
        method = {"updateMouse"},
        at = {@At("HEAD")}
    )
    private void coopmod$eatHorizontalLook(CallbackInfo ci) {
        if (HandSpinClientHandler.isLocalPlayerSpinning() || HandSpinClientHandler.isLocalPlayerMonkeFlying() || ReadyHugClientHandler.isLocalPlayerInHug()) {
            this.cursorDeltaX = 0.0;
        }
    }

    @Inject(
        method = {"updateMouse"},
        at = {@At("TAIL")}
    )
    private void coopmod$faceCenter(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player != null) {
            if (HandSpinClientHandler.isLocalPlayerMonkeFlying()) {
                long nowNanos = System.nanoTime();
                if (coopmod$lastFlyNanos > 0L) {
                    float dt = (float)(nowNanos - coopmod$lastFlyNanos) / 1.0E9F;
                    float yaw = MathHelper.wrapDegrees(player.getYaw() + 360.0F * dt);
                    HandSpinClientHandler.applyLockedYaw(player, yaw);
                }

                coopmod$lastFlyNanos = nowNanos;
            } else {
                coopmod$lastFlyNanos = -1L;
                if (ReadyHugClientHandler.isLocalPlayerInHug()) {
                    UUID pid = ReadyHugClientHandler.getPartnerId();
                    if (pid != null && client.world != null) {
                        for (AbstractClientPlayerEntity other : client.world.getPlayers()) {
                            if (other.getUuid().equals(pid)) {
                                double hdx = other.getX() - player.getX();
                                double hdz = other.getZ() - player.getZ();
                                if (hdx * hdx + hdz * hdz < 1.0E-6) {
                                    return;
                                }

                                float hugYaw = (float)Math.toDegrees(Math.atan2(-hdx, hdz));
                                HandSpinClientHandler.applyLockedYaw(player, hugYaw);
                                return;
                            }
                        }
                    }
                } else if (HandSpinClientHandler.isLocalPlayerSpinning()) {
                    Vec3d center = HandSpinClientHandler.getLockedCenter();
                    if (center != null) {
                        float tickDelta = com.cooptest.fabricshim.mc.RenderTickCounter.current().getTickDelta(true);
                        Vec3d rp = player.getLerpedPos(tickDelta);
                        double dx = rp.x - center.x;
                        double dz = rp.z - center.z;
                        if (!(dx * dx + dz * dz < 1.0E-6)) {
                            float targetYaw = (float)Math.toDegrees(Math.atan2(dx, -dz));
                            HandSpinClientHandler.applyLockedYaw(player, targetYaw);
                        }
                    }
                }
            }
        }
    }
}
