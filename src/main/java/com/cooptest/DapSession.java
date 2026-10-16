package com.cooptest;

import java.util.UUID;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;

public class DapSession {
    private final UUID playerAId;
    private final UUID playerBId;
    private Vec3d targetA;
    private Vec3d targetB;
    private final long startTime;
    private int tickCount;
    private boolean positioningComplete;
    private final double targetDistance;
    private final double lerpSpeed;
    private final DapSession.DapType type;
    private Runnable onPositioningComplete;
    private boolean cancelled = false;

    public DapSession(UUID playerA, UUID playerB, double targetDistance, DapSession.DapType type) {
        this.playerAId = playerA;
        this.playerBId = playerB;
        this.targetDistance = targetDistance;
        this.type = type;
        this.startTime = System.currentTimeMillis();
        this.tickCount = 0;
        this.positioningComplete = false;
        if (type == DapSession.DapType.PERFECT_DAP) {
            this.lerpSpeed = 0.95;
        } else {
            this.lerpSpeed = 0.75;
        }
    }

    public void onComplete(Runnable callback) {
        this.onPositioningComplete = callback;
    }

    public void tick(MinecraftServer server) {
        if (!this.cancelled) {
            ServerPlayerEntity playerA = server.getPlayerManager().getPlayer(this.playerAId);
            ServerPlayerEntity playerB = server.getPlayerManager().getPlayer(this.playerBId);
            if (playerA != null && playerB != null && !playerA.isRemoved() && !playerB.isRemoved()) {
                if (this.positioningComplete) {
                    this.tickCount++;
                } else if (this.tickCount > 100) {
                    this.forceComplete(playerA, playerB);
                } else {
                    this.freezePlayers(playerA, playerB);
                    this.computeTargets(playerA, playerB);
                    this.smoothMoveToTargets(playerA, playerB);
                    this.makeFaceEachOther(playerA, playerB);
                    playerA.swingHand(Hand.MAIN_HAND);
                    playerB.swingHand(Hand.MAIN_HAND);
                    if (!this.positioningComplete) {
                        this.checkPositioningComplete(playerA, playerB);
                    }

                    this.tickCount++;
                }
            }
        }
    }

    private void freezePlayers(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        playerA.setVelocity(Vec3d.ZERO);
        playerB.setVelocity(Vec3d.ZERO);
        playerA.velocityModified = true;
        playerB.velocityModified = true;
        playerA.fallDistance = 0.0F;
        playerB.fallDistance = 0.0F;
    }

    private void computeTargets(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        Vec3d posA = playerA.getPos();
        Vec3d posB = playerB.getPos();
        Vec3d midpoint = posA.add(posB).multiply(0.5);
        Vec3d direction = posB.subtract(posA);
        if (direction.length() < 0.001) {
            direction = new Vec3d(1.0, 0.0, 0.0);
        }

        direction = direction.normalize();
        double halfDistance = this.targetDistance / 2.0;
        this.targetA = midpoint.subtract(direction.multiply(halfDistance));
        this.targetB = midpoint.add(direction.multiply(halfDistance));
        double targetY = Math.max(posA.y, posB.y);
        ServerWorld world = playerA.getServerWorld();
        BlockPos groundPos = new BlockPos((int)midpoint.x, (int)targetY - 1, (int)midpoint.z);
        if (world.getBlockState(groundPos).isAir()) {
            targetY = Math.min(posA.y, posB.y);
        }

        this.targetA = new Vec3d(this.targetA.x, targetY, this.targetA.z);
        this.targetB = new Vec3d(this.targetB.x, targetY, this.targetB.z);
    }

    private void smoothMoveToTargets(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        Vec3d currentA = playerA.getPos();
        Vec3d currentB = playerB.getPos();
        Vec3d newPosA = new Vec3d(
            this.lerp(currentA.x, this.targetA.x, this.lerpSpeed),
            this.lerp(currentA.y, this.targetA.y, this.lerpSpeed),
            this.lerp(currentA.z, this.targetA.z, this.lerpSpeed)
        );
        Vec3d newPosB = new Vec3d(
            this.lerp(currentB.x, this.targetB.x, this.lerpSpeed),
            this.lerp(currentB.y, this.targetB.y, this.lerpSpeed),
            this.lerp(currentB.z, this.targetB.z, this.lerpSpeed)
        );
        playerA.teleport(playerA.getServerWorld(), newPosA.x, newPosA.y, newPosA.z, playerA.getYaw(), playerA.getPitch());
        playerB.teleport(playerB.getServerWorld(), newPosB.x, newPosB.y, newPosB.z, playerB.getYaw(), playerB.getPitch());
    }

    private void makeFaceEachOther(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        Vec3d posA = playerA.getPos();
        Vec3d posB = playerB.getPos();
        double dx = posB.x - posA.x;
        double dz = posB.z - posA.z;
        float yawA = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float yawB = yawA + 180.0F;
        playerA.setYaw(yawA);
        playerA.setBodyYaw(yawA);
        playerA.setHeadYaw(yawA);
        playerA.prevYaw = yawA;
        playerB.setYaw(yawB);
        playerB.setBodyYaw(yawB);
        playerB.setHeadYaw(yawB);
        playerB.prevYaw = yawB;
        playerA.teleport(playerA.getServerWorld(), posA.x, posA.y, posA.z, yawA, playerA.getPitch());
        playerB.teleport(playerB.getServerWorld(), posB.x, posB.y, posB.z, yawB, playerB.getPitch());
    }

    private void checkPositioningComplete(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        double distA = playerA.getPos().distanceTo(this.targetA);
        double distB = playerB.getPos().distanceTo(this.targetB);
        double threshold = this.type == DapSession.DapType.PERFECT_DAP ? 0.35 : 0.25;
        if (distA < threshold && distB < threshold) {
            this.positioningComplete = true;
            if (this.onPositioningComplete != null) {
                this.onPositioningComplete.run();
            }

            this.tickCount = 99;
        }
    }

    private void forceComplete(ServerPlayerEntity playerA, ServerPlayerEntity playerB) {
        if (!this.positioningComplete) {
            this.positioningComplete = true;
            if (this.onPositioningComplete != null) {
                this.onPositioningComplete.run();
            }
        }
    }

    private double lerp(double current, double target, double factor) {
        return current + (target - current) * factor;
    }

    public UUID getPlayerAId() {
        return this.playerAId;
    }

    public UUID getPlayerBId() {
        return this.playerBId;
    }

    public boolean isPositioningComplete() {
        return this.positioningComplete;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public int getTickCount() {
        return this.tickCount;
    }

    public DapSession.DapType getType() {
        return this.type;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public Vec3d getTargetA() {
        return this.targetA;
    }

    public Vec3d getTargetB() {
        return this.targetB;
    }

    public static enum DapType {
        NORMAL_DAP,
        PERFECT_DAP,
        FIRE_DAP,
        FIRE_COMBO,
        DAP_HOLD;
    }
}
