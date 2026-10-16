package com.cooptest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Box;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.server.MinecraftServer;

public class HighFiveStreakHandler {
    private static final Map<UUID, HighFiveStreakHandler.Streak> streaks = new HashMap<>();

    private static boolean enabled() {
        return CoopMovesConfig.get().enableHighFiveBuffs;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            try {
                tick(server);
            } catch (Throwable var2) {
                System.err.println("[Hype] tick failed, clearing streaks: " + var2);
                streaks.clear();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> streaks.clear());
    }

    public static void onFastFive(ServerPlayerEntity p) {
        if (enabled() && p != null && p.isAlive()) {
            CoopMovesConfig c = CoopMovesConfig.get();
            int max = Math.max(1, c.hypeMaxStacks);
            HighFiveStreakHandler.Streak s = streaks.computeIfAbsent(p.getUuid(), x -> new HighFiveStreakHandler.Streak());
            int before = s.stacks;
            s.stacks = Math.min(max, s.stacks + 1);
            s.lastFiveMs = System.currentTimeMillis();
            applyBuffs(p, s.stacks, c);
            ServerWorld world = (ServerWorld)p.getWorld();
            world.spawnParticles(ParticleTypes.field_11205, p.getX(), p.getY() + 2.1, p.getZ(), 4 + s.stacks * 2, 0.2, 0.15, 0.2, 0.1);
            int tornadoAt = Math.max(1, c.hypeTornadoStacks);
            if (c.enableHypeTornado && before < tornadoAt && s.stacks >= tornadoAt) {
                world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.field_14550, SoundCategory.field_15248, 1.0F, 1.3F);
                world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.field_14706, SoundCategory.field_15248, 1.0F, 0.6F);
            }

            String bar = "§6" + "▮".repeat(s.stacks) + "§8" + "▮".repeat(max - s.stacks);
            p.sendMessage(Text.literal("§e§lHYPE §7[" + bar + "§7]"), true);
        }
    }

    private static void applyBuffs(ServerPlayerEntity p, int stacks, CoopMovesConfig c) {
        int ticks = (int)Math.max(20L, c.hypeDecayMs / 50L);
        int speedAmp = Math.min(Math.max(0, c.hypeMaxSpeedAmp), stacks - 1);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5904, ticks, speedAmp, false, true));
        if (stacks >= 3) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5913, ticks, stacks >= 5 ? 1 : 0, false, true));
        }
    }

    private static void tick(MinecraftServer server) {
        if (!streaks.isEmpty()) {
            CoopMovesConfig c = CoopMovesConfig.get();
            long now = System.currentTimeMillis();
            Iterator<Entry<UUID, HighFiveStreakHandler.Streak>> it = streaks.entrySet().iterator();

            while (it.hasNext()) {
                Entry<UUID, HighFiveStreakHandler.Streak> e = it.next();
                HighFiveStreakHandler.Streak s = e.getValue();
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p != null && p.isAlive()) {
                    if (now - s.lastFiveMs >= c.hypeDecayMs) {
                        p.sendMessage(Text.literal("§8Hype faded"), true);
                        it.remove();
                    } else {
                        s.ticks++;
                        if (c.enableHypeTornado && s.stacks >= Math.max(1, c.hypeTornadoStacks)) {
                            tornado(p, s, c);
                        }
                    }
                } else {
                    it.remove();
                }
            }
        }
    }

    private static void tornado(ServerPlayerEntity p, HighFiveStreakHandler.Streak s, CoopMovesConfig c) {
        if (p.getWorld() instanceof ServerWorld w) {
            int var30 = s.stacks - Math.max(1, c.hypeTornadoStacks);
            double r = c.hypeTornadoRadius + var30 * c.hypeTornadoRadiusPerStack;
            double height = 3.0 + var30 * 0.4;
            double x = p.getX();
            double y = p.getY();
            double z = p.getZ();
            int points = Math.max(1, c.hypeTornadoParticles);
            double spin = s.ticks * 0.55;

            for (int box = 0; box < points; box++) {
                double k = (box + 0.5) / points;
                double a = spin + box * 2.4;
                double rr = r * (0.3 + 0.7 * k);
                w.spawnParticles(ParticleTypes.field_11204, x + Math.cos(a) * rr, y + k * height, z + Math.sin(a) * rr, 1, 0.0, 0.0, 0.0, 0.0);
            }

            if (s.ticks % 10 == 0) {
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.2, z, 1, 0.0, 0.0, 0.0, 0.0);
            }

            if (s.ticks % 30 == 0) {
                w.playSound(null, x, y, z, SoundEvents.field_14572, SoundCategory.field_15248, 0.35F, 1.3F);
            }

            Box box = new Box(x - r, y - 1.0, z - r, x + r, y + height, z + r);

            for (LivingEntity t : w.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != p)) {
                if (!(t instanceof ArmorStandEntity)
                    && !(t instanceof ServerPlayerEntity sp && (sp.isSpectator() || getStacks(sp.getUuid()) > 0))
                    && t != p.getVehicle()
                    && !t.hasPassenger(p)) {
                    double dx = t.getX() - x;
                    double dz = t.getZ() - z;
                    double d = Math.hypot(dx, dz);
                    if (!(d > r)) {
                        if (d < 0.001) {
                            dx = 1.0;
                            dz = 0.0;
                            d = 1.0;
                        }

                        dx /= d;
                        dz /= d;
                        double push = c.hypeTornadoPush * (1.0 - 0.4 * d / r);
                        t.setVelocity(dx * push - dz * push * 0.4, Math.max(t.getVelocity().y, 0.42), dz * push + dx * push * 0.4);
                        t.velocityModified = true;
                        if (t instanceof ServerPlayerEntity spx) {
                            spx.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(spx));
                        }
                    }
                }
            }
        }
    }

    public static void cleanup(UUID id) {
        streaks.remove(id);
    }

    public static int getStacks(UUID id) {
        HighFiveStreakHandler.Streak s = streaks.get(id);
        return s == null ? 0 : s.stacks;
    }

    private static final class Streak {
        int stacks;
        long lastFiveMs;
        int ticks;
    }
}
