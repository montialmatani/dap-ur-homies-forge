package com.cooptest.bros;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.server.MinecraftServer;

public final class BrosAbilities {
    public static final int SYNC_WINDOW_TICKS = 8;
    public static int RUSH_TICKS = 8;
    public static double RUSH_SPEED = 0.75;
    public static float RUSH_DAMAGE = 18.0F;
    public static final double RUSH_KNOCKBACK = 1.4;
    public static final double RUSH_HIT_RADIUS = 1.6;
    public static int RUSH_COOLDOWN_TICKS = 120;
    public static float RUSH_SHIELD_COST = 6.0F;
    public static boolean RUSH_BREAKS_BLOCKS = true;
    public static float RUSH_MAX_HARDNESS = 3.0F;
    public static final int RUSH_MAX_BLOCKS_TICK = 14;
    public static double PULSE_RADIUS = 10.0;
    public static float PULSE_DAMAGE = 10.0F;
    public static double PULSE_KNOCKBACK = 1.6;
    public static float PULSE_SHIELD_COST = 8.0F;
    public static int PULSE_COOLDOWN_TICKS = 160;
    public static float BUNKER_DRAIN = 0.06F;
    public static double BUNKER_SHOVE_RADIUS = 3.5;
    public static double BUNKER_SHOVE_POWER = 0.55;
    public static int BUNKER_SHOVE_INTERVAL = 10;
    public static float BUNKER_DAMAGE_THROUGH = 0.08F;
    public static int RECHARGE_FULL_TICKS = 80;
    public static float RECHARGE_HEALTH_COST = 0.2F;
    public static float RECHARGE_MIN_HEALTH = 6.0F;

    private BrosAbilities() {
    }

    static void onAbility(MinecraftServer server, ServerPlayerEntity p, byte kind, boolean down) {
        BrosHandler.Session s = BrosHandler.sessionOf(p);
        if (s != null && !s.ended && s.phase == 2) {
            boolean isA = p == s.a;
            switch (kind) {
                case 0:
                    if (s.ticks < s.rushCdUntil) {
                        cooldownMsg(p, "Rush", s.rushCdUntil - s.ticks);
                        return;
                    }

                    if (s.bunkering) {
                        p.sendMessage(Text.literal("§7Let go of the bunker first"), true);
                        return;
                    }

                    if (s.shieldHp <= RUSH_SHIELD_COST) {
                        p.sendMessage(Text.literal("§cShield too weak to rush"), true);
                        return;
                    }

                    if (isA) {
                        s.rushVoteA = s.ticks;
                    } else {
                        s.rushVoteB = s.ticks;
                    }

                    if (agreed(s, s.rushVoteA, s.rushVoteB)) {
                        startRush(s);
                    }
                    break;
                case 1:
                    if (s.ticks < s.pulseCdUntil) {
                        cooldownMsg(p, "Pulse", s.pulseCdUntil - s.ticks);
                        return;
                    }

                    if (isA) {
                        s.pulseVoteA = s.ticks;
                    } else {
                        s.pulseVoteB = s.ticks;
                    }

                    if (agreed(s, s.pulseVoteA, s.pulseVoteB)) {
                        firePulse(server, s);
                    }
                    break;
                case 2:
                    if (isA) {
                        s.holdA = down;
                    } else {
                        s.holdB = down;
                    }
                    break;
                case 3:
                    if (down && s.ticks < s.pulseCdUntil) {
                        cooldownMsg(p, "Bunker", s.pulseCdUntil - s.ticks);
                        if (isA) {
                            s.bunkerA = false;
                        } else {
                            s.bunkerB = false;
                        }

                        return;
                    }

                    if (isA) {
                        s.bunkerA = down;
                    } else {
                        s.bunkerB = down;
                    }
            }
        }
    }

    private static boolean agreed(BrosHandler.Session s, int voteA, int voteB) {
        boolean dummy = s.b instanceof ArmorStandEntity;
        return dummy ? voteA >= 0 && s.ticks - voteA <= 8 : voteA >= 0 && voteB >= 0 && Math.abs(voteA - voteB) <= 8 && s.ticks - Math.min(voteA, voteB) <= 8;
    }

    static void tick(MinecraftServer server, BrosHandler.Session s) {
        s.rushVoteA = expire(s, s.rushVoteA, s.a, "G");
        s.rushVoteB = expire(s, s.rushVoteB, s.b, "G");
        s.pulseVoteA = expire(s, s.pulseVoteA, s.a, "H");
        s.pulseVoteB = expire(s, s.pulseVoteB, s.b, "H");
        boolean dummyB = s.b instanceof ArmorStandEntity;
        boolean bothBunker = s.bunkerA && (dummyB || s.bunkerB);
        boolean wasBunkering = s.bunkering;
        s.bunkering = bothBunker && s.rushTicksLeft <= 0 && s.ticks >= s.pulseCdUntil && s.shieldHp > 0.2F;
        if (s.bunkering && s.world instanceof ServerWorld bw) {
            s.shieldHp = Math.max(0.1F, s.shieldHp - BUNKER_DRAIN);
            double by = Math.min(s.ya, s.yb);
            if (!wasBunkering) {
                bw.playSound(null, s.cx, by + 1.0, s.cz, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8F, 0.6F);
            }

            if (s.ticks % 3 == 0) {
                for (int i = 0; i < 8; i++) {
                    double a = (Math.PI * 2) * i / 8.0 + s.ticks * 0.08;
                    bw.spawnParticles(ParticleTypes.END_ROD, s.cx + Math.cos(a) * 1.4, by + 0.4 + i % 2 * 0.8, s.cz + Math.sin(a) * 1.4, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }

            if (s.ticks % BUNKER_SHOVE_INTERVAL == 0) {
                Box shoveBox = new Box(
                    s.cx - BUNKER_SHOVE_RADIUS, by - 2.0, s.cz - BUNKER_SHOVE_RADIUS, s.cx + BUNKER_SHOVE_RADIUS, by + 3.0, s.cz + BUNKER_SHOVE_RADIUS
                );

                for (LivingEntity t : bw.getEntitiesByClass(LivingEntity.class, shoveBox, e -> e != s.a && e != s.b && e.isAlive())) {
                    double dx = t.getX() - s.cx;
                    double dz = t.getZ() - s.cz;
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (!(d > BUNKER_SHOVE_RADIUS)) {
                        if (d < 0.05) {
                            dx = 1.0;
                            dz = 0.0;
                            d = 1.0;
                        }

                        double falloff = 1.0 - d / BUNKER_SHOVE_RADIUS * 0.6;
                        double power = BUNKER_SHOVE_POWER * falloff;
                        t.addVelocity(dx / d * power, 0.12, dz / d * power);
                        t.velocityModified = true;
                        if (t instanceof ServerPlayerEntity sp) {
                            sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
                        }
                    }
                }
            }

            if (s.shieldHp <= 0.2F) {
                s.bunkering = false;
                bw.playSound(null, s.cx, by + 1.0, s.cz, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8F, 1.3F);
            }
        } else if (wasBunkering && s.world instanceof ServerWorld bw2) {
            bw2.playSound(null, s.cx, Math.min(s.ya, s.yb) + 1.0, s.cz, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.6F, 1.2F);
        }

        boolean dummy = s.b instanceof ArmorStandEntity;
        boolean bothHold = s.holdA && (dummy || s.holdB);
        boolean inCombat = s.ticks - s.lastHitTick < BrosShield.HEAL_COMBAT_PAUSE_TICKS;
        boolean canCharge = bothHold && s.rushTicksLeft <= 0 && !s.bunkering && !inCombat && s.shieldHp < BrosShield.SHIELD_HP && s.phase == 2;
        s.charging = bothHold && s.rushTicksLeft <= 0;
        ServerPlayerEntity pa = s.a instanceof ServerPlayerEntity p1 ? p1 : null;
        ServerPlayerEntity pb = s.b instanceof ServerPlayerEntity p2 ? p2 : null;
        int payers = (pa != null ? 1 : 0) + (pb != null ? 1 : 0);
        float gain = BrosShield.SHIELD_HP / RECHARGE_FULL_TICKS;
        float costEach = payers == 0 ? 0.0F : gain * RECHARGE_HEALTH_COST / payers;
        s.lowHp = pa != null && pa.getHealth() - costEach < RECHARGE_MIN_HEALTH || pb != null && pb.getHealth() - costEach < RECHARGE_MIN_HEALTH;
        s.filling = canCharge && !s.lowHp;
        if (s.filling && s.world instanceof ServerWorld sw) {
            if (pa != null) {
                pa.setHealth(pa.getHealth() - costEach);
            }

            if (pb != null) {
                pb.setHealth(pb.getHealth() - costEach);
            }

            s.shieldHp = Math.min(BrosShield.SHIELD_HP, s.shieldHp + gain);
            double y = Math.min(s.ya, s.yb);
            if (s.ticks % 5 == 0) {
                float fill = s.shieldHp / BrosShield.SHIELD_HP;
                sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, s.cx, y + 0.2, s.cz, 3, 0.6, 0.05, 0.6, 0.02);
                sw.playSound(null, s.cx, y + 1.0, s.cz, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.4F, 0.8F + fill);
            }

            if (s.shieldHp >= BrosShield.SHIELD_HP) {
                sw.playSound(null, s.cx, y + 1.0, s.cz, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8F, 1.4F);
                sw.spawnParticles(ParticleTypes.END_ROD, s.cx, y + 1.5, s.cz, 10, 0.8, 0.5, 0.8, 0.02);
            }
        }
    }

    static int hudFlags(BrosHandler.Session s) {
        boolean dummy = s.b instanceof ArmorStandEntity;
        boolean rA = pending(s, s.rushVoteA);
        boolean pA = pending(s, s.pulseVoteA);
        boolean rB = dummy ? rA : pending(s, s.rushVoteB);
        boolean pB = dummy ? pA : pending(s, s.pulseVoteB);
        boolean hB = dummy ? s.holdA : s.holdB;
        int f = 0;
        if (rA) {
            f |= 1;
        }

        if (rB) {
            f |= 2;
        }

        if (pA) {
            f |= 4;
        }

        if (pB) {
            f |= 8;
        }

        if (s.holdA) {
            f |= 16;
        }

        if (hB) {
            f |= 32;
        }

        if (s.filling) {
            f |= 64;
        }

        if (s.ticks - s.lastHitTick < BrosShield.HEAL_COMBAT_PAUSE_TICKS) {
            f |= 128;
        }

        if (s.lowHp) {
            f |= 256;
        }

        if (s.shieldHp > PULSE_SHIELD_COST) {
            f |= 512;
        }

        if (s.shieldHp > RUSH_SHIELD_COST) {
            f |= 1024;
        }

        if (s.bunkering) {
            f |= 2048;
        }

        return f;
    }

    private static boolean pending(BrosHandler.Session s, int vote) {
        return vote >= 0 && s.ticks - vote <= 8;
    }

    private static int expire(BrosHandler.Session s, int vote, LivingEntity who, String key) {
        if (vote >= 0 && s.ticks - vote > 8) {
            if (who instanceof ServerPlayerEntity p) {
                String bind = "G".equals(key) ? "key.coopmoves.dap" : "key.coopmoves.highfive";
                p.sendMessage(Text.literal("§7Your bro didn't press §f").append(Text.keybind(bind)), true);
            }

            return -1;
        } else {
            return vote;
        }
    }

    private static void startRush(BrosHandler.Session s) {
        s.rushVoteA = -1;
        s.rushVoteB = -1;
        s.shieldHp = Math.max(0.1F, s.shieldHp - RUSH_SHIELD_COST);
        s.holdA = false;
        s.holdB = false;
        s.charging = false;
        double[] f = BrosHandler.forward(s.heading);
        s.rushDx = f[0];
        s.rushDz = f[1];
        s.rushTicksLeft = RUSH_TICKS;
        s.rushCdUntil = s.ticks + RUSH_COOLDOWN_TICKS;
        s.rushHit.clear();
        if (s.world instanceof ServerWorld sw) {
            double y = Math.min(s.ya, s.yb) + 1.0;
            sw.playSound(null, s.cx, y, s.cz, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.6F);
            sw.playSound(null, s.cx, y, s.cz, SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 0.5F, 1.4F);
        }
    }

    static boolean tickRush(MinecraftServer server, BrosHandler.Session s) {
        if (s.rushTicksLeft > 0 && s.world instanceof ServerWorld sw) {
            s.rushTicksLeft--;
            double var10 = s.rushDx * RUSH_SPEED;
            double dz = s.rushDz * RUSH_SPEED;
            if (RUSH_BREAKS_BLOCKS) {
                smashAhead(sw, s, var10, dz);
            }

            hitAhead(sw, s);
            boolean moved = BrosHandler.tryCommit(sw, s, var10, dz, 0.0F);
            if (!moved) {
                s.rushTicksLeft = 0;
                sw.playSound(null, s.cx, Math.min(s.ya, s.yb) + 1.0, s.cz, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.PLAYERS, 1.0F, 0.7F);
                BrosHandler.tryCommit(sw, s, 0.0, 0.0, 0.0F);
            }

            double y = Math.min(s.ya, s.yb);
            sw.spawnParticles(ParticleTypes.CLOUD, s.cx - s.rushDx, y + 0.3, s.cz - s.rushDz, 2, 0.4, 0.1, 0.4, 0.01);
            if (s.rushTicksLeft == 0) {
                s.vx *= 0.3;
                s.vz *= 0.3;
            }

            return true;
        } else {
            return false;
        }
    }

    private static void smashAhead(ServerWorld sw, BrosHandler.Session s, double dx, double dz) {
        int broken = 0;
        double lx = dx * 1.5;
        double lz = dz * 1.5;

        for (LivingEntity e : new LivingEntity[]{s.a, s.b}) {
            double[] slot = BrosHandler.slotAt(s.cx + lx, s.cz + lz, s.heading, e == s.a == s.aLeft);
            double feet = e == s.a ? s.ya : s.yb;
            Box box = e.getBoundingBox()
                .offset(slot[0] - e.getX(), feet - e.getY(), slot[1] - e.getZ())
                .expand(0.15, 0.0, 0.15);
            int minY = MathHelper.floor(box.minY + 0.05);
            int maxY = MathHelper.floor(box.maxY - 0.01);

            for (BlockPos pos : BlockPos.iterate(
                MathHelper.floor(box.minX),
                minY,
                MathHelper.floor(box.minZ),
                MathHelper.floor(box.maxX),
                maxY,
                MathHelper.floor(box.maxZ)
            )) {
                if (broken >= 14) {
                    return;
                }

                BlockState st = sw.getBlockState(pos);
                if (!st.isAir() && !st.hasBlockEntity() && !st.getCollisionShape(sw, pos).isEmpty()) {
                    float hard = st.getHardness(sw, pos);
                    if (!(hard < 0.0F) && !(hard > RUSH_MAX_HARDNESS)) {
                        sw.breakBlock(pos.toImmutable(), true, breaker(s));
                        broken++;
                    }
                }
            }
        }
    }

    private static void hitAhead(ServerWorld sw, BrosHandler.Session s) {
        double y = Math.min(s.ya, s.yb);
        double fx = s.cx + s.rushDx * 1.0;
        double fz = s.cz + s.rushDz * 1.0;
        Box zone = new Box(fx - 1.6, y - 0.5, fz - 1.6, fx + 1.6, y + 2.5, fz + 1.6);
        ServerPlayerEntity src = breaker(s);
        DamageSource dmg = src != null ? sw.getDamageSources().playerAttack(src) : sw.getDamageSources().generic();

        for (LivingEntity t : sw.getEntitiesByClass(LivingEntity.class, zone, e -> e.isAlive())) {
            if (t != s.a && t != s.b && !(t instanceof ArmorStandEntity) && !(t instanceof ServerPlayerEntity sp && sp.isSpectator()) && s.rushHit.add(t.getId())) {
                t.damage(dmg, RUSH_DAMAGE);
                t.takeKnockback(1.4, -s.rushDx, -s.rushDz);
                t.addVelocity(0.0, 0.35, 0.0);
                syncVelocity(t);
                sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, t.getX(), t.getY() + 1.0, t.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                sw.spawnParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 1.0, t.getZ(), 8, 0.3, 0.4, 0.3, 0.3);
                sw.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0F, 0.8F);
            }
        }
    }

    private static void firePulse(MinecraftServer server, BrosHandler.Session s) {
        s.pulseVoteA = -1;
        s.pulseVoteB = -1;
        if (s.world instanceof ServerWorld sw) {
            double var18 = Math.min(s.ya, s.yb);
            if (s.shieldHp <= PULSE_SHIELD_COST) {
                sw.playSound(null, s.cx, var18 + 1.0, s.cz, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.6F, 1.6F);

                for (LivingEntity e : new LivingEntity[]{s.a, s.b}) {
                    if (e instanceof ServerPlayerEntity p) {
                        p.sendMessage(Text.literal("§cShield too weak to pulse"), true);
                    }
                }
            } else {
                s.shieldHp = s.shieldHp - PULSE_SHIELD_COST;
                s.pulseCdUntil = s.ticks + PULSE_COOLDOWN_TICKS;
                Box box = new Box(s.cx - PULSE_RADIUS, var18 - 1.0, s.cz - PULSE_RADIUS, s.cx + PULSE_RADIUS, var18 + 3.5, s.cz + PULSE_RADIUS);
                ServerPlayerEntity src = breaker(s);
                DamageSource dmg = src != null ? sw.getDamageSources().playerAttack(src) : sw.getDamageSources().generic();

                for (LivingEntity t : sw.getEntitiesByClass(LivingEntity.class, box, ex -> ex.isAlive())) {
                    if (t != s.a && t != s.b && !(t instanceof ArmorStandEntity) && !(t instanceof ServerPlayerEntity sp && sp.isSpectator())) {
                        double dx = t.getX() - s.cx;
                        double dz = t.getZ() - s.cz;
                        double d = Math.hypot(dx, dz);
                        if (!(d > PULSE_RADIUS)) {
                            if (d < 0.001) {
                                dx = 1.0;
                                dz = 0.0;
                                d = 1.0;
                            }

                            t.damage(dmg, PULSE_DAMAGE);
                            double falloff = 1.0 - 0.5 * (d / PULSE_RADIUS);
                            t.takeKnockback(PULSE_KNOCKBACK * falloff, -dx / d, -dz / d);
                            t.addVelocity(0.0, 0.3, 0.0);
                            syncVelocity(t);
                        }
                    }
                }

                for (ProjectileEntity proj : sw.getEntitiesByClass(ProjectileEntity.class, box, p -> !p.isRemoved())) {
                    if (Math.hypot(proj.getX() - s.cx, proj.getZ() - s.cz) <= PULSE_RADIUS) {
                        proj.discard();
                    }
                }

                for (int ring = 0; ring < 2; ring++) {
                    double r = ring == 0 ? PULSE_RADIUS * 0.45 : PULSE_RADIUS;
                    int n = ring == 0 ? 16 : 28;

                    for (int i = 0; i < n; i++) {
                        double a = (Math.PI * 2) * i / n;
                        sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, s.cx + Math.cos(a) * r, var18 + 0.6, s.cz + Math.sin(a) * r, 1, 0.0, 0.1, 0.0, 0.02);
                    }
                }

                sw.spawnParticles(ParticleTypes.EXPLOSION, s.cx, var18 + 1.0, s.cz, 1, 0.0, 0.0, 0.0, 0.0);
                sw.playSound(null, s.cx, var18 + 1.0, s.cz, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0F, 0.7F);
                sw.playSound(null, s.cx, var18 + 1.0, s.cz, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5F, 1.6F);
            }
        }
    }

    private static ServerPlayerEntity breaker(BrosHandler.Session s) {
        if (s.a instanceof ServerPlayerEntity p) {
            return p;
        } else {
            return s.b instanceof ServerPlayerEntity p ? p : null;
        }
    }

    private static void syncVelocity(Entity e) {
        if (e instanceof ServerPlayerEntity p) {
            p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
        }
    }

    private static void cooldownMsg(ServerPlayerEntity p, String what, int ticksLeft) {
        p.sendMessage(Text.literal(String.format("§7%s ready in §f%.1fs", what, ticksLeft / 20.0F)), true);
    }
}
