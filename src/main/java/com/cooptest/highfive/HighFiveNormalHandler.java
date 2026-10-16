package com.cooptest.highfive;

import com.cooptest.HighFiveHandler;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;

public class HighFiveNormalHandler {
    private static final java.util.UUID FAST_SLOW_ID = com.cooptest.fabricshim.mc.ModifierIds.of(new Identifier("testcoop", "highfive_fast_slow"));
    private static final double FAST_SLOW_MULT = -0.35;
    private static final long FAST_SLOW_DUR_MS = 400L;
    private static final List<HighFiveNormalHandler.PendingRepos> pendingSlide = new ArrayList<>();
    private static final List<HighFiveNormalHandler.PendingRepos> pendingPull = new ArrayList<>();
    private static final List<HighFiveNormalHandler.PendingPush> pendingPush = new ArrayList<>();
    private static final List<HighFiveNormalHandler.PendingRock> pendingRock = new ArrayList<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            long now = System.currentTimeMillis();
            drainRepos(pendingSlide);
            drainRepos(pendingPull);
            Iterator<HighFiveNormalHandler.PendingPush> pushIt = pendingPush.iterator();

            while (pushIt.hasNext()) {
                HighFiveNormalHandler.PendingPush p = pushIt.next();
                if (now >= p.untilMs()) {
                    pushIt.remove();
                } else {
                    Vec3d pos = p.player().getPos();
                    p.player().requestTeleport(pos.x + p.nudgePerTick().x, pos.y, pos.z + p.nudgePerTick().z);
                }
            }

            Iterator<HighFiveNormalHandler.PendingRock> rockIt = pendingRock.iterator();

            while (rockIt.hasNext()) {
                HighFiveNormalHandler.PendingRock r = rockIt.next();
                Vec3d step = r.phase == 0 ? r.fwdStep : r.backStep;
                Vec3d pos = r.player.getPos();
                r.player.requestTeleport(pos.x + step.x, pos.y, pos.z + step.z);
                if (--r.ticksLeft <= 0) {
                    if (r.phase == 0) {
                        r.phase = 1;
                        r.ticksLeft = r.backTicks;
                    } else {
                        rockIt.remove();
                    }
                }
            }
        });
    }

    private static void drainRepos(List<HighFiveNormalHandler.PendingRepos> list) {
        Iterator<HighFiveNormalHandler.PendingRepos> it = list.iterator();

        while (it.hasNext()) {
            HighFiveNormalHandler.PendingRepos r = it.next();
            if (r.remainingTicks <= 0) {
                it.remove();
            } else {
                Vec3d step = r.remainingDelta.multiply(1.0 / r.remainingTicks);
                Vec3d pos = r.player.getPos();
                r.player.requestTeleport(pos.x + step.x, pos.y, pos.z + step.z);
                r.remainingDelta = r.remainingDelta.subtract(step);
                if (--r.remainingTicks <= 0) {
                    it.remove();
                }
            }
        }
    }

    public static void executeNormal(ServerPlayerEntity p1, ServerPlayerEntity p2, long now) {
        Vec3d[] targets = computeTargets(p1, p2, 1.1, 0.2);
        int slideTicks = msToTicks(290L);
        queueSlide(p1, p1.getPos(), targets[0], slideTicks);
        queueSlide(p2, p2.getPos(), targets[1], slideTicks);
        Vec3d back1 = HighFiveHandler.horizontalForward(p1).multiply(-0.4);
        Vec3d back2 = HighFiveHandler.horizontalForward(p2).multiply(-0.4);
        long delay = 540L;
        new Thread(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException var7x) {
            }

            p1.getServer().execute(() -> {
                pendingPull.add(new HighFiveNormalHandler.PendingRepos(p1, back1, 5));
                pendingPull.add(new HighFiveNormalHandler.PendingRepos(p2, back2, 5));
            });
        }).start();
    }

    public static void executeFast(ServerPlayerEntity p1, ServerPlayerEntity p2, long now) {
        Vec3d[] targets = computeTargets(p1, p2, 1.1, 0.2);
        int slideTicks = msToTicks(290L);
        queueSlide(p1, p1.getPos(), targets[0], slideTicks);
        queueSlide(p2, p2.getPos(), targets[1], slideTicks);
    }

    public static void executeMixed(ServerPlayerEntity fastP, ServerPlayerEntity stillP, long now) {
        Vec3d[] targets = computeTargets(fastP, stillP, 1.1, 0.2);
        int slideTicks = msToTicks(290L);
        queueSlide(fastP, fastP.getPos(), targets[0], slideTicks);
        queueSlide(stillP, stillP.getPos(), targets[1], slideTicks);
        Vec3d backStill = HighFiveHandler.horizontalForward(stillP).multiply(-0.4);
        long delay = 540L;
        new Thread(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException var5x) {
            }

            stillP.getServer().execute(() -> pendingPull.add(new HighFiveNormalHandler.PendingRepos(stillP, backStill, 5)));
        }).start();
    }

    public static void executeNormalSolo(ServerPlayerEntity player, long now) {
        Vec3d fwd = HighFiveHandler.horizontalForward(player);
        Vec3d right = rightOf(fwd);
        Vec3d target = player.getPos().add(fwd.multiply(0.55)).add(right.multiply(0.2));
        queueSlide(player, player.getPos(), target, msToTicks(290L));
        Vec3d back = fwd.multiply(-0.4);
        long delay = 540L;
        new Thread(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException var5x) {
            }

            player.getServer().execute(() -> pendingPull.add(new HighFiveNormalHandler.PendingRepos(player, back, 5)));
        }).start();
    }

    public static void executeFastSolo(ServerPlayerEntity player, long now) {
        Vec3d fwd = HighFiveHandler.horizontalForward(player);
        Vec3d right = rightOf(fwd);
        Vec3d target = player.getPos().add(fwd.multiply(0.45)).add(right.multiply(0.2));
        queueSlide(player, player.getPos(), target, msToTicks(210L));
        long impactMs = now + 210L;
        long animEndMs = now + 1208L;
        Vec3d nudge = fwd.multiply(0.04);
        new Thread(() -> {
            try {
                Thread.sleep(impactMs - now);
            } catch (InterruptedException var9) {
            }

            player.getServer().execute(() -> pendingPush.add(new HighFiveNormalHandler.PendingPush(player, nudge, animEndMs)));
        }).start();
    }

    public static void executeMixedSoloFast(ServerPlayerEntity player, long now) {
        Vec3d right = rightOf(HighFiveHandler.horizontalForward(player));
        Vec3d target = player.getPos().add(right.multiply(0.2));
        queueSlide(player, player.getPos(), target, msToTicks(210L));
    }

    private static void applyFastSlow(ServerPlayerEntity player) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(FAST_SLOW_ID);
            attr.addTemporaryModifier(new EntityAttributeModifier(FAST_SLOW_ID, "fast_slow_id", -0.35, Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeFastSlow(ServerPlayerEntity player) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(FAST_SLOW_ID);
        }
    }

    private static Vec3d[] computeTargets(ServerPlayerEntity p1, ServerPlayerEntity p2, double separation, double rightOffset) {
        Vec3d pos1 = p1.getPos();
        Vec3d pos2 = p2.getPos();
        Vec3d toP2 = pos2.subtract(pos1);
        double dist = Math.hypot(toP2.x, toP2.z);
        if (dist < 0.01) {
            toP2 = HighFiveHandler.horizontalForward(p1);
            dist = 1.0;
        }

        Vec3d dir12 = new Vec3d(toP2.x / dist, 0.0, toP2.z / dist);
        Vec3d dir21 = dir12.negate();
        double fwdStep = Math.max(0.0, Math.min((dist - separation) / 2.0, separation * 0.3));
        Vec3d right1 = rightOf(HighFiveHandler.horizontalForward(p1));
        Vec3d right2 = rightOf(HighFiveHandler.horizontalForward(p2));
        Vec3d t1 = new Vec3d(
            pos1.x + dir12.x * fwdStep + right1.x * rightOffset,
            pos1.y,
            pos1.z + dir12.z * fwdStep + right1.z * rightOffset
        );
        Vec3d t2 = new Vec3d(
            pos2.x + dir21.x * fwdStep + right2.x * rightOffset,
            pos2.y,
            pos2.z + dir21.z * fwdStep + right2.z * rightOffset
        );
        return new Vec3d[]{t1, t2};
    }

    private static Vec3d rightOf(Vec3d fwd) {
        return new Vec3d(fwd.z, 0.0, -fwd.x);
    }

    private static void queueSlide(ServerPlayerEntity player, Vec3d from, Vec3d to, int ticks) {
        Vec3d delta = new Vec3d(to.x - from.x, 0.0, to.z - from.z);
        if (!(Math.hypot(delta.x, delta.z) > 2.0)) {
            pendingSlide.add(new HighFiveNormalHandler.PendingRepos(player, delta, ticks));
        }
    }

    private static int msToTicks(long ms) {
        return (int)Math.max(1L, ms / 50L);
    }

    private record PendingPush(ServerPlayerEntity player, Vec3d nudgePerTick, long untilMs) {
    }

    private static final class PendingRepos {
        final ServerPlayerEntity player;
        Vec3d remainingDelta;
        int remainingTicks;

        PendingRepos(ServerPlayerEntity player, Vec3d totalDelta, int ticks) {
            this.player = player;
            this.remainingDelta = totalDelta;
            this.remainingTicks = Math.max(1, ticks);
        }
    }

    private static final class PendingRock {
        final ServerPlayerEntity player;
        int phase;
        int ticksLeft;
        final Vec3d fwdStep;
        final Vec3d backStep;
        final int backTicks;

        PendingRock(ServerPlayerEntity player, Vec3d forward, int fwdTicks, double fwdMag, int backTicks, double backMag) {
            this.player = player;
            this.phase = 0;
            this.fwdStep = forward.multiply(fwdMag / fwdTicks);
            this.backStep = forward.multiply(-backMag / backTicks);
            this.ticksLeft = fwdTicks;
            this.backTicks = backTicks;
        }
    }
}
