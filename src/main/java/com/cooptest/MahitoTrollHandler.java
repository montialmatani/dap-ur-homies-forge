package com.cooptest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public class MahitoTrollHandler {
    private static final Map<UUID, MahitoTrollHandler.TrollData> trolledPlayers = new HashMap<>();
    private static final long TROLL_START_DELAY_MS = 2000L;
    private static final long TROLL_DEATH_DELAY_MS = 4000L;

    public static void register() {
        PayloadTypeRegistry.playS2C().register(MahitoTrollHandler.MahitoAnimPayload.ID, MahitoTrollHandler.MahitoAnimPayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            long now = System.currentTimeMillis();
            Iterator<Entry<UUID, MahitoTrollHandler.TrollData>> iter = trolledPlayers.entrySet().iterator();

            while (iter.hasNext()) {
                Entry<UUID, MahitoTrollHandler.TrollData> entry = iter.next();
                UUID victimId = entry.getKey();
                MahitoTrollHandler.TrollData data = entry.getValue();
                ServerPlayerEntity victim = server.getPlayerManager().getPlayer(victimId);
                if (victim != null && victim.isAlive()) {
                    long elapsed = now - data.dapTime;
                    if (!data.trollStarted && elapsed >= 2000L) {
                        data.trollStarted = true;
                        startTroll(victim, server.getPlayerManager().getPlayer(data.trollerId));
                    }

                    if (data.trollStarted && elapsed >= 6000L) {
                        executeDeath(victim);
                        iter.remove();
                    }

                    if (data.trollStarted) {
                        victim.setVelocity(0.0, victim.getVelocity().y, 0.0);
                        victim.velocityModified = true;
                    }
                } else {
                    iter.remove();
                }
            }
        });
    }

    public static void checkForMahitoTroll(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        boolean p1HasMahito = p1.hasStatusEffect(ModEffects.MAHITO);
        boolean p2HasMahito = p2.hasStatusEffect(ModEffects.MAHITO);
        if (p1HasMahito && !p2HasMahito) {
            startMahitoTroll(p2, p1);
            p1.removeStatusEffect(ModEffects.MAHITO);
        } else if (p2HasMahito && !p1HasMahito) {
            startMahitoTroll(p1, p2);
            p2.removeStatusEffect(ModEffects.MAHITO);
        }
    }

    private static void startMahitoTroll(ServerPlayerEntity victim, ServerPlayerEntity troller) {
        trolledPlayers.put(victim.getUuid(), new MahitoTrollHandler.TrollData(System.currentTimeMillis(), troller.getUuid()));
        if (troller != null) {
            troller.sendMessage(Text.literal("§c§l☠ You cursed " + victim.getName().getString() + "! ☠"), true);
        }
    }

    private static void startTroll(ServerPlayerEntity victim, ServerPlayerEntity troller) {
        ServerWorld world = victim.getServerWorld();
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 255, false, false));
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 100, 1, false, false));
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 100, 128, false, false));
        world.playSound(null, victim.getX(), victim.getY(), victim.getZ(), ModSounds.MAHITO, SoundCategory.PLAYERS, 2.0F, 1.0F);

        for (ServerPlayerEntity player : world.getPlayers()) {
            ServerPlayNetworking.send(player, new MahitoTrollHandler.MahitoAnimPayload(victim.getUuid()));
        }

        world.spawnParticles(ParticleTypes.SOUL, victim.getX(), victim.getY() + 1.0, victim.getZ(), 20, 0.5, 1.0, 0.5, 0.02);
        world.spawnParticles(ParticleTypes.SMOKE, victim.getX(), victim.getY() + 1.0, victim.getZ(), 15, 0.4, 0.8, 0.4, 0.01);
        victim.sendMessage(Text.literal("§4§l☠ MAHITO'S CURSE! ☠"), true);
        world.playSound(
            null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.5F, 1.5F
        );
    }

    private static void executeDeath(ServerPlayerEntity victim) {
        ServerWorld world = victim.getServerWorld();
        double x = victim.getX();
        double y = victim.getY();
        double z = victim.getZ();
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 2.0, z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.FIREWORK, x, y + 2.0, z, 50, 0.5, 0.5, 0.5, 0.3);
        world.spawnParticles(ParticleTypes.FLASH, x, y + 2.0, z, 3, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 2.0, z, 30, 0.4, 0.4, 0.4, 0.15);
        world.spawnParticles(ParticleTypes.DRAGON_BREATH, x, y + 2.0, z, 20, 0.3, 0.3, 0.3, 0.1);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 2.0F, 1.0F);
        world.playSound(null, x, y, z, (SoundEvent)SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.5F, 1.2F);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0F, 0.5F);
        victim.damage(world.getDamageSources().magic(), Float.MAX_VALUE);

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player != victim) {
                player.sendMessage(Text.literal("§4" + victim.getName().getString() + " §7was trolled by §cMahito's Curse!"), false);
            }
        }
    }

    public static boolean isBeingTrolled(UUID playerId) {
        return trolledPlayers.containsKey(playerId);
    }

    public static void cleanup(UUID playerId) {
        trolledPlayers.remove(playerId);
    }

    public record MahitoAnimPayload(UUID playerId) implements CustomPayload {
        public static final Id<MahitoTrollHandler.MahitoAnimPayload> ID = new Id(new Identifier("cooptest", "mahito_anim"));
        public static final PacketCodec<PacketByteBuf, MahitoTrollHandler.MahitoAnimPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeUuid(payload.playerId), buf -> new MahitoTrollHandler.MahitoAnimPayload(buf.readUuid())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static class TrollData {
        long dapTime;
        boolean trollStarted;
        UUID trollerId;

        TrollData(long time, UUID troller) {
            this.dapTime = time;
            this.trollStarted = false;
            this.trollerId = troller;
        }
    }
}
