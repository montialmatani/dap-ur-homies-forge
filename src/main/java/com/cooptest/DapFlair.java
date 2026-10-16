package com.cooptest;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class DapFlair {
    public static final double TIER_BASE_MULT = 1.6;
    public static final double TIER_MULT_STEP = 0.9;
    public static final int BASE_PARTICLE_COUNT = 34;
    public static final double BASE_SPREAD = 0.4;
    public static final long PRE_IMPACT_MS = 180L;
    public static final long POST_IMPACT_MS = 340L;
    public static final long MIN_WINDOW_MS = 520L;
    public static final float FLAIR_SHAKE = 0.55F;
    public static final long FLAIR_SHAKE_MS = 200L;
    public static final int CLEAN_REGEN_TICKS = 280;
    public static final int CLEAN_REGEN_AMP = 0;
    public static final int CLEAN_RESISTANCE_TICKS = 160;
    public static final int CLEAN_RESISTANCE_AMP = 0;
    public static final int PERFECT_BUFF_TICKS = 700;
    public static final int PERFECT_HASTE_AMP = 2;
    public static final int PERFECT_SPEED_AMP = 2;
    public static final int PERFECT_STRENGTH_AMP = 1;
    public static final float BLAST_MAX_DAMAGE = 16.0F;
    public static final double BLAST_RADIUS = 8.0;
    public static final double BLAST_KNOCKBACK = 2.1;
    public static final double BLAST_KNOCKUP = 0.7;
    public static final float CLEAN_BLAST_SHAKE = 1.4F;
    public static final int CLEAN_BLAST_SHAKE_MS = 260;
    public static final double SHOCKWAVE_RADIUS = 10.0;
    public static final int SHOCKWAVE_STEPS = 12;
    public static final float RARE_CHANCE = 0.12F;
    private static final Map<UUID, DapFlair.Flair> windows = new HashMap<>();
    public static final String[] RARE_LABELS = new String[]{
        "§d§lINSANE DAP!!", "§5§lIMMACULATE!!", "§c§lCERTIFIED!!", "§a§lLEGENDARY DAP!!", "§9§lUNREAL!!", "§6§l✦ GOATED ✦"
    };

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(DapFlair.FlairWindowPayload.ID, DapFlair.FlairWindowPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DapFlair.FlairPressPayload.ID, DapFlair.FlairPressPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapFlair.FlairSuccessPayload.ID, DapFlair.FlairSuccessPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DapFlair.FlairShakePayload.ID, DapFlair.FlairShakePayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(DapFlair.FlairPressPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            ctx.server().execute(() -> onButtonPress(player, "G"));
        });
    }

    public static void open(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2, long impactInMs) {
        open(world, pos, p1, p2, impactInMs, 1);
    }

    public static void open(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2, long impactInMs, int tier) {
        open(world, pos, p1, p2, impactInMs, tier, false);
    }

    public static void open(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2, long impactInMs, int tier, boolean clean) {
        open(world, pos, p1, p2, impactInMs, tier, clean, false);
    }

    public static void openDapRun(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2, long impactInMs, int runTier) {
        open(world, pos, p1, p2, impactInMs, 3, false, false);
        DapFlair.Flair f = windows.get(p1.getUuid());
        if (f != null) {
            f.dapRun = runTier;
        }
    }

    public static void open(ServerWorld world, Vec3d pos, ServerPlayerEntity p1, ServerPlayerEntity p2, long impactInMs, int tier, boolean clean, boolean perfect) {
        long now = System.currentTimeMillis();
        long impactAt = now + Math.max(0L, impactInMs);
        long opens = Math.max(now, impactAt - 180L);
        long closes = Math.max(impactAt + 340L, opens + 520L);
        DapFlair.Flair f = new DapFlair.Flair(p1.getUuid(), p2.getUuid(), opens, closes);
        f.tier = tier;
        f.clean = clean;
        f.perfect = perfect;
        windows.put(p1.getUuid(), f);
        windows.put(p2.getUuid(), f);
        MinecraftServer server = p1.getServer();
        if (server != null) {
            UUID id1 = p1.getUuid();
            UUID id2 = p2.getUuid();
            long delay = Math.max(1L, closes - now);
            int armedMs = (int)(closes - now);
            int promptDelay = (int)Math.max(0L, opens - now);
            DapFlair.FlairWindowPayload armed = new DapFlair.FlairWindowPayload(true, armedMs, promptDelay, tier);
            ServerPlayNetworking.send(p1, armed);
            ServerPlayNetworking.send(p2, armed);
            new Thread(() -> {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException var8) {
                    return;
                }

                server.execute(() -> resolve(server, world, pos, id1, id2));
            }).start();
        }
    }

    public static boolean onButtonPress(ServerPlayerEntity player, String button) {
        if (!"G".equals(button)) {
            return false;
        } else {
            UUID id = player.getUuid();
            DapFlair.Flair f = windows.get(id);
            if (f == null) {
                return false;
            } else {
                long now = System.currentTimeMillis();
                if (now < f.opensAt) {
                    f.burned = true;
                    player.sendMessage(Text.literal("§8too early"), true);
                    return true;
                } else if (now > f.closesAt) {
                    return true;
                } else {
                    boolean isA = id.equals(f.a);
                    boolean already = isA ? f.pressedA : f.pressedB;
                    if (!already && !f.burned) {
                        if (isA) {
                            f.pressedA = true;
                        } else {
                            f.pressedB = true;
                        }

                        return true;
                    } else {
                        f.burned = true;
                        player.sendMessage(Text.literal("§8flair burned"), true);
                        return true;
                    }
                }
            }
        }
    }

    private static boolean resolve(MinecraftServer server, ServerWorld world, Vec3d pos, UUID id1, UUID id2) {
        DapFlair.Flair f = windows.remove(id1);
        windows.remove(id2);
        if (f == null) {
            return false;
        } else if (!f.burned && f.pressedA && f.pressedB) {
            ServerPlayerEntity p1 = server.getPlayerManager().getPlayer(id1);
            ServerPlayerEntity p2 = server.getPlayerManager().getPlayer(id2);
            double m = 1.6 + 0.9 * Math.max(0, f.tier - 1);
            int n = (int)Math.round(34.0 * m);
            double spread = 0.4 * m;
            world.spawnParticles(ParticleTypes.field_11207, pos.x, pos.y, pos.z, (int)(n * 1.6), spread, spread, spread, 0.22 * m);
            world.spawnParticles(ParticleTypes.field_11248, pos.x, pos.y, pos.z, (int)(n * 1.2), spread, spread, spread, 0.18 * m);
            world.spawnParticles(ParticleTypes.field_11208, pos.x, pos.y, pos.z, n, spread, spread, spread, 0.14 * m);
            world.spawnParticles(ParticleTypes.field_11205, pos.x, pos.y, pos.z, n, spread, spread, spread, 0.14 * m);
            world.spawnParticles(ParticleTypes.field_11220, pos.x, pos.y, pos.z, (int)(n * 0.8), spread, spread, spread, 0.2 * m);
            if (f.tier >= 2) {
                world.spawnParticles(
                    ParticleTypes.field_29644, pos.x, pos.y, pos.z, (int)(n * 0.7), 0.18 * m, 0.55 * m, 0.18 * m, 0.3 * m
                );
                world.spawnParticles(ParticleTypes.field_22246, pos.x, pos.y, pos.z, (int)(n * 0.4), 0.2 * m, 0.4 * m, 0.2 * m, 0.14 * m);
            }

            if (f.tier >= 3) {
                world.spawnParticles(ParticleTypes.field_17909, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
                world.spawnParticles(ParticleTypes.field_11236, pos.x, pos.y, pos.z, 3, 0.4, 0.4, 0.4, 0.0);
                ring(world, pos, 1.6 * m, (int)Math.round(28.0 * m), ParticleTypes.field_11207);
            }

            if (f.tier >= 4) {
                world.spawnParticles(ParticleTypes.field_11240, pos.x, pos.y, pos.z, (int)(n * 1.1), spread, spread, spread, 0.24 * m);
                world.spawnParticles(
                    ParticleTypes.field_11239, pos.x, pos.y, pos.z, (int)(n * 0.3), spread * 0.6, spread * 0.6, spread * 0.6, 0.0
                );
                ring(world, pos, 2.2 * m, (int)Math.round(36.0 * m), ParticleTypes.field_11240);
            }

            if (f.tier >= 5) {
                for (int y = 0; y < 20; y++) {
                    world.spawnParticles(ParticleTypes.field_11207, pos.x, pos.y + y * 0.45, pos.z, 4, 0.18, 0.04, 0.18, 0.02);
                    world.spawnParticles(ParticleTypes.field_11220, pos.x, pos.y + y * 0.45, pos.z, 2, 0.25, 0.04, 0.25, 0.03);
                }

                world.spawnParticles(ParticleTypes.field_11248, pos.x, pos.y + 1.0, pos.z, (int)(n * 2.0), 1.2, 1.2, 1.2, 0.35);
            }

            ring(world, pos, 1.0 * m, (int)Math.round(20.0 * m), ParticleTypes.field_29644);
            ring(world, pos, 2.6 * m, (int)Math.round(40.0 * m), ParticleTypes.field_11248);

            for (int y = 0; y < 7; y++) {
                double yy = pos.y + y * 0.28;
                world.spawnParticles(ParticleTypes.field_29643, pos.x, yy, pos.z, (int)(n * 0.22), 0.3 * m, 0.05, 0.3 * m, 0.1 * m);
                world.spawnParticles(ParticleTypes.field_28479, pos.x, yy, pos.z, (int)(n * 0.18), 0.26 * m, 0.05, 0.26 * m, 0.07 * m);
            }

            world.spawnParticles(
                ParticleTypes.field_29645,
                pos.x,
                pos.y + 0.4,
                pos.z,
                (int)(n * 0.5),
                spread * 1.4,
                spread * 0.7,
                spread * 1.4,
                0.26 * m
            );
            world.spawnParticles(ParticleTypes.field_11207, pos.x, pos.y + 0.9, pos.z, (int)(n * 0.6), 0.1, 0.55 * m, 0.1, 0.42 * m);
            if (f.clean) {
                shockwave(world, pos);
                if (CoopMovesConfig.get().enableFlairBlast) {
                    blast(server, world, pos, id1, id2);
                }

                world.playSound(null, pos.x, pos.y, pos.z, ModSounds.DAP_FOLLOW, SoundCategory.field_15248, 1.0F, 1.0F);
            } else {
                world.playSound(null, pos.x, pos.y, pos.z, ModSounds.PERFECT_DAP, SoundCategory.field_15248, 1.0F, 1.0F);
            }

            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.EPIC_DAP, SoundCategory.field_15248, 1.0F, 1.0F);
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.field_14917, SoundCategory.field_15248, 0.9F, 1.7F);
            if (CoopMovesConfig.get().enableFlairBuffs) {
                grantRewards(p1, f);
                grantRewards(p2, f);
            }

            float shakeAmt = (float)(0.55F * m) * (f.clean ? 1.8F : 1.0F);
            DapFlair.FlairSuccessPayload ok = new DapFlair.FlairSuccessPayload(shakeAmt, 200);
            if (p1 != null) {
                ServerPlayNetworking.send(p1, ok);
                p1.sendMessage(Text.literal("§b§lFLAIR!"), true);
            }

            if (p2 != null) {
                ServerPlayNetworking.send(p2, ok);
                p2.sendMessage(Text.literal("§b§lFLAIR!"), true);
            }

            return true;
        } else {
            return false;
        }
    }

    private static void grantRewards(ServerPlayerEntity p, DapFlair.Flair f) {
        if (p != null) {
            if (f.dapRun > 0) {
                CoopMovesConfig c = CoopMovesConfig.get();
                boolean perfectRun = f.dapRun >= 3;
                int secs = perfectRun ? c.dapRunFlairPerfectSpeedSec : c.dapRunFlairSprintSpeedSec;
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5904, Math.max(1, secs) * 20, perfectRun ? 1 : 0, false, true));
                DapRunHandler.onRunFlair(p, perfectRun);
                p.sendMessage(Text.literal(perfectRun ? "§b§lRUN FLAIR §7— speed II" : "§b§lRUN FLAIR §7— speed I"), true);
            } else {
                if (f.perfect) {
                    DapHearts.flair(p, false);
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5917, 700, 2, false, true));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5904, 700, 2, false, true));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5910, 700, 1, false, true));
                    p.sendMessage(Text.literal("§e§lPERFECT FLAIR §7— haste III + speed III + strength II"), true);
                } else if (f.clean) {
                    DapHearts.flair(p, true);
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5924, 280, 0, false, true));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5907, 160, 0, false, true));
                    p.sendMessage(Text.literal("§6§lCLEAN FLAIR §7— regen + resistance"), true);
                }
            }
        }
    }

    private static void blast(MinecraftServer server, ServerWorld world, Vec3d pos, UUID id1, UUID id2) {
        Box box = new Box(
            pos.x - 8.0, pos.y - 8.0, pos.z - 8.0, pos.x + 8.0, pos.y + 8.0, pos.z + 8.0
        );

        for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, box, e -> {
            if (!e.isAlive()) {
                return false;
            } else {
                UUID eid = e.getUuid();
                return !eid.equals(id1) && !eid.equals(id2);
            }
        })) {
            Vec3d away = victim.getPos().subtract(pos);
            double dist = away.length();
            if (!(dist > 8.0)) {
                float dmg = 16.0F;
                double kbScale = 0.45 + 0.55 * (1.0 - dist / 8.0);
                Vec3d dir = dist < 0.001 ? new Vec3d(1.0, 0.0, 0.0) : away.normalize();
                victim.setVelocity(dir.x * 2.1 * kbScale, 0.7 * kbScale, dir.z * 2.1 * kbScale);
                victim.velocityModified = true;
                if (dmg > 0.5F) {
                    victim.damage(world.getDamageSources().generic(), dmg);
                }
            }
        }

        world.playSound(null, pos.x, pos.y, pos.z, ModSounds.EXPLOSION_IMPACT, SoundCategory.field_15248, 1.4F, 0.8F);

        for (int ring = 0; ring < 3; ring++) {
            double r = 1.5 + ring * 2.2;
            int pts = 24 + ring * 10;

            for (int i = 0; i < pts; i++) {
                double a = (Math.PI * 2) * i / pts;
                world.spawnParticles(
                    ParticleTypes.field_11205,
                    pos.x + Math.cos(a) * r,
                    pos.y + 0.15,
                    pos.z + Math.sin(a) * r,
                    1,
                    0.03,
                    0.03,
                    0.03,
                    0.01
                );
                world.spawnParticles(
                    ParticleTypes.field_11207, pos.x + Math.cos(a) * r, pos.y + 0.4, pos.z + Math.sin(a) * r, 1, 0.02, 0.1, 0.02, 0.0
                );
            }
        }

        world.spawnParticles(ParticleTypes.field_11221, pos.x, pos.y + 0.3, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        DapFlair.FlairShakePayload blastShake = new DapFlair.FlairShakePayload(1.4F, 260);
        ServerPlayerEntity sa = server.getPlayerManager().getPlayer(id1);
        ServerPlayerEntity sb = server.getPlayerManager().getPlayer(id2);
        if (sa != null) {
            ServerPlayNetworking.send(sa, blastShake);
        }

        if (sb != null) {
            ServerPlayNetworking.send(sb, blastShake);
        }
    }

    private static void ring(ServerWorld world, Vec3d pos, double radius, int points, ParticleEffect effect) {
        for (int i = 0; i < points; i++) {
            double ang = (Math.PI * 2) * i / points;
            world.spawnParticles(
                effect, pos.x + Math.cos(ang) * radius, pos.y, pos.z + Math.sin(ang) * radius, 1, 0.0, 0.02, 0.0, 0.01
            );
        }
    }

    public static void shockwave(ServerWorld world, Vec3d pos) {
        MinecraftServer server = world.getServer();
        if (server != null) {
            new Thread(() -> {
                for (int step = 1; step <= 12; step++) {
                    double r = 10.0 * (step / 12.0);
                    server.execute(() -> {
                        int points = (int)Math.max(12.0, r * 10.0);

                        for (int i = 0; i < points; i++) {
                            double ang = (Math.PI * 2) * i / points;
                            double x = pos.x + Math.cos(ang) * r;
                            double z = pos.z + Math.sin(ang) * r;
                            world.spawnParticles(ParticleTypes.field_11204, x, pos.y, z, 1, 0.0, 0.02, 0.0, 0.01);
                            if ((i & 1) == 0) {
                                world.spawnParticles(ParticleTypes.field_11207, x, pos.y + 0.1, z, 1, 0.0, 0.03, 0.0, 0.02);
                            }
                        }
                    });

                    try {
                        Thread.sleep(50L);
                    } catch (InterruptedException var7) {
                        return;
                    }
                }
            }).start();
        }
    }

    public static Object[] flavour(Random rng, boolean clean) {
        String[] common = clean
            ? new String[]{"§b§lCLEAN DAP!", "§b§lCRISP!", "§3§lSMOOTH!", "§b§lNICE ONE!"}
            : new String[]{"§6§lGREAT DAP!", "§e§lSOLID!", "§6§lNOT BAD!", "§e§lTHAT'LL DO!"};
        return rng.nextFloat() < 0.12F
            ? new Object[]{RARE_LABELS[rng.nextInt(RARE_LABELS.length)], true}
            : new Object[]{common[rng.nextInt(common.length)], false};
    }

    public static void playRareEffect(ServerWorld world, Vec3d pos, String label) {
        int idx = 0;

        for (int i = 0; i < RARE_LABELS.length; i++) {
            if (RARE_LABELS[i].equals(label)) {
                idx = i;
                break;
            }
        }

        switch (idx) {
            case 0:
                world.spawnParticles(ParticleTypes.field_11249, pos.x, pos.y, pos.z, 90, 0.8, 0.8, 0.8, 0.35);
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.field_14671, SoundCategory.field_15248, 0.35F, 1.9F);
                break;
            case 1:
                for (int y = 0; y < 14; y++) {
                    world.spawnParticles(ParticleTypes.field_11207, pos.x, pos.y + y * 0.35, pos.z, 6, 0.12, 0.02, 0.12, 0.01);
                }

                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.field_14703, SoundCategory.field_15248, 0.6F, 2.0F);
                break;
            case 2:
                world.spawnParticles(ParticleTypes.field_11239, pos.x, pos.y, pos.z, 24, 0.4, 0.4, 0.4, 0.02);
                world.spawnParticles(ParticleTypes.field_11240, pos.x, pos.y, pos.z, 70, 0.6, 0.6, 0.6, 0.12);
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.field_15013, SoundCategory.field_15248, 1.0F, 0.8F);
                break;
            case 3:
                for (int ix = 0; ix < 60; ix++) {
                    double a = ix * 0.45;
                    double r = 0.15 + ix * 0.02;
                    world.spawnParticles(
                        ParticleTypes.field_11211,
                        pos.x + Math.cos(a) * r,
                        pos.y + ix * 0.05,
                        pos.z + Math.sin(a) * r,
                        1,
                        0.0,
                        0.0,
                        0.0,
                        0.0
                    );
                }

                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.field_15195, SoundCategory.field_15248, 0.7F, 1.2F);
                break;
            case 4:
                world.spawnParticles(ParticleTypes.field_22246, pos.x, pos.y, pos.z, 70, 0.7, 0.7, 0.7, 0.16);
                world.spawnParticles(ParticleTypes.field_11214, pos.x, pos.y, pos.z, 120, 0.9, 0.9, 0.9, 0.6);
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.field_14669, SoundCategory.field_15248, 0.5F, 1.6F);
                break;
            default:
                world.spawnParticles(ParticleTypes.field_11220, pos.x, pos.y, pos.z, 140, 0.9, 1.0, 0.9, 0.45);

                for (int y = 0; y < 18; y++) {
                    world.spawnParticles(ParticleTypes.field_11248, pos.x, pos.y + y * 0.4, pos.z, 4, 0.2, 0.05, 0.2, 0.03);
                }

                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.field_15188, SoundCategory.field_15248, 1.0F, 1.0F);
                world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.field_14709, SoundCategory.field_15248, 0.8F, 0.7F);
        }
    }

    public static boolean hasWindow(UUID id) {
        return windows.containsKey(id);
    }

    public static void cleanup(UUID id) {
        DapFlair.Flair f = windows.remove(id);
        if (f != null) {
            windows.remove(f.a);
            windows.remove(f.b);
        }
    }

    private static final class Flair {
        final UUID a;
        final UUID b;
        final long opensAt;
        final long closesAt;
        int tier;
        boolean clean;
        boolean perfect;
        int dapRun;
        boolean pressedA;
        boolean pressedB;
        boolean burned;

        Flair(UUID a, UUID b, long opensAt, long closesAt) {
            this.a = a;
            this.b = b;
            this.opensAt = opensAt;
            this.closesAt = closesAt;
        }
    }

    public record FlairPressPayload() implements CustomPayload {
        public static final Id<DapFlair.FlairPressPayload> ID = new Id(new Identifier("cooptest", "dap_flair_press"));
        public static final PacketCodec<PacketByteBuf, DapFlair.FlairPressPayload> CODEC = PacketCodec.unit(new DapFlair.FlairPressPayload());

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FlairShakePayload(float shake, int shakeMs) implements CustomPayload {
        public static final Id<DapFlair.FlairShakePayload> ID = new Id(new Identifier("cooptest", "dap_flair_shake"));
        public static final PacketCodec<PacketByteBuf, DapFlair.FlairShakePayload> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeFloat(p.shake);
            buf.writeInt(p.shakeMs);
        }, buf -> new DapFlair.FlairShakePayload(buf.readFloat(), buf.readInt()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FlairSuccessPayload(float shake, int shakeMs) implements CustomPayload {
        public static final Id<DapFlair.FlairSuccessPayload> ID = new Id(new Identifier("cooptest", "dap_flair_success"));
        public static final PacketCodec<PacketByteBuf, DapFlair.FlairSuccessPayload> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeFloat(p.shake);
            buf.writeInt(p.shakeMs);
        }, buf -> new DapFlair.FlairSuccessPayload(buf.readFloat(), buf.readInt()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FlairWindowPayload(boolean open, int durationMs, int promptDelayMs, int tier) implements CustomPayload {
        public static final Id<DapFlair.FlairWindowPayload> ID = new Id(new Identifier("cooptest", "dap_flair_window"));
        public static final PacketCodec<PacketByteBuf, DapFlair.FlairWindowPayload> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeBoolean(p.open);
            buf.writeInt(p.durationMs);
            buf.writeInt(p.promptDelayMs);
            buf.writeInt(p.tier);
        }, buf -> new DapFlair.FlairWindowPayload(buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
