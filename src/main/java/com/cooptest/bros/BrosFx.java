package com.cooptest.bros;

import com.cooptest.PoseNetworking;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;

public final class BrosFx {
    public static final boolean BASE_CLIP_IS_LEFT_BRO = true;
    public static final int START_ANIM_TICKS = 11;
    public static final int PAT_TICK = 6;
    public static final double STEP_STRIDE = 0.85;
    public static final float STEP_VOLUME = 0.18F;
    public static final int BOND_SPARK_TICKS = 14;
    public static final double SHOULDER_HEIGHT = 1.45;
    private static final int ANIM_NONE = 0;
    private static final int ANIM_BROS_START = 129;
    private static final int ANIM_BROS_IDLE = 130;
    private static final int ANIM_BROS_END = 131;
    private static final int ANIM_BROS_START_MIRROR = 132;
    private static final int ANIM_BROS_IDLE_MIRROR = 133;
    private static final int ANIM_BROS_END_MIRROR = 134;

    private BrosFx() {
    }

    static void onStart(BrosHandler.Session s) {
        broadcast(s, 129, 132, null);
    }

    static void tick(BrosHandler.Session s) {
        if (s.world instanceof ServerWorld sw) {
            if (s.ticks == 11) {
                broadcast(s, 130, 133, null);
            }

            double x = s.cx;
            double z = s.cz;
            double y = Math.min(s.ya, s.yb) + 1.45;
            if (s.ticks == 6) {
                sw.playSound(null, x, y, z, (SoundEvent)SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.8F, 1.25F);
                sw.playSound(null, x, y, z, (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.35F, 1.5F);
                sw.spawnParticles(ParticleTypes.WAX_ON, x, y, z, 4, 0.25, 0.08, 0.25, 0.0);
            }

            if (s.ticks > 11 && s.ticks % 14 == 0) {
                sw.spawnParticles(ParticleTypes.WAX_ON, x, y + 0.1, z, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
    }

    static void onMoved(BrosHandler.Session s, double dist) {
        if (!(dist <= 0.0)) {
            s.stepDist += dist;
            if (!(s.stepDist < 0.85)) {
                s.stepDist -= 0.85;
                if (s.world instanceof ServerWorld sw) {
                    step(sw, s.a);
                    step(sw, s.b);
                }
            }
        }
    }

    static void onEnd(BrosHandler.Session s, UUID skip) {
        broadcast(s, 131, 134, skip);
        if (s.world instanceof ServerWorld sw) {
            sw.playSound(null, s.cx, Math.min(s.ya, s.yb) + 1.45, s.cz, (SoundEvent)SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.6F, 0.9F);
        }
    }

    private static void step(ServerWorld sw, LivingEntity e) {
        BlockPos below = BlockPos.ofFloored(e.getX(), e.getY() - 0.2, e.getZ());
        BlockState state = sw.getBlockState(below);
        if (!state.isAir()) {
            BlockSoundGroup g = state.getSoundGroup();
            sw.playSound(
                null,
                e.getX(),
                e.getY(),
                e.getZ(),
                g.getStepSound(),
                SoundCategory.PLAYERS,
                g.getVolume() * 0.18F,
                g.getPitch()
            );
        }
    }

    private static void broadcast(BrosHandler.Session s, int leftAnim, int rightAnim, UUID skip) {
        send(s.a, s.aLeft, leftAnim, rightAnim, skip);
        send(s.b, !s.aLeft, leftAnim, rightAnim, skip);
    }

    private static void send(LivingEntity e, boolean isLeftBro, int leftAnim, int rightAnim, UUID skip) {
        if (e instanceof ServerPlayerEntity p && !p.isRemoved()) {
            if (skip == null || !p.getUuid().equals(skip)) {
                boolean base = isLeftBro;

                try {
                    PoseNetworking.broadcastAnimState(p, base ? leftAnim : rightAnim);
                } catch (Throwable var8) {
                    System.err.println("[Bros] anim broadcast failed: " + var8);
                }
            }
        }
    }

    static void forceNone(BrosHandler.Session s) {
        for (LivingEntity e : new LivingEntity[]{s.a, s.b}) {
            if (e instanceof ServerPlayerEntity p && !p.isRemoved()) {
                try {
                    PoseNetworking.broadcastAnimState(p, 0);
                } catch (Throwable var7) {
                }
            }
        }
    }
}
