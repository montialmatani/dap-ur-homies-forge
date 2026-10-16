package com.cooptest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Box;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.Heightmap.Type;

public class HeavenCrater {
    private static final int MAX_RADIUS = 400;
    private static final float MOB_DAMAGE = 1000.0F;
    public static final int START_DELAY_TICKS = 16;
    public static final int BLAST_FX_EVERY_TICKS = 2;
    public static final int BLAST_FX_POINTS = 18;
    public static final double FX_VIEW_RANGE = 512.0;
    private static final int FLAGS = 50;
    private static final List<HeavenCrater.Job> jobs = new ArrayList<>();
    private static int[] cachedOffsets = null;
    private static int cachedRadius = -1;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> tick());
        ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> jobs.clear());
    }

    public static Vec3d start(ServerWorld world, Vec3d handPos, ServerPlayerEntity p1, ServerPlayerEntity p2, boolean carve) {
        int x = MathHelper.floor(handPos.x);
        int z = MathHelper.floor(handPos.z);
        int feetY = MathHelper.floor(handPos.y - 1.4);
        int surfaceY = world.getTopY(Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        int groundY = Math.min(feetY, surfaceY);
        HeavenCrater.Settings cfg = new HeavenCrater.Settings(CoopMovesConfig.get(), carve);
        jobs.add(new HeavenCrater.Job(world, x, groundY, z, cfg, p1, p2));
        int floorY = cfg.carve ? Math.max(world.getBottomY() + 1, groundY - cfg.depth) : groundY;
        return new Vec3d(x + 0.5, floorY, z + 0.5);
    }

    public static void returnBlast(ServerWorld world, Vec3d pos) {
        forceParticles(world, ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1.0, pos.z, 25, 8.0, 3.0, 8.0, 0.0);
        forceParticles(world, ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 5, 0.0, 0.0, 0.0, 0.0);
        forceParticles(world, ParticleTypes.CLOUD, pos.x, pos.y + 0.5, pos.z, 400, 14.0, 0.5, 14.0, 0.6);
        world.playSound(
            null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 24.0F, 0.5F
        );
    }

    private static void tick() {
        if (!jobs.isEmpty()) {
            Iterator<HeavenCrater.Job> it = jobs.iterator();

            while (it.hasNext()) {
                HeavenCrater.Job job = it.next();
                if (job.delay > 0) {
                    job.delay--;
                } else if (tickJob(job)) {
                    it.remove();
                }
            }
        }
    }

    private static boolean tickJob(HeavenCrater.Job job) {
        ServerWorld world = job.world;
        HeavenCrater.Settings c = job.cfg;
        int[] offsets = offsets(c.outerR);
        if (job.ticks == 0) {
            firstTickFx(job);
            if (c.push) {
                job.pushQueue = collectTargets(job);
            }
        }

        job.ticks++;
        Mutable m = new Mutable();
        int bottom = world.getBottomY() + 1;
        int columns = 0;
        int blocks = 0;
        int craterR2 = c.carve ? c.craterR * c.craterR : -1;
        int leafR2 = c.stripLeaves ? c.leafR * c.leafR : -1;
        boolean touchBlocks = c.carve || c.stripLeaves;

        while (job.index < offsets.length && columns < c.colsPerTick && blocks < c.maxBlocks) {
            int packed = offsets[job.index++];
            columns++;
            if (touchBlocks) {
                int dx = (short)(packed >> 16);
                int dz = (short)packed;
                int d2 = dx * dx + dz * dz;
                int x = job.cx + dx;
                int z = job.cz + dz;
                if (world.isChunkLoaded(x >> 4, z >> 4)) {
                    int topY = world.getTopY(Type.WORLD_SURFACE, x, z) - 1;
                    if (d2 <= leafR2) {
                        int leafFrom = world.getTopY(Type.MOTION_BLOCKING_NO_LEAVES, x, z);

                        for (int y = leafFrom; y <= topY; y++) {
                            m.set(x, y, z);
                            BlockState state = world.getBlockState(m);
                            if (state.isIn(BlockTags.LEAVES)) {
                                world.setBlockState(m, Blocks.AIR.getDefaultState(), 50);
                                blocks++;
                            }
                        }
                    }

                    if (d2 <= craterR2) {
                        double t = Math.sqrt(d2) / c.craterR;
                        double inv = 1.0 - t * t;
                        int floorY = Math.max(bottom, job.cy - (int)Math.round(c.depth * inv));
                        int ceilY = job.cy + (int)Math.round(c.clearAbove * Math.sqrt(Math.max(0.0, inv)));
                        int maxY = Math.min(ceilY, topY);

                        for (int yx = floorY; yx <= maxY; yx++) {
                            m.set(x, yx, z);
                            BlockState state = world.getBlockState(m);
                            if (!state.isAir() && !(state.getHardness(world, m) < 0.0F)) {
                                world.setBlockState(m, Blocks.AIR.getDefaultState(), 50);
                                blocks++;
                            }
                        }
                    }
                }
            }
        }

        double waveR = c.outerR;
        if (job.index > 0 && job.index < offsets.length) {
            int last = offsets[job.index - 1];
            int ldx = (short)(last >> 16);
            int ldz = (short)last;
            waveR = Math.sqrt(ldx * ldx + ldz * ldz);
        }

        pushReached(job, waveR);
        if (job.ticks % 2 == 0 && job.index > 0) {
            frontFx(job, waveR);
        }

        return job.index >= offsets.length;
    }

    private static List<HeavenCrater.Target> collectTargets(HeavenCrater.Job job) {
        HeavenCrater.Settings c = job.cfg;
        double r = c.pushR;
        double cx = job.cx + 0.5;
        double cz = job.cz + 0.5;
        Box box = new Box(cx - r, job.cy - c.depth - 8, cz - r, cx + r, job.cy + c.clearAbove + 60, cz + r);
        List<HeavenCrater.Target> out = new ArrayList<>();

        for (Entity e : job.world.getOtherEntities(null, box)) {
            if (e != job.p1 && e != job.p2 && e instanceof LivingEntity && !(e instanceof ArmorStandEntity) && !(e instanceof PlayerEntity pl && pl.isSpectator())) {
                double dx = e.getX() - cx;
                double dz = e.getZ() - cz;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= r) {
                    out.add(new HeavenCrater.Target(e, d));
                }
            }
        }

        out.sort((a, b) -> Double.compare(a.dist(), b.dist()));
        return out;
    }

    private static void pushReached(HeavenCrater.Job job, double waveR) {
        if (job.pushQueue != null) {
            HeavenCrater.Settings c = job.cfg;
            double cx = job.cx + 0.5;
            double cz = job.cz + 0.5;
            Iterator<HeavenCrater.Target> it = job.pushQueue.iterator();

            while (it.hasNext()) {
                HeavenCrater.Target t = it.next();
                if (t.dist() > waveR) {
                    break;
                }

                it.remove();
                Entity e = t.entity();
                if (!e.isRemoved() && e.isAlive()) {
                    if (c.killMobs && !(e instanceof PlayerEntity)) {
                        e.damage(job.world.getDamageSources().explosion(null, null), 1000.0F);
                    } else {
                        double dx = e.getX() - cx;
                        double dz = e.getZ() - cz;
                        double d = Math.sqrt(dx * dx + dz * dz);
                        double nx;
                        double nz;
                        if (d < 0.5) {
                            double a = job.world.random.nextDouble() * Math.PI * 2.0;
                            nx = Math.cos(a);
                            nz = Math.sin(a);
                        } else {
                            nx = dx / d;
                            nz = dz / d;
                        }

                        double f = 1.0 - Math.min(1.0, d / Math.max(1, c.pushR));
                        double k = c.pushMin + (c.pushMax - c.pushMin) * f;
                        e.setVelocity(nx * k, c.pushUp * (0.6 + 0.4 * f), nz * k);
                        e.velocityModified = true;
                        if (c.protect && e instanceof ServerPlayerEntity sp) {
                            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 160, 255, false, false));
                        }
                    }
                }
            }
        }
    }

    private static void firstTickFx(HeavenCrater.Job job) {
        ServerWorld w = job.world;
        double x = job.cx + 0.5;
        double y = job.cy + 1;
        double z = job.cz + 0.5;
        forceParticles(w, ParticleTypes.FLASH, x, y, z, 8, 0.0, 0.0, 0.0, 0.0);
        forceParticles(w, ParticleTypes.EXPLOSION_EMITTER, x, y + 6.0, z, 70, 14.0, 10.0, 14.0, 0.0);
        forceParticles(w, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + 15.0, z, 250, 5.0, 20.0, 5.0, 0.12);
        forceParticles(w, ParticleTypes.EXPLOSION_EMITTER, x, y + 45.0, z, 30, 18.0, 4.0, 18.0, 0.0);
        forceParticles(w, ParticleTypes.LAVA, x, y, z, 150, 10.0, 4.0, 10.0, 0.0);
        w.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 48.0F, 0.35F);
        w.playSound(null, x, y, z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 48.0F, 0.45F);
        w.playSound(null, x, y, z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 32.0F, 0.5F);
    }

    private static void frontFx(HeavenCrater.Job job, double r) {
        ServerWorld w = job.world;
        double off = job.ticks * 0.37;

        for (int i = 0; i < 18; i++) {
            double a = off + i * (Math.PI / 9);
            double px = job.cx + 0.5 + Math.cos(a) * r;
            double pz = job.cz + 0.5 + Math.sin(a) * r;
            int gy = w.isChunkLoaded(MathHelper.floor(px) >> 4, MathHelper.floor(pz) >> 4)
                ? w.getTopY(Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(px), MathHelper.floor(pz))
                : job.cy;
            forceParticles(w, ParticleTypes.EXPLOSION_EMITTER, px, gy + 1, pz, 1, 1.5, 1.0, 1.5, 0.0);
            forceParticles(w, ParticleTypes.LARGE_SMOKE, px, gy + 2, pz, 8, 3.0, 2.0, 3.0, 0.06);
        }

        if (job.ticks % 6 == 0) {
            w.playSound(
                null,
                job.cx + 0.5,
                job.cy,
                job.cz + 0.5,
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.BLOCKS,
                24.0F,
                0.45F + w.random.nextFloat() * 0.3F
            );
        }
    }

    private static <T extends ParticleEffect> void forceParticles(
        ServerWorld w, T type, double x, double y, double z, int count, double dx, double dy, double dz, double speed
    ) {
        double rangeSq = 262144.0;

        for (ServerPlayerEntity viewer : w.getPlayers()) {
            if (!(viewer.squaredDistanceTo(x, y, z) > rangeSq)) {
                w.spawnParticles(viewer, type, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    private static int[] offsets(int radius) {
        if (cachedOffsets != null && cachedRadius == radius) {
            return cachedOffsets;
        } else {
            int r2 = radius * radius;
            List<int[]> list = new ArrayList<>();

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 <= r2) {
                        list.add(new int[]{d2, dx, dz});
                    }
                }
            }

            list.sort((a, b) -> Integer.compare(a[0], b[0]));
            int[] out = new int[list.size()];

            for (int i = 0; i < out.length; i++) {
                int[] e = list.get(i);
                out[i] = e[1] << 16 | e[2] & 65535;
            }

            cachedOffsets = out;
            cachedRadius = radius;
            return out;
        }
    }

    public static boolean isRunning() {
        return !jobs.isEmpty();
    }

    private static final class Job {
        final ServerWorld world;
        final int cx;
        final int cy;
        final int cz;
        final HeavenCrater.Settings cfg;
        final ServerPlayerEntity p1;
        final ServerPlayerEntity p2;
        int delay = 16;
        int index = 0;
        int ticks = 0;
        List<HeavenCrater.Target> pushQueue = null;

        Job(ServerWorld world, int cx, int cy, int cz, HeavenCrater.Settings cfg, ServerPlayerEntity p1, ServerPlayerEntity p2) {
            this.world = world;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.cfg = cfg;
            this.p1 = p1;
            this.p2 = p2;
        }
    }

    private static final class Settings {
        final boolean carve;
        final boolean stripLeaves;
        final boolean push;
        final boolean protect;
        final boolean killMobs;
        final int craterR;
        final int depth;
        final int clearAbove;
        final int leafR;
        final int pushR;
        final int outerR;
        final int colsPerTick;
        final int maxBlocks;
        final double pushMax;
        final double pushMin;
        final double pushUp;

        Settings(CoopMovesConfig c, boolean griefAllowed) {
            this.craterR = clamp(c.heavenCraterRadius, 1, 400);
            this.depth = clamp(c.heavenCraterDepth, 0, 200);
            this.clearAbove = clamp(c.heavenCraterClearAbove, 0, 300);
            this.leafR = clamp(c.heavenLeafRadius, 0, 400);
            this.pushR = clamp(c.heavenPushRadius, 0, 400);
            this.carve = griefAllowed && !c.noGriefMode && c.heavenCraterEnabled;
            this.stripLeaves = griefAllowed && !c.noGriefMode && c.heavenStripLeaves;
            this.push = c.heavenPushEnabled;
            this.protect = c.heavenProtectPushedPlayers;
            this.killMobs = c.heavenKillMobs;
            this.pushMax = c.heavenPushMax;
            this.pushMin = c.heavenPushMin;
            this.pushUp = c.heavenPushUp;
            this.colsPerTick = Math.max(1, c.heavenColumnsPerTick);
            this.maxBlocks = Math.max(1, c.heavenMaxBlocksPerTick);
            int r = 0;
            if (this.carve) {
                r = Math.max(r, this.craterR);
            }

            if (this.stripLeaves) {
                r = Math.max(r, this.leafR);
            }

            if (this.push) {
                r = Math.max(r, this.pushR);
            }

            this.outerR = Math.max(r, 1);
        }

        private static int clamp(int v, int lo, int hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }

    private record Target(Entity entity, double dist) {
    }
}
