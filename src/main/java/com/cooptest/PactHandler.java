package com.cooptest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.command.v2.CommandRegistrationCallback;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDeath;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.server.MinecraftServer;

public class PactHandler {
    public static final long SEAL_HOLD_MS = 7000L;
    private static final Map<String, PactHandler.Bond> bonds = new HashMap<>();
    private static final Map<String, Set<UUID>> declaredBroken = new HashMap<>();
    private static final Set<UUID> betrayers = new HashSet<>();
    private static final Map<UUID, PactHandler.Sealing> sealing = new HashMap<>();
    private static final Map<UUID, PactHandler.Pact> pacts = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static long sealHoldMs() {
        return 7000L;
    }

    public static long holdTimeoutMs() {
        return CoopMovesConfig.get().pactHoldTimeoutMs;
    }

    public static long pactDurationMs() {
        return CoopMovesConfig.get().pactDurationMs;
    }

    public static float betrayalDamage() {
        return CoopMovesConfig.get().pactBetrayalDamage;
    }

    public static boolean enabled() {
        return CoopMovesConfig.get().enablePact;
    }

    private static Path savePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("coopmoves_pacts.json");
    }

    private static String key(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? a + "_" + b : b + "_" + a;
    }

    public static PactHandler.Bond bond(UUID a, UUID b) {
        return bonds.computeIfAbsent(key(a, b), k -> new PactHandler.Bond());
    }

    public static void load() {
        try {
            Path p = savePath();
            if (!Files.exists(p)) {
                return;
            }

            try (Reader r = Files.newBufferedReader(p)) {
                Map<String, PactHandler.Bond> loaded = (Map<String, PactHandler.Bond>)GSON.fromJson(
                    r, (new TypeToken<Map<String, PactHandler.Bond>>() {}).getType()
                );
                if (loaded != null) {
                    bonds.putAll(loaded);
                }
            }
        } catch (Exception var6) {
            System.err.println("[Pact] could not load bonds: " + var6);
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(savePath())) {
            GSON.toJson(bonds, w);
        } catch (Exception var5) {
            System.err.println("[Pact] could not save bonds: " + var5);
        }
    }

    public static void register() {
        if (enabled()) {
            load();
            ServerTickEvents.END_SERVER_TICK.register(PactHandler::tick);
            registerCommands();
            ServerLivingEntityEvents.AFTER_DEATH.register((AfterDeath)(entity, source) -> {
                if (entity instanceof ServerPlayerEntity victim) {
                    if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
                        PactHandler.Pact pact = pacts.get(victim.getUuid());
                        if (pact != null) {
                            UUID other = pact.a.equals(victim.getUuid()) ? pact.b : pact.a;
                            if (other.equals(attacker.getUuid())) {
                                breakPact(victim.getServer(), pact, attacker.getUuid());
                            }
                        }
                    }
                }
            });
        }
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT
            .register(
                (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
                    (LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("handshake").executes(ctx -> {
                        listPacts(((ServerCommandSource)ctx.getSource()).getPlayerOrThrow());
                        return 1;
                    })).then(((RequiredArgumentBuilder)CommandManager.argument("target", EntityArgumentType.player()).executes(ctx -> {
                        ServerPlayerEntity me = ((ServerCommandSource)ctx.getSource()).getPlayerOrThrow();
                        ServerPlayerEntity them = EntityArgumentType.getPlayer(ctx, "target");
                        showRecord(me, them);
                        return 1;
                    })).then(CommandManager.literal("broken").executes(ctx -> {
                        ServerPlayerEntity me = ((ServerCommandSource)ctx.getSource()).getPlayerOrThrow();
                        ServerPlayerEntity them = EntityArgumentType.getPlayer(ctx, "target");
                        declareBroken(me, them);
                        return 1;
                    })))
                )
            );
    }

    private static void listPacts(ServerPlayerEntity me) {
        PactHandler.Pact pact = pacts.get(me.getUuid());
        if (pact == null) {
            me.sendMessage(Text.literal("§7No active pact. Shake hands and hold G to make one."), false);
        } else {
            UUID other = pact.a.equals(me.getUuid()) ? pact.b : pact.a;
            PactHandler.Bond b = bond(me.getUuid(), other);
            String name = "§7(offline)";
            MinecraftServer srv = me.getServer();
            if (srv != null) {
                ServerPlayerEntity op = srv.getPlayerManager().getPlayer(other);
                if (op != null) {
                    name = "§f" + op.getName().getString();
                }
            }

            long heldMin = (System.currentTimeMillis() - pact.sealedAt) / 60000L;
            me.sendMessage(Text.literal("§6\ud83e\udd1d " + name + "  " + tier(b)), false);
            me.sendMessage(Text.literal("§7Handshakes §f" + b.daps + " §8| §7Pacts §f" + b.sealed + " §8| §aKept §f" + b.kept), false);
            me.sendMessage(Text.literal("§7Betrayed §c" + b.broken + " §8| §7Confirmed §f" + b.confirmed + " §8| §7Held §f" + heldMin + "m"), false);
            me.sendMessage(Text.literal(pact.confirmed ? "§a✔ Confirmed" : "§e○ Not confirmed §7— shake hands again to confirm it"), false);
            me.sendMessage(Text.literal("§8/handshake " + name.replaceAll("§.", "") + " broken §8— if they betray the pact"), false);
        }
    }

    private static void showRecord(ServerPlayerEntity me, ServerPlayerEntity them) {
        int totalSealed = 0;
        int totalKept = 0;
        int totalBroken = 0;
        int totalDissolved = 0;
        String prefix = them.getUuid().toString();

        for (Entry<String, PactHandler.Bond> e : bonds.entrySet()) {
            if (e.getKey().contains(prefix)) {
                PactHandler.Bond x = e.getValue();
                totalSealed += x.sealed;
                totalKept += x.kept;
                totalBroken += x.broken;
                totalDissolved += x.confirmed;
            }
        }

        me.sendMessage(Text.literal("§6" + them.getName().getString() + "§7's record"), false);
        me.sendMessage(
            Text.literal(
                "§7Pacts §f" + totalSealed + " §8| §aKept §f" + totalKept + " §8| §cBetrayed §f" + totalBroken + " §8| §7Confirmed §f" + totalDissolved
            ),
            false
        );
        if (totalBroken > 0) {
            me.sendMessage(Text.literal("§c⚠ Has betrayed " + totalBroken + " pact(s)."), false);
        }

        PactHandler.Bond shared = bond(me.getUuid(), them.getUuid());
        me.sendMessage(
            Text.literal(
                "§7With you: " + tier(shared) + " §8| §7handshakes §f" + shared.daps + " §8| §akept §f" + shared.kept + " §8| §cbetrayed §f" + shared.broken
            ),
            false
        );
    }

    private static void declareBroken(ServerPlayerEntity me, ServerPlayerEntity them) {
        if (me.getUuid().equals(them.getUuid())) {
            me.sendMessage(Text.literal("§7Name the player who betrayed you, not yourself."), false);
        } else {
            PactHandler.Pact pact = pacts.get(me.getUuid());
            if (pact != null && (pact.a.equals(them.getUuid()) || pact.b.equals(them.getUuid()))) {
                PactHandler.Bond b = bond(me.getUuid(), them.getUuid());
                b.broken++;
                betrayers.add(them.getUuid());
                pacts.remove(pact.a);
                pacts.remove(pact.b);
                declaredBroken.remove(key(me.getUuid(), them.getUuid()));
                save();
                me.sendMessage(Text.literal("§c§lPACT BROKEN §7— " + them.getName().getString() + " betrayed you."), false);
                them.sendMessage(Text.literal("§c§lYOU BROKE THE PACT §7— " + me.getName().getString() + " has declared your betrayal."), false);
                ServerWorld w = (ServerWorld)me.getWorld();
                ceremonyEffect(w, me.getPos().add(0.0, 1.0, 0.0), false);
                announce(
                    me.getServer(),
                    Text.literal("§c§l⚠ " + them.getName().getString() + " §7betrayed §c" + me.getName().getString() + "§7's pact.")
                );
            } else {
                me.sendMessage(Text.literal("§7You have no pact with " + them.getName().getString() + "."), false);
            }
        }
    }

    public static void onHandshakeCounted(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (enabled()) {
            PactHandler.Bond b = bond(p1.getUuid(), p2.getUuid());
            warnIfBadReputation(p1, p2);
            warnIfBadReputation(p2, p1);
            b.daps++;
            save();
        }
    }

    public static void onHandshakeStart(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (enabled()) {
            PactHandler.Bond b = bond(p1.getUuid(), p2.getUuid());
            PactHandler.Pact existing = pacts.get(p1.getUuid());
            if (existing != null && (existing.a.equals(p2.getUuid()) || existing.b.equals(p2.getUuid()))) {
                confirmBond(p1, p2, b);
            }

            UUID lo = p1.getUuid().compareTo(p2.getUuid()) <= 0 ? p1.getUuid() : p2.getUuid();
            UUID hi = lo.equals(p1.getUuid()) ? p2.getUuid() : p1.getUuid();
            PactHandler.Sealing s = new PactHandler.Sealing(lo, hi);
            sealing.put(lo, s);
            sealing.put(hi, s);
        }
    }

    private static void confirmBond(ServerPlayerEntity p1, ServerPlayerEntity p2, PactHandler.Bond b) {
        ServerWorld w = (ServerWorld)p1.getWorld();
        Vec3d m = p1.getPos().add(p2.getPos()).multiply(0.5).add(0.0, 1.3, 0.0);
        PactHandler.Pact p = pacts.get(p1.getUuid());
        boolean firstConfirm = p != null && !p.confirmed;
        if (firstConfirm) {
            p.confirmed = true;
            b.confirmed++;
        }

        if (p != null) {
            p.sealedAt = System.currentTimeMillis();
        }

        save();
        w.playSound(null, m.x, m.y, m.z, ModSounds.TRUE_FRIENDSHIP, SoundCategory.field_15248, 1.0F, 1.2F);
        w.playSound(null, m.x, m.y, m.z, ModSounds.EPIC_DAP, SoundCategory.field_15248, 1.0F, 1.0F);
        w.spawnParticles(ParticleTypes.field_11220, m.x, m.y, m.z, 60, 0.5, 0.5, 0.5, 0.35);
        w.spawnParticles(ParticleTypes.field_11207, m.x, m.y, m.z, 30, 0.3, 0.5, 0.3, 0.3);
        w.spawnParticles(ParticleTypes.field_11248, m.x, m.y, m.z, 40, 0.4, 0.4, 0.4, 0.28);
        String word = b.kept > 0 ? "§6§lBROTHER" : "§6§lHOMIE";
        p1.sendMessage(Text.literal(word + " §7— " + p2.getName().getString()), true);
        p2.sendMessage(Text.literal(word + " §7— " + p1.getName().getString()), true);
        if (firstConfirm) {
            ceremonyEffect(w, m, true);
            p1.sendMessage(Text.literal("§a§l✔ PACT CONFIRMED"), false);
            p2.sendMessage(Text.literal("§a§l✔ PACT CONFIRMED"), false);
            announce(
                p1.getServer(),
                Text.literal("§a✔ §6" + p1.getName().getString() + " §7and §6" + p2.getName().getString() + " §7confirmed their pact.")
            );
        }
    }

    private static void send(ServerPlayerEntity to, String otherName, PactHandler.Bond b) {
        to.sendMessage(Text.literal(tier(b) + " §7with §f" + otherName), true);
    }

    private static void warnIfBadReputation(ServerPlayerEntity to, ServerPlayerEntity about) {
        int total = 0;
        String prefix = about.getUuid().toString();

        for (Entry<String, PactHandler.Bond> e : bonds.entrySet()) {
            if (e.getKey().contains(prefix)) {
                total += e.getValue().broken;
            }
        }

        if (total > 0) {
            to.sendMessage(
                Text.literal("§c⚠ " + about.getName().getString() + " has betrayed " + total + " pact" + (total == 1 ? "" : "s")), true
            );
        }
    }

    public static String tier(PactHandler.Bond b) {
        int score = b.daps + b.sealed * 5 + b.confirmed * 8 + b.kept * 10 - b.broken * 15;
        if (b.broken > 0 && score < 0) {
            return "§cBROKEN BOND";
        } else if (score >= 200) {
            return "§d§lBLOOD BROTHERS";
        } else if (score >= 100) {
            return "§5HOMIES";
        } else if (score >= 50) {
            return "§bTRUSTED";
        } else if (score >= 20) {
            return "§aFRIENDS";
        } else {
            return score >= 5 ? "§eACQUAINTED" : "§7STRANGERS";
        }
    }

    public static void onHold(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        PactHandler.Sealing s = sealing.get(id);
        if (s != null && !s.done) {
            long now = System.currentTimeMillis();
            long timeout = holdTimeoutMs();
            boolean isA = id.equals(s.a);
            if (isA) {
                if (now - s.holdA > timeout) {
                    s.startedA = now;
                }

                s.holdA = now;
            } else {
                if (now - s.holdB > timeout) {
                    s.startedB = now;
                }

                s.holdB = now;
            }
        }
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();

        for (PactHandler.Sealing s : new HashSet<>(sealing.values())) {
            if (!s.done) {
                ServerPlayerEntity a = server.getPlayerManager().getPlayer(s.a);
                ServerPlayerEntity b = server.getPlayerManager().getPlayer(s.b);
                if (a != null && b != null) {
                    boolean bothHolding = now - s.holdA <= holdTimeoutMs() && now - s.holdB <= holdTimeoutMs();
                    if (bothHolding) {
                        long since = now - Math.max(s.startedA, s.startedB);
                        float progress = Math.min(1.0F, (float)since / (float)sealHoldMs());
                        String bar = bar(progress);
                        a.sendMessage(Text.literal(bar), true);
                        b.sendMessage(Text.literal(bar), true);
                        Vec3d mid = a.getPos().add(b.getPos()).multiply(0.5).add(0.0, 1.0, 0.0);
                        ServerWorld world = (ServerWorld)a.getWorld();
                        world.spawnParticles(ParticleTypes.field_11207, mid.x, mid.y + progress, mid.z, 2, 0.12, 0.05, 0.12, 0.01);
                        if (progress >= 1.0F) {
                            s.done = true;
                            sealPact(server, world, mid, a, b);
                            clearSealing(s);
                        }
                    }
                } else {
                    clearSealing(s);
                }
            }
        }

        for (PactHandler.Pact p : new HashSet<>(pacts.values())) {
            if (now - p.sealedAt >= pactDurationMs()) {
                PactHandler.Bond bd = bond(p.a, p.b);
                bd.kept++;
                save();
                pacts.remove(p.a);
                pacts.remove(p.b);
                ServerPlayerEntity a = server.getPlayerManager().getPlayer(p.a);
                ServerPlayerEntity b = server.getPlayerManager().getPlayer(p.b);
                String line = "§6§l⚔ PACT KEPT ⚔";
                String sub = "§7You are now " + tier(bd) + "§7.";
                if (a != null) {
                    a.sendMessage(Text.literal(line), false);
                    a.sendMessage(Text.literal(sub), false);
                    a.sendMessage(Text.literal("§8Shake hands again to start another."), false);
                }

                if (b != null) {
                    b.sendMessage(Text.literal(line), false);
                    b.sendMessage(Text.literal(sub), false);
                    b.sendMessage(Text.literal("§8Shake hands again to start another."), false);
                }
            }
        }
    }

    private static String bar(float progress) {
        int segments = Math.max(1, CoopMovesConfig.get().pactBarSegments);
        int filled = Math.round(progress * segments);
        StringBuilder sb = new StringBuilder("§7[");

        for (int i = 0; i < segments; i++) {
            sb.append(i < filled ? "§a|" : "§8|");
        }

        sb.append("§7] ").append(progress >= 1.0F ? "§a§lPACT" : "§e" + Math.round(progress * 100.0F) + "%");
        return sb.toString();
    }

    private static void announce(MinecraftServer server, Text line) {
        if (server != null) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.sendMessage(line, false);
            }
        }
    }

    private static void ceremonyEffect(ServerWorld world, Vec3d mid, boolean sealed) {
        if (sealed) {
            for (int i = 0; i < 60; i++) {
                double a = (Math.PI * 2) * i / 60.0;

                for (double y = 0.0; y < 3.0; y += 0.5) {
                    world.spawnParticles(
                        ParticleTypes.field_11207,
                        mid.x + Math.cos(a) * 1.4,
                        mid.y + y,
                        mid.z + Math.sin(a) * 1.4,
                        1,
                        0.02,
                        0.02,
                        0.02,
                        0.0
                    );
                }
            }

            world.spawnParticles(ParticleTypes.field_11220, mid.x, mid.y + 1.0, mid.z, 120, 0.6, 0.9, 0.6, 0.5);
            world.spawnParticles(ParticleTypes.field_11248, mid.x, mid.y + 1.4, mid.z, 80, 0.5, 0.6, 0.5, 0.45);
            world.playSound(null, mid.x, mid.y, mid.z, ModSounds.TRUE_FRIENDSHIP, SoundCategory.field_15248, 1.3F, 1.0F);
            world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.field_15188, SoundCategory.field_15248, 1.2F, 1.2F);
        } else {
            world.spawnParticles(ParticleTypes.field_11237, mid.x, mid.y + 1.0, mid.z, 80, 0.7, 0.7, 0.7, 0.1);
            world.spawnParticles(ParticleTypes.field_23114, mid.x, mid.y + 1.0, mid.z, 50, 0.5, 0.7, 0.5, 0.18);
            world.spawnParticles(ParticleTypes.field_11205, mid.x, mid.y + 1.2, mid.z, 60, 0.6, 0.6, 0.6, 0.3);
            world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.field_14688, SoundCategory.field_15248, 1.0F, 0.6F);
            world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.field_15081, SoundCategory.field_15248, 1.2F, 0.7F);
        }
    }

    private static void sealPact(MinecraftServer server, ServerWorld world, Vec3d mid, ServerPlayerEntity a, ServerPlayerEntity b) {
        PactHandler.Bond bd = bond(a.getUuid(), b.getUuid());
        bd.sealed++;
        ceremonyEffect(world, mid, true);
        save();
        PactHandler.Pact pact = new PactHandler.Pact(a.getUuid(), b.getUuid(), System.currentTimeMillis());
        pacts.put(a.getUuid(), pact);
        pacts.put(b.getUuid(), pact);

        for (int y = 0; y < 24; y++) {
            world.spawnParticles(ParticleTypes.field_11207, mid.x, mid.y + y * 0.4, mid.z, 4, 0.15, 0.03, 0.15, 0.01);
            world.spawnParticles(ParticleTypes.field_11248, mid.x, mid.y + y * 0.4, mid.z, 3, 0.25, 0.05, 0.25, 0.04);
        }

        for (int i = 0; i < 90; i++) {
            double ang = (Math.PI * 2) * i / 90.0;
            world.spawnParticles(
                ParticleTypes.field_11220, mid.x + Math.cos(ang) * 2.5, mid.y, mid.z + Math.sin(ang) * 2.5, 1, 0.0, 0.05, 0.0, 0.02
            );
        }

        world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.field_15188, SoundCategory.field_15248, 1.2F, 1.0F);
        world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.field_15195, SoundCategory.field_15248, 0.9F, 1.0F);
        world.playSound(null, mid.x, mid.y, mid.z, ModSounds.TRUE_FRIENDSHIP, SoundCategory.field_15248, 1.0F, 1.0F);
        String tier = tier(bd);

        for (ServerPlayerEntity p : new ServerPlayerEntity[]{a, b}) {
            p.sendMessage(Text.literal("§6§l⚔ PACT SEALED ⚔"), false);
            p.sendMessage(Text.literal("§7Your bond is now §f" + tier), false);
        }

        announce(
            server,
            Text.literal(
                "§6§l\ud83e\udd1d " + a.getName().getString() + " §7and §6§l" + b.getName().getString() + " §7have agreed a pact. §8(" + tier + "§8)"
            )
        );
    }

    private static void breakPact(MinecraftServer server, PactHandler.Pact pact, UUID breaker) {
        pacts.remove(pact.a);
        pacts.remove(pact.b);
        PactHandler.Bond bd = bond(pact.a, pact.b);
        bd.broken++;
        save();
        if (server != null) {
            ServerPlayerEntity a = server.getPlayerManager().getPlayer(pact.a);
            ServerPlayerEntity b = server.getPlayerManager().getPlayer(pact.b);
            ServerPlayerEntity br = server.getPlayerManager().getPlayer(breaker);
            String brName = br != null ? br.getName().getString() : "Someone";
            Text announcement = Text.literal("§4§l☠ " + brName + " BROKE THE PACT ☠");
            if (CoopMovesConfig.get().pactBroadcastBetrayal) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    p.sendMessage(announcement, false);
                }
            } else {
                if (a != null) {
                    a.sendMessage(announcement, false);
                }

                if (b != null) {
                    b.sendMessage(announcement, false);
                }
            }

            if (a != null) {
                ServerWorld w = (ServerWorld)a.getWorld();
                Vec3d pos = a.getPos().add(0.0, 1.0, 0.0);
                w.playSound(null, pos.x, pos.y, pos.z, ModSounds.DAP_MISS, SoundCategory.field_15248, 1.0F, 0.6F);
                w.spawnParticles(ParticleTypes.field_11251, pos.x, pos.y, pos.z, 60, 0.6, 0.6, 0.6, 0.05);
            }

            if (b != null) {
                ServerWorld w = (ServerWorld)b.getWorld();
                Vec3d pos = b.getPos().add(0.0, 1.0, 0.0);
                w.spawnParticles(ParticleTypes.field_11251, pos.x, pos.y, pos.z, 60, 0.6, 0.6, 0.6, 0.05);
            }
        }
    }

    public static boolean hasPact(UUID id) {
        return pacts.containsKey(id);
    }

    public static boolean isSealing(UUID id) {
        PactHandler.Sealing s = sealing.get(id);
        return s != null && !s.done;
    }

    private static void clearSealing(PactHandler.Sealing s) {
        sealing.remove(s.a);
        sealing.remove(s.b);
    }

    public static void cleanup(UUID id) {
        PactHandler.Sealing s = sealing.get(id);
        if (s != null) {
            clearSealing(s);
        }
    }

    public static class Bond {
        public int daps;
        public int sealed;
        public int kept;
        public int broken;
        public int confirmed;
        public int dissolved;
    }

    private static final class Pact {
        final UUID a;
        final UUID b;
        long sealedAt;
        boolean confirmed = false;

        Pact(UUID a, UUID b, long sealedAt) {
            this.a = a;
            this.b = b;
            this.sealedAt = sealedAt;
        }
    }

    private static final class Sealing {
        final UUID a;
        final UUID b;
        long holdA;
        long holdB;
        long startedA;
        long startedB;
        boolean done;

        Sealing(UUID a, UUID b) {
            this.a = a;
            this.b = b;
        }
    }
}
