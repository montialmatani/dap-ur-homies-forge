package com.cooptest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import net.minecraft.world.World.ExplosionSourceType;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class MeteorStrikeHandler {
    public static final long ABILITY_DURATION_MS = 60000L;
    public static final long COUNTDOWN_MS = 3000L;
    public static final int CRATER_RADIUS = 10;
    public static final int DAMAGE_RADIUS = 20;
    private static final Map<UUID, Long> abilityExpiry = new HashMap<>();
    private static final Map<UUID, MeteorStrikeHandler.PendingMeteor> pendingMeteors = new HashMap<>();

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(MeteorStrikeHandler.MeteorFirePayload.ID, MeteorStrikeHandler.MeteorFirePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MeteorStrikeHandler.MeteorGrantPayload.ID, MeteorStrikeHandler.MeteorGrantPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MeteorStrikeHandler.MeteorStatusPayload.ID, MeteorStrikeHandler.MeteorStatusPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MeteorStrikeHandler.MeteorExpiredPayload.ID, MeteorStrikeHandler.MeteorExpiredPayload.CODEC);
    }

    public static void registerClientPayloads() {
        try {
            PayloadTypeRegistry.playC2S().register(MeteorStrikeHandler.MeteorFirePayload.ID, MeteorStrikeHandler.MeteorFirePayload.CODEC);
        } catch (Exception var4) {
        }

        try {
            PayloadTypeRegistry.playS2C().register(MeteorStrikeHandler.MeteorGrantPayload.ID, MeteorStrikeHandler.MeteorGrantPayload.CODEC);
        } catch (Exception var3) {
        }

        try {
            PayloadTypeRegistry.playS2C().register(MeteorStrikeHandler.MeteorStatusPayload.ID, MeteorStrikeHandler.MeteorStatusPayload.CODEC);
        } catch (Exception var2) {
        }

        try {
            PayloadTypeRegistry.playS2C().register(MeteorStrikeHandler.MeteorExpiredPayload.ID, MeteorStrikeHandler.MeteorExpiredPayload.CODEC);
        } catch (Exception var1) {
        }
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
            MeteorStrikeHandler.MeteorFirePayload.ID, (payload, ctx) -> ctx.server().execute(() -> onFire(ctx.player()))
        );
        ServerTickEvents.END_SERVER_TICK.register(MeteorStrikeHandler::tick);
    }

    public static void grantAbility(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        long expiry = System.currentTimeMillis() + 60000L;
        abilityExpiry.put(p1.getUuid(), expiry);
        abilityExpiry.put(p2.getUuid(), expiry);

        try {
            ServerPlayNetworking.send(p1, new MeteorStrikeHandler.MeteorGrantPayload(expiry));
        } catch (Exception var7) {
        }

        try {
            ServerPlayNetworking.send(p2, new MeteorStrikeHandler.MeteorGrantPayload(expiry));
        } catch (Exception var6) {
        }

        for (ServerPlayerEntity p : p1.getServer().getPlayerManager().getPlayerList()) {
            p.sendMessage(
                Text.literal(
                    "§c☄ "
                        + p1.getName().getString()
                        + " §7and §c"
                        + p2.getName().getString()
                        + " §7have unlocked §c§lMETEOR STRIKE§7! Press §lG§7 to fire!"
                ),
                false
            );
        }
    }

    public static boolean hasAbility(UUID id) {
        return abilityExpiry.containsKey(id);
    }

    public static void cleanup(UUID id) {
        abilityExpiry.remove(id);
        pendingMeteors.remove(id);
    }

    private static void onFire(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (abilityExpiry.containsKey(id)) {
            if (!pendingMeteors.containsKey(id)) {
                Vec3d eye = player.getEyePos();
                Vec3d look = player.getRotationVec(1.0F);
                BlockPos target = null;

                for (double d = 1.0; d <= 80.0; d += 0.5) {
                    Vec3d point = eye.add(look.multiply(d));
                    BlockPos bp = BlockPos.ofFloored(point);
                    if (!player.getServerWorld().getBlockState(bp).isAir()) {
                        target = bp;
                        break;
                    }
                }

                if (target == null) {
                    Vec3d endpoint = eye.add(look.multiply(80.0));
                    target = BlockPos.ofFloored(endpoint);
                }

                pendingMeteors.put(id, new MeteorStrikeHandler.PendingMeteor(id, target, player.getServerWorld()));
                Vec3d targetCenter = Vec3d.ofCenter(target);

                for (double dx = 0.0; dx < eye.distanceTo(targetCenter); dx++) {
                    Vec3d point = eye.add(look.multiply(dx));
                    player.getServerWorld().spawnParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
                }

                player.getServerWorld()
                    .playSound(
                        null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 2.0F, 0.5F
                    );
                player.sendMessage(Text.literal("§c☄ METEOR INCOMING §7— impact in 3 seconds!"), true);
            }
        }
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        abilityExpiry.entrySet().removeIf(ex -> {
            if (now >= (Long)ex.getValue()) {
                ServerPlayerEntity px = server.getPlayerManager().getPlayer((UUID)ex.getKey());
                if (px != null) {
                    try {
                        ServerPlayNetworking.send(px, new MeteorStrikeHandler.MeteorExpiredPayload());
                    } catch (Exception var6x) {
                    }
                }

                pendingMeteors.remove(ex.getKey());
                return true;
            } else {
                return false;
            }
        });

        for (MeteorStrikeHandler.PendingMeteor m : new ArrayList<>(pendingMeteors.values())) {
            long msLeft = m.impactTime - now;
            if (msLeft <= 500L && !m.invulnGranted) {
                m.invulnGranted = true;
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(m.playerId);
                if (p != null) {
                    p.setInvulnerable(true);
                }
            }

            if (server.getTicks() % 2 == 0) {
                spawnCountdownPillar(m.world, m.target, (float)msLeft / 3000.0F);
            }

            if (now >= m.impactTime) {
                pendingMeteors.remove(m.playerId);
                abilityExpiry.remove(m.playerId);
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(m.playerId);
                impact(m.world, m.target, p);
                if (p != null) {
                    try {
                        ServerPlayNetworking.send(p, new MeteorStrikeHandler.MeteorExpiredPayload());
                    } catch (Exception var12) {
                    }

                    server.execute(() -> p.setInvulnerable(false));
                }
            } else {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(m.playerId);
                if (p != null) {
                    long abilityLeft = Math.max(0L, abilityExpiry.getOrDefault(m.playerId, 0L) - now);

                    try {
                        ServerPlayNetworking.send(p, new MeteorStrikeHandler.MeteorStatusPayload(abilityLeft, msLeft));
                    } catch (Exception var13) {
                    }
                }
            }
        }

        for (Entry<UUID, Long> e : abilityExpiry.entrySet()) {
            if (!pendingMeteors.containsKey(e.getKey())) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p != null && server.getTicks() % 5 == 0) {
                    long abilityLeft = Math.max(0L, e.getValue() - now);

                    try {
                        ServerPlayNetworking.send(p, new MeteorStrikeHandler.MeteorStatusPayload(abilityLeft, -1L));
                    } catch (Exception var11) {
                    }
                }
            }
        }
    }

    private static void spawnCountdownPillar(ServerWorld world, BlockPos target, float progress) {
        double x = target.getX() + 0.5;
        double z = target.getZ() + 0.5;
        int height = (int)(50.0F * progress) + 5;

        for (int y = 0; y < height; y += 3) {
            world.spawnParticles(ParticleTypes.FLAME, x, target.getY() + y, z, 1, 0.3, 0.0, 0.3, 0.05);
        }

        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30.0 + System.currentTimeMillis() / 100.0 % 360.0);
            double radius = 3.0 * progress + 0.5;
            world.spawnParticles(
                ParticleTypes.CRIT, x + Math.cos(angle) * radius, target.getY() + 0.5, z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0
            );
        }

        if (progress < 0.5F) {
            world.playSound(
                null, x, target.getY(), z, (SoundEvent)SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.5F, 0.5F + (1.0F - progress)
            );
        }
    }

    private static void impact(ServerWorld world, BlockPos target, ServerPlayerEntity shooter) {
        Vec3d center = Vec3d.ofCenter(target);
        int r = 10;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z <= r * r) {
                        BlockPos bp = target.add(x, y, z);
                        BlockState state = world.getBlockState(bp);
                        if (!state.isAir() && state.getBlock() != Blocks.BEDROCK) {
                            world.breakBlock(bp, false);
                        }
                    }
                }
            }
        }

        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45.0);
            double ex = center.x + Math.cos(angle) * 12.0;
            double ez = center.z + Math.sin(angle) * 12.0;
            world.createExplosion(shooter, ex, center.y, ez, 12.0F, true, ExplosionSourceType.TNT);
        }

        world.createExplosion(shooter, center.x, center.y, center.z, 20.0F, true, ExplosionSourceType.TNT);
        Box hitBox = new Box(center, center).expand(20.0);

        for (Entity e : world.getOtherEntities(shooter, hitBox)) {
            if (e instanceof LivingEntity living) {
                double dist = e.getPos().distanceTo(center);
                if (!(dist > 20.0)) {
                    float dmg = (float)(25.0 * (1.0 - dist / 20.0));
                    living.damage(world.getDamageSources().explosion(null, shooter), dmg);
                    Vec3d dir = e.getPos().subtract(center).normalize();
                    if (dir.lengthSquared() < 0.001) {
                        dir = new Vec3d(0.0, 1.0, 0.0);
                    }

                    living.addVelocity(dir.x * 3.0, 2.0, dir.z * 3.0);
                    living.velocityModified = true;
                }
            }
        }

        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 20, 5.0, 5.0, 5.0, 0.0);
        world.spawnParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 200, 8.0, 4.0, 8.0, 0.5);
        world.playSound(
            null, center.x, center.y, center.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 5.0F, 0.3F
        );
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 5.0F, 0.5F);
    }

    public record MeteorExpiredPayload() implements CustomPayload {
        public static final Id<MeteorStrikeHandler.MeteorExpiredPayload> ID = new Id(new Identifier("cooptest", "meteor_expired"));
        public static final PacketCodec<PacketByteBuf, MeteorStrikeHandler.MeteorExpiredPayload> CODEC = PacketCodec.of(
            (p, buf) -> {}, buf -> new MeteorStrikeHandler.MeteorExpiredPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record MeteorFirePayload() implements CustomPayload {
        public static final Id<MeteorStrikeHandler.MeteorFirePayload> ID = new Id(new Identifier("cooptest", "meteor_fire"));
        public static final PacketCodec<PacketByteBuf, MeteorStrikeHandler.MeteorFirePayload> CODEC = PacketCodec.of(
            (p, buf) -> {}, buf -> new MeteorStrikeHandler.MeteorFirePayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record MeteorGrantPayload(long expiryMs) implements CustomPayload {
        public static final Id<MeteorStrikeHandler.MeteorGrantPayload> ID = new Id(new Identifier("cooptest", "meteor_grant"));
        public static final PacketCodec<PacketByteBuf, MeteorStrikeHandler.MeteorGrantPayload> CODEC = PacketCodec.of(
            (p, buf) -> buf.writeLong(p.expiryMs), buf -> new MeteorStrikeHandler.MeteorGrantPayload(buf.readLong())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record MeteorStatusPayload(long remainingAbilityMs, long countdownMs) implements CustomPayload {
        public static final Id<MeteorStrikeHandler.MeteorStatusPayload> ID = new Id(new Identifier("cooptest", "meteor_status"));
        public static final PacketCodec<PacketByteBuf, MeteorStrikeHandler.MeteorStatusPayload> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeLong(p.remainingAbilityMs);
            buf.writeLong(p.countdownMs);
        }, buf -> new MeteorStrikeHandler.MeteorStatusPayload(buf.readLong(), buf.readLong()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static class PendingMeteor {
        final UUID playerId;
        final BlockPos target;
        final long impactTime;
        final ServerWorld world;
        boolean invulnGranted = false;

        PendingMeteor(UUID playerId, BlockPos target, ServerWorld world) {
            this.playerId = playerId;
            this.target = target;
            this.world = world;
            this.impactTime = System.currentTimeMillis() + 3000L;
        }
    }
}
