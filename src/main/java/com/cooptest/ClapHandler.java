package com.cooptest;

import com.cooptest.client.CoopAnimationHandler;
import com.cooptest.spin.HandSpinHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.util.math.Box;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class ClapHandler {
    private static final long TIER_SLOW_MS = 600L;
    private static final long TIER_MEDIUM_MS = 200L;
    private static final long SYNC_WINDOW_MS = 300L;
    private static final double SYNC_RANGE = 6.0;
    private static final int IMPACT_TICKS_SLOW = 5;
    private static final int IMPACT_TICKS_MEDIUM = 3;
    private static final int IMPACT_TICKS_FAST = 2;
    private static final Random RANDOM = new Random();
    private static final Map<UUID, Long> lastPressTime = new HashMap<>();
    private static final List<ClapHandler.ScheduledEffect> scheduled = new ArrayList<>();
    private static final Set<UUID> rightClickHeld = new HashSet<>();
    private static final Map<UUID, Entity> firstTarget = new HashMap<>();

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(ClapHandler.ClapRequestPayload.ID, ClapHandler.ClapRequestPayload.CODEC);
        registerTodoPayload();
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ClapHandler.ClapRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> onClapRequest(player));
        });
        ServerTickEvents.END_SERVER_TICK.register(ClapHandler::tick);
        registerTodoReceiver();
    }

    private static void onClapRequest(ServerPlayerEntity player) {
        if (!GrabMechanic.isHolding(player)) {
            if (!HandSpinHandler.isInSession(player.getUuid())) {
                UUID id = player.getUuid();
                long now = System.currentTimeMillis();
                Long last = lastPressTime.get(id);
                int tier;
                if (last == null || now - last > 600L) {
                    tier = 0;
                } else if (now - last > 200L) {
                    tier = 1;
                } else {
                    tier = 2;
                }

                lastPressTime.put(id, now);
                if (player.hasStatusEffect(ModEffects.TODO)) {
                    performTodoSwap(player);
                }
                CoopAnimationHandler.AnimState animState = switch (tier) {
                    case 1 -> CoopAnimationHandler.AnimState.CLAP_SPAM;
                    case 2 -> CoopAnimationHandler.AnimState.CLAP_STRONG;
                    default -> CoopAnimationHandler.AnimState.CLAP;
                };
                PoseNetworking.broadcastAnimState(player, animState.ordinal());
                if (tier == 2) {
                    Box fearBox = player.getBoundingBox().expand(15.0);
                    player.getServerWorld().getEntitiesByClass(AnimalEntity.class, fearBox, a -> !a.isRemoved()).forEach(animal -> {
                        Vec3d away = animal.getPos().subtract(player.getPos());
                        if (away.horizontalLengthSquared() < 0.001) {
                            away = new Vec3d(1.0, 0.0, 0.0);
                        }

                        away = away.normalize();
                        animal.setVelocity(away.x * 0.55, 0.35, away.z * 0.55);
                        animal.velocityModified = true;
                        if (animal instanceof MobEntity) {
                            animal.setTarget(null);
                            Vec3d fleeTarget = animal.getPos().add(away.multiply(8.0));
                            animal.getNavigation().startMovingTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, 1.4);
                        }
                    });
                }

                boolean isSync = false;

                for (ServerPlayerEntity other : player.getServer().getPlayerManager().getPlayerList()) {
                    if (other != player) {
                        Long otherLast = lastPressTime.get(other.getUuid());
                        if (otherLast != null && now - otherLast <= 300L && player.getPos().distanceTo(other.getPos()) <= 6.0) {
                            isSync = true;
                            break;
                        }
                    }
                }

                double yawRad = Math.toRadians(player.getBodyYaw());
                Vec3d armTip = player.getPos().add(-Math.sin(yawRad) * 0.5, 1.2, Math.cos(yawRad) * 0.5);

                int delay = switch (tier) {
                    case 1 -> 3;
                    case 2 -> 2;
                    default -> 5;
                };
                scheduled.add(new ClapHandler.ScheduledEffect(player.getServerWorld(), armTip, isSync, tier, delay, player));
            }
        }
    }

    private static void tick(MinecraftServer server) {
        Iterator<ClapHandler.ScheduledEffect> it = scheduled.iterator();

        while (it.hasNext()) {
            ClapHandler.ScheduledEffect fx = it.next();
            if (--fx.ticksRemaining <= 0) {
                playEffects(fx);
                it.remove();
            }
        }
    }

    private static void playEffects(ClapHandler.ScheduledEffect fx) {
        Vec3d p = fx.armTip;
        SoundEvent sound = ModSounds.CLAP_SOUNDS[RANDOM.nextInt(ModSounds.CLAP_SOUNDS.length)];
        if (fx.syncClap) {
            fx.world.playSound(null, p.x, p.y, p.z, sound, SoundCategory.field_15248, 1.8F, 1.2F);
            fx.world.playSound(null, p.x, p.y, p.z, SoundEvents.field_14840, SoundCategory.field_15248, 0.9F, 1.4F);
            fx.world.spawnParticles(ParticleTypes.field_11248, p.x, p.y, p.z, 18, 0.15, 0.15, 0.15, 0.06);
            fx.world.spawnParticles(ParticleTypes.field_29642, p.x, p.y, p.z, 10, 0.12, 0.12, 0.12, 0.03);
            fx.world.spawnParticles(ParticleTypes.field_11207, p.x, p.y, p.z, 7, 0.12, 0.12, 0.12, 0.04);
            fx.world.spawnParticles(ParticleTypes.field_11208, p.x, p.y, p.z, 8, 0.1, 0.1, 0.1, 0.05);
        } else {
            switch (fx.tier) {
                case 0:
                    fx.world.playSound(null, p.x, p.y, p.z, sound, SoundCategory.field_15248, 1.1F, 1.0F);
                    fx.world.spawnParticles(ParticleTypes.field_11205, p.x, p.y, p.z, 7, 0.12, 0.12, 0.12, 0.04);
                    break;
                case 1:
                    fx.world.playSound(null, p.x, p.y, p.z, sound, SoundCategory.field_15248, 1.3F, 1.15F);
                    fx.world.playSound(null, p.x, p.y, p.z, SoundEvents.field_14706, SoundCategory.field_15248, 0.5F, 1.8F);
                    fx.world.spawnParticles(ParticleTypes.field_11205, p.x, p.y, p.z, 6, 0.12, 0.12, 0.12, 0.05);
                    fx.world.spawnParticles(ParticleTypes.field_11208, p.x, p.y, p.z, 4, 0.1, 0.1, 0.1, 0.04);
                    break;
                case 2:
                    fx.world.playSound(null, p.x, p.y, p.z, sound, SoundCategory.field_15248, 1.5F, 1.35F);
                    fx.world.playSound(null, p.x, p.y, p.z, SoundEvents.field_14840, SoundCategory.field_15248, 0.7F, 1.6F);
                    fx.world.spawnParticles(ParticleTypes.field_11205, p.x, p.y, p.z, 8, 0.15, 0.15, 0.15, 0.07);
                    fx.world.spawnParticles(ParticleTypes.field_11208, p.x, p.y, p.z, 5, 0.12, 0.12, 0.12, 0.06);
                    fx.world.spawnParticles(ParticleTypes.field_11248, p.x, p.y, p.z, 4, 0.1, 0.1, 0.1, 0.04);
            }
        }

        if (fx.player != null && !fx.player.isRemoved()) {
            fx.world.playSound(fx.player, p.x, p.y, p.z, sound, SoundCategory.field_15248, 1.0F, 1.0F);
        }
    }

    public static void registerTodoPayload() {
        PayloadTypeRegistry.playC2S().register(ClapHandler.TodoRightClickPayload.ID, ClapHandler.TodoRightClickPayload.CODEC);
    }

    public static void registerTodoReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(ClapHandler.TodoRightClickPayload.ID, (payload, ctx) -> ctx.server().execute(() -> {
            if (payload.held()) {
                rightClickHeld.add(ctx.player().getUuid());
            } else {
                rightClickHeld.remove(ctx.player().getUuid());
            }
        }));
    }

    private static void performTodoSwap(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        boolean targetMode = rightClickHeld.contains(player.getUuid());
        UUID pid = player.getUuid();
        if (targetMode) {
            Entity aimed = getAimedEntity(player, 50.0);
            if (aimed == null) {
                return;
            }

            Entity stored = firstTarget.get(pid);
            if (stored != null && !stored.isRemoved()) {
                spawnBlueParticles(world, stored.getPos().add(0.0, 1.0, 0.0));
                spawnBlueParticles(world, aimed.getPos().add(0.0, 1.0, 0.0));
                playSounds(world, stored.getPos().add(0.0, 1.0, 0.0));
                swapEntities(stored, aimed);
                firstTarget.remove(pid);
            } else {
                firstTarget.put(pid, aimed);
                world.spawnParticles(
                    ParticleTypes.field_11207,
                    aimed.getPos().x,
                    aimed.getPos().y + 1.0,
                    aimed.getPos().z,
                    5,
                    0.2,
                    0.3,
                    0.2,
                    0.03
                );
                playSounds(world, aimed.getPos().add(0.0, 1.0, 0.0));
            }
        } else {
            firstTarget.remove(pid);
            Entity target = getAimedEntity(player, 50.0);
            if (target == null) {
                return;
            }

            Vec3d fromPos = player.getPos();
            Vec3d toPos = target.getPos();
            spawnBlueParticles(world, fromPos.add(0.0, 1.0, 0.0));
            spawnBlueParticles(world, toPos.add(0.0, 1.0, 0.0));
            playSounds(world, fromPos.add(0.0, 1.0, 0.0));
            if (target instanceof ServerPlayerEntity tp) {
                player.requestTeleport(toPos.x, toPos.y, toPos.z);
                tp.requestTeleport(fromPos.x, fromPos.y, fromPos.z);
            } else {
                player.requestTeleport(toPos.x, toPos.y, toPos.z);
                target.setPosition(fromPos.x, fromPos.y, fromPos.z);
                target.refreshPositionAfterTeleport(fromPos.x, fromPos.y, fromPos.z);
            }
        }
    }

    private static void spawnBlueParticles(ServerWorld world, Vec3d pos) {
        world.spawnParticles(ParticleTypes.field_23114, pos.x, pos.y, pos.z, 1, 0.1, 0.2, 0.1, 0.01);
    }

    private static void playSounds(ServerWorld world, Vec3d pos) {
        world.playSound(null, pos.x, pos.y, pos.z, ModSounds.CLAP_SWAP, SoundCategory.field_15248, 1.2F, 1.0F);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.field_14709, SoundCategory.field_15248, 0.6F, 1.8F);
    }

    private static void swapEntities(Entity a, Entity b) {
        Vec3d posA = a.getPos();
        Vec3d posB = b.getPos();
        if (a instanceof ServerPlayerEntity pa && b instanceof ServerPlayerEntity pb) {
            pa.requestTeleport(posB.x, posB.y, posB.z);
            pb.requestTeleport(posA.x, posA.y, posA.z);
        } else if (a instanceof ServerPlayerEntity pa) {
            pa.requestTeleport(posB.x, posB.y, posB.z);
            b.setPosition(posA.x, posA.y, posA.z);
            b.refreshPositionAfterTeleport(posA.x, posA.y, posA.z);
        } else if (b instanceof ServerPlayerEntity pb) {
            pb.requestTeleport(posA.x, posA.y, posA.z);
            a.setPosition(posB.x, posB.y, posB.z);
            a.refreshPositionAfterTeleport(posB.x, posB.y, posB.z);
        } else {
            a.setPosition(posB.x, posB.y, posB.z);
            a.refreshPositionAfterTeleport(posB.x, posB.y, posB.z);
            b.setPosition(posA.x, posA.y, posA.z);
            b.refreshPositionAfterTeleport(posA.x, posA.y, posA.z);
        }
    }

    private static Entity getAimedEntity(ServerPlayerEntity player, double maxDist) {
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d end = start.add(look.multiply(maxDist));
        Box box = player.getBoundingBox().stretch(look.multiply(maxDist)).expand(1.5);
        Entity best = null;
        double closest = maxDist;

        for (Entity e : player.getServerWorld().getOtherEntities(player, box)) {
            if (!e.isRemoved()) {
                Optional<Vec3d> hit = e.getBoundingBox().expand(0.3).raycast(start, end);
                if (hit.isPresent()) {
                    double d = start.distanceTo(hit.get());
                    if (d < closest) {
                        closest = d;
                        best = e;
                    }
                }
            }
        }

        return best;
    }

    public static void cleanupTodo(UUID id) {
        firstTarget.remove(id);
        rightClickHeld.remove(id);
    }

    public static void cleanup(UUID id) {
        lastPressTime.remove(id);
        cleanupTodo(id);
    }

    public record ClapRequestPayload() implements CustomPayload {
        public static final Identifier ID_LOC = new Identifier("cooptest", "clap_request");
        public static final Id<ClapHandler.ClapRequestPayload> ID = new Id(ID_LOC);
        public static final PacketCodec<PacketByteBuf, ClapHandler.ClapRequestPayload> CODEC = PacketCodec.of(
            (p, buf) -> {}, buf -> new ClapHandler.ClapRequestPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static class ScheduledEffect {
        final ServerWorld world;
        final Vec3d armTip;
        final boolean syncClap;
        final int tier;
        final ServerPlayerEntity player;
        int ticksRemaining;

        ScheduledEffect(ServerWorld world, Vec3d armTip, boolean syncClap, int tier, int delay, ServerPlayerEntity player) {
            this.world = world;
            this.armTip = armTip;
            this.syncClap = syncClap;
            this.tier = tier;
            this.ticksRemaining = delay;
            this.player = player;
        }
    }

    public record TodoRightClickPayload(boolean held) implements CustomPayload {
        public static final Id<ClapHandler.TodoRightClickPayload> ID = new Id(new Identifier("testcoop", "todo_rc"));
        public static final PacketCodec<PacketByteBuf, ClapHandler.TodoRightClickPayload> CODEC = PacketCodec.of(
            (v, buf) -> buf.writeBoolean(v.held()), buf -> new ClapHandler.TodoRightClickPayload(buf.readBoolean())
        );

        public Id<ClapHandler.TodoRightClickPayload> getId() {
            return ID;
        }
    }
}
