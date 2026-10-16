package com.cooptest;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.command.v2.CommandRegistrationCallback;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDeath;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.server.MinecraftServer;

public class RallyBeaconHandler {
    public static final long BEACON_DURATION_MS = 60000L;
    public static final boolean BEACON_DEBUG = false;
    public static final float REVIVE_COST_HEARTS = 2.0F;
    public static final float REVIVE_COST_FLOOR = 2.0F;
    public static final float REVIVE_HEALTH = 2.0F;
    public static final int REGEN_TICKS = 200;
    public static final int REGEN_AMPLIFIER = 1;
    public static final int RESISTANCE_TICKS = 100;
    public static final int RESISTANCE_AMP = 2;
    public static final int PARTICLE_INTERVAL_TICKS = 4;
    public static final double BEACON_HEIGHT = 40.0;
    public static final String[] REVIVE_LINES = new String[]{
        "§6§lIT'S NOT OVER.", "§6§lGET UP.", "§6§lTHEY NEED YOU.", "§6§lNOT TODAY.", "§6§lONE MORE ROUND.", "§6§lSTAY IN IT.", "§6§lYOUR HOMIES GOT YOU."
    };
    private static final Map<UUID, RallyBeaconHandler.Beacon> beacons = new HashMap<>();
    private static final Map<UUID, UUID> memberBeacon = new HashMap<>();

    private static boolean enabled() {
        return CoopMovesConfig.get().enableRallyBeacon;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RallyBeaconHandler::tick);
        registerDebugCommand();
        ServerLivingEntityEvents.ALLOW_DEATH.register((AllowDeath)(entity, source, amount) -> {
            if (!enabled()) {
                return true;
            } else {
                return entity instanceof ServerPlayerEntity player ? !tryRevive(player) : true;
            }
        });
    }

    public static void light(ServerWorld world, Vec3d centre, Set<UUID> members) {
        if (enabled() && !members.isEmpty()) {
            UUID id = UUID.randomUUID();
            long ms = CoopMovesConfig.get().rallyBeaconMs;
            RallyBeaconHandler.Beacon b = new RallyBeaconHandler.Beacon(
                centre,
                UUID.nameUUIDFromBytes(world.getRegistryKey().getValue().toString().getBytes()),
                new HashSet<>(members),
                System.currentTimeMillis() + ms
            );
            beacons.put(id, b);

            for (int i = 0; i < 48; i++) {
                double a = (Math.PI * 2) * i / 48.0;
                world.spawnParticles(
                    ParticleTypes.field_11220,
                    centre.x + Math.cos(a) * 1.5,
                    centre.y + 0.2,
                    centre.z + Math.sin(a) * 1.5,
                    2,
                    0.05,
                    0.1,
                    0.05,
                    0.05
                );
            }

            for (double y = 0.0; y < 40.0; y += 0.4) {
                world.spawnParticles(ParticleTypes.field_11207, centre.x, centre.y + y, centre.z, 2, 0.1, 0.03, 0.1, 0.0);
                if ((int)(y * 2.0) % 3 == 0) {
                    world.spawnParticles(ParticleTypes.field_28479, centre.x, centre.y + y, centre.z, 1, 0.18, 0.03, 0.18, 0.0);
                }
            }

            MinecraftServer server = world.getServer();

            for (UUID uid : members) {
                memberBeacon.put(uid, id);
                if (server != null) {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(uid);
                    if (p != null) {
                        p.sendMessage(Text.literal("§6§lRALLY §7— one save for the group, " + formatDuration(ms)), false);
                    }
                }
            }

            world.playSound(null, centre.x, centre.y, centre.z, ModSounds.TRUE_FRIENDSHIP, SoundCategory.field_15248, 1.2F, 0.9F);
        }
    }

    private static boolean tryRevive(ServerPlayerEntity player) {
        UUID bid = memberBeacon.get(player.getUuid());
        if (bid == null) {
            return false;
        } else {
            RallyBeaconHandler.Beacon b = beacons.get(bid);
            if (b == null) {
                memberBeacon.remove(player.getUuid());
                return false;
            } else if (System.currentTimeMillis() > b.expiresAt) {
                return false;
            } else if (b.spent) {
                return false;
            } else if (!b.used.add(player.getUuid())) {
                return false;
            } else {
                b.spent = true;
                ServerWorld world = (ServerWorld)player.getWorld();
                player.setHealth(2.0F);
                player.clearStatusEffects();
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5924, 200, 1, false, true));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5907, 100, 2, false, true));
                player.extinguish();
                player.setVelocity(Vec3d.ZERO);
                player.velocityModified = true;
                player.teleport(world, b.pos.x, b.pos.y, b.pos.z, player.getYaw(), player.getPitch());
                String line = REVIVE_LINES[(int)(Math.random() * REVIVE_LINES.length)];
                player.sendMessage(Text.literal(line), false);
                player.sendMessage(Text.literal("§7The huddle brought you back."), true);
                world.spawnParticles(ParticleTypes.field_11220, b.pos.x, b.pos.y + 1.0, b.pos.z, 90, 0.6, 0.9, 0.6, 0.45);
                world.spawnParticles(ParticleTypes.field_11207, b.pos.x, b.pos.y + 1.0, b.pos.z, 40, 0.3, 1.2, 0.3, 0.3);
                world.playSound(null, b.pos.x, b.pos.y, b.pos.z, ModSounds.TRUE_FRIENDSHIP, SoundCategory.field_15248, 1.4F, 1.0F);
                MinecraftServer server = world.getServer();
                if (server != null) {
                    for (UUID uid : b.members) {
                        if (!uid.equals(player.getUuid())) {
                            ServerPlayerEntity other = server.getPlayerManager().getPlayer(uid);
                            if (other != null && other.isAlive()) {
                                other.sendMessage(Text.literal("§6" + player.getName().getString() + " §7was saved by the huddle."), false);
                                float cost = 4.0F;
                                float left = other.getHealth() - cost;
                                if (left < 2.0F) {
                                    left = Math.min(other.getHealth(), 2.0F);
                                }

                                if (left < other.getHealth()) {
                                    other.setHealth(left);
                                    other.sendMessage(Text.literal("§c-2❤ §7you gave your strength"), true);
                                    ServerWorld ow = (ServerWorld)other.getWorld();
                                    ow.spawnParticles(
                                        ParticleTypes.field_11220, other.getX(), other.getY() + 1.0, other.getZ(), 12, 0.3, 0.5, 0.3, 0.1
                                    );
                                }
                            }
                        }
                    }
                }

                if (server != null) {
                    for (UUID uidx : b.members) {
                        ServerPlayerEntity m = server.getPlayerManager().getPlayer(uidx);
                        if (m != null) {
                            m.sendMessage(Text.literal("§8The rally is spent."), true);
                        }
                    }
                }

                world.spawnParticles(ParticleTypes.field_11251, b.pos.x, b.pos.y + 0.5, b.pos.z, 40, 0.8, 0.5, 0.8, 0.02);
                world.playSound(null, b.pos.x, b.pos.y, b.pos.z, SoundEvents.field_19344, SoundCategory.field_15248, 1.0F, 0.9F);
                beacons.values().remove(b);

                for (UUID uidxx : b.members) {
                    memberBeacon.remove(uidxx);
                }

                return true;
            }
        }
    }

    private static String formatDuration(long ms) {
        long s = Math.max(0L, ms / 1000L);
        return s % 60L == 0L && s >= 60L ? s / 60L + "m" : s + "s";
    }

    private static void tick(MinecraftServer server) {
        if (!beacons.isEmpty()) {
            long now = System.currentTimeMillis();
            beacons.entrySet()
                .removeIf(
                    e -> {
                        RallyBeaconHandler.Beacon b = e.getValue();
                        if (now > b.expiresAt) {
                            for (UUID uid : b.members) {
                                if (e.getKey().equals(memberBeacon.get(uid))) {
                                    memberBeacon.remove(uid);
                                }

                                ServerPlayerEntity p = server.getPlayerManager().getPlayer(uid);
                                if (p != null) {
                                    p.sendMessage(Text.literal("§8The rally fades."), true);
                                }
                            }

                            return true;
                        } else {
                            if (++b.ticks % 4 == 0) {
                                ServerWorld w = null;

                                for (UUID uid : b.members) {
                                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(uid);
                                    if (p != null) {
                                        w = (ServerWorld)p.getWorld();
                                        break;
                                    }
                                }

                                if (w != null) {
                                    double phase = b.ticks / 4.0 % 20.0;

                                    for (double y = phase; y < 40.0; y += 20.0) {
                                        for (double dy = 0.0; dy < 3.0; dy += 0.5) {
                                            w.spawnParticles(
                                                ParticleTypes.field_11207, b.pos.x, b.pos.y + y + dy, b.pos.z, 1, 0.1, 0.05, 0.1, 0.0
                                            );
                                        }
                                    }

                                    double spin = b.ticks / 18.0;

                                    for (int i = 0; i < 10; i++) {
                                        double a = (Math.PI * 2) * i / 10.0 + spin;
                                        w.spawnParticles(
                                            ParticleTypes.field_11220,
                                            b.pos.x + Math.cos(a) * 1.6,
                                            b.pos.y + 0.15,
                                            b.pos.z + Math.sin(a) * 1.6,
                                            1,
                                            0.02,
                                            0.02,
                                            0.02,
                                            0.0
                                        );
                                    }
                                }
                            }

                            return false;
                        }
                    }
                );
        }
    }

    private static void registerDebugCommand() {
        CommandRegistrationCallback.EVENT
            .register(
                (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
                    (LiteralArgumentBuilder)CommandManager.literal("testbeacon")
                        .executes(
                            ctx -> {
                                ServerPlayerEntity p = ((ServerCommandSource)ctx.getSource()).getPlayerOrThrow();
                                Set<UUID> solo = new HashSet<>();
                                solo.add(p.getUuid());
                                light((ServerWorld)p.getWorld(), p.getPos(), solo);
                                p.sendMessage(
                                    Text.literal("§6/testbeacon §7lit on you — take lethal damage to test the save. §8/kill bypasses it by design."),
                                    false
                                );
                                return 1;
                            }
                        )
                )
            );
    }

    public static void cleanup(UUID playerId) {
        memberBeacon.remove(playerId);

        for (RallyBeaconHandler.Beacon b : beacons.values()) {
            b.members.remove(playerId);
        }
    }

    private static final class Beacon {
        final Vec3d pos;
        final UUID worldKey;
        final Set<UUID> members;
        final Set<UUID> used = new HashSet<>();
        boolean spent = false;
        final long expiresAt;
        int ticks = 0;

        Beacon(Vec3d pos, UUID worldKey, Set<UUID> members, long expiresAt) {
            this.pos = pos;
            this.worldKey = worldKey;
            this.members = members;
            this.expiresAt = expiresAt;
        }
    }
}
