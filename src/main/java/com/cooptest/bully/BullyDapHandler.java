package com.cooptest.bully;

import com.cooptest.ChargedDapHandler;
import com.cooptest.ModSounds;
import com.cooptest.PoseNetworking;
import com.cooptest.client.CoopAnimationHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
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
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import net.minecraft.world.World.ExplosionSourceType;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class BullyDapHandler {
    public static final float PRIMED_MIN_HEIGHT = 5.0F;
    public static final float PRIMED_MAX_HEIGHT = 20.0F;
    public static final int HEIGHT_SCAN_BLOCKS = 26;
    public static final float PRIMED_BOOST = 0.5F;
    public static final long RELEASE_WINDOW_MS = 500L;
    public static final double TRIGGER_RANGE = 8.0;
    public static final double LOOK_DOT_THRESHOLD = 0.5;
    public static final int FALL_TICKS = 6;
    private static final int HAND_SWING_EVERY = 2;
    private static final long T_HIT_P1 = 542L;
    private static final long T_PARTICLES = 792L;
    private static final long T_CLEANUP = 3925L;
    private static final Set<UUID> primedVisual = new HashSet<>();
    private static final Map<UUID, BullyDapHandler.PendingRelease> pending = new HashMap<>();
    private static final Map<UUID, BullyDapHandler.FallSession> fallSessions = new HashMap<>();
    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final Set<UUID> inBullyDap = new HashSet<>();
    private static final Map<UUID, BullyDapHandler.ActiveDap> activeDaps = new HashMap<>();

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(BullyDapHandler.BullyDapEffectsPayload.ID, BullyDapHandler.BullyDapEffectsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BullyDapHandler.BullySimpleFlashPayload.ID, BullyDapHandler.BullySimpleFlashPayload.CODEC);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {});
    }

    private static void tickPendingExpiry(MinecraftServer server) {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> {
            if (now - e.getValue().timestamp > 500L) {
                return true;
            } else {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                return p == null || p.isOnGround();
            }
        });
    }

    private static void tickPrimedVisual(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID id = player.getUuid();
            if (!inBullyDap.contains(id) && !isOnCooldown(id) && !fallSessions.containsKey(id)) {
                boolean onGround = player.isOnGround();
                boolean charging = ChargedDapHandler.chargeStartTime.containsKey(id);
                if (onGround) {
                    if (primedVisual.remove(id)) {
                        pending.remove(id);
                        ChargedDapHandler.executeWhiff(player);
                        ChargedDapHandler.chargeStartTime.remove(id);
                        ChargedDapHandler.fireLevel.remove(id);
                        ChargedDapHandler.broadcastChargeCancel(player);
                    }
                } else if (!charging) {
                    if (primedVisual.remove(id)) {
                        PoseNetworking.broadcastAnimState(player, 0);
                    }
                } else if (!primedVisual.contains(id)) {
                    double height = getHeightAboveGround(player);
                    if (!(height < 5.0)) {
                        primedVisual.add(id);
                        Vec3d fwd = player.getRotationVec(1.0F);
                        Vec3d vel = player.getVelocity();
                        player.setVelocity(vel.x + fwd.x * 0.5, vel.y, vel.z + fwd.z * 0.5);
                        player.velocityModified = true;
                        PoseNetworking.broadcastAnimState(player, CoopAnimationHandler.AnimState.BULLY_DAP_P1.ordinal());
                        new Thread(() -> {
                            try {
                                Thread.sleep(542L);
                            } catch (InterruptedException var3x) {
                            }

                            player.getServer().execute(() -> {
                                if (primedVisual.contains(id) && !inBullyDap.contains(id) && !fallSessions.containsKey(id)) {
                                    PoseNetworking.broadcastAnimState(player, CoopAnimationHandler.AnimState.BULLY_DAP_IDLE.ordinal());
                                }
                            });
                        }).start();
                    }
                }
            }
        }
    }

    public static double getHeightAboveGround(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        double py = player.getY();
        int px = (int)Math.floor(player.getX());
        int pz = (int)Math.floor(player.getZ());
        int startY = (int)Math.floor(py) - 1;

        for (int dy = 0; dy < 26; dy++) {
            BlockPos pos = new BlockPos(px, startY - dy, pz);
            BlockState state = world.getBlockState(pos);
            if (!state.isAir() && !state.getCollisionShape(world, pos).isEmpty()) {
                return py - (pos.getY() + 1.0);
            }
        }

        return 26.0;
    }

    public static float calcNormalized(double height) {
        return (float)Math.min(Math.max((height - 5.0) / 15.0, 0.0), 1.0);
    }

    public static boolean tryBullyDap(ServerPlayerEntity player, double height) {
        long now = System.currentTimeMillis();
        UUID id = player.getUuid();
        player.sendMessage(Text.literal("§7[Bully] tryBullyDap — pending size=" + pending.size()), true);
        Iterator<Entry<UUID, BullyDapHandler.PendingRelease>> it = pending.entrySet().iterator();

        while (it.hasNext()) {
            Entry<UUID, BullyDapHandler.PendingRelease> e = it.next();
            UUID otherId = e.getKey();
            if (!otherId.equals(id)) {
                BullyDapHandler.PendingRelease pr = e.getValue();
                long age = now - pr.timestamp;
                if (age > 500L) {
                    player.sendMessage(Text.literal("§7[Bully] candidate expired, age=" + age + "ms"), true);
                    it.remove();
                } else {
                    ServerPlayerEntity other = player.getServer().getPlayerManager().getPlayer(otherId);
                    if (other == null) {
                        it.remove();
                    } else {
                        double distSq = player.squaredDistanceTo(other);
                        boolean inRange = distSq <= 64.0;
                        boolean facing = areFacingEachOtherHorizontal(player, other);
                        player.sendMessage(
                            Text.literal(
                                "§7[Bully] candidate dist="
                                    + String.format("%.1f", Math.sqrt(distSq))
                                    + " inRange="
                                    + inRange
                                    + " facing="
                                    + facing
                                    + " age="
                                    + age
                                    + "ms"
                            ),
                            true
                        );
                        if (inRange && facing) {
                            it.remove();
                            float h1 = (float)height;
                            float h2 = (float)pr.height;
                            float normalized = calcNormalized(Math.max(h1, h2));
                            startBullyDap(player, other, normalized);
                            return true;
                        }
                    }
                }
            }
        }

        pending.put(id, new BullyDapHandler.PendingRelease(now, height));
        return false;
    }

    private static boolean areFacingEachOtherHorizontal(ServerPlayerEntity a, ServerPlayerEntity b) {
        Vec3d posA = a.getPos();
        Vec3d posB = b.getPos();
        Vec3d lookA = a.getRotationVec(1.0F);
        Vec3d lookB = b.getRotationVec(1.0F);
        Vec3d lookAFlat = new Vec3d(lookA.x, 0.0, lookA.z).normalize();
        Vec3d lookBFlat = new Vec3d(lookB.x, 0.0, lookB.z).normalize();
        Vec3d toBFlat = new Vec3d(posB.x - posA.x, 0.0, posB.z - posA.z);
        Vec3d toAFlat = new Vec3d(posA.x - posB.x, 0.0, posA.z - posB.z);
        if (toBFlat.lengthSquared() < 1.0E-4) {
            return true;
        } else {
            toBFlat = toBFlat.normalize();
            toAFlat = toAFlat.normalize();
            double dotA = lookAFlat.dotProduct(toBFlat);
            double dotB = lookBFlat.dotProduct(toAFlat);
            return dotA >= 0.5 && dotB >= 0.5;
        }
    }

    private static void startBullyDap(ServerPlayerEntity p1, ServerPlayerEntity p2, float normalized) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        if (!inBullyDap.contains(id1) && !inBullyDap.contains(id2)) {
            if (!fallSessions.containsKey(id1) && !fallSessions.containsKey(id2)) {
                long now = System.currentTimeMillis();
                inBullyDap.add(id1);
                inBullyDap.add(id2);
                primedVisual.remove(id1);
                primedVisual.remove(id2);
                cooldowns.put(id1, now + 3000L);
                cooldowns.put(id2, now + 3000L);
                ChargedDapHandler.cooldowns.put(id1, now + 3000L);
                ChargedDapHandler.cooldowns.put(id2, now + 3000L);
                String msg = normalized < 0.33F
                    ? "§e⚡ Bully Dap!"
                    : (normalized < 0.67F ? "§6§l⚡⚡ Hard Bully Dap!" : (normalized < 1.0F ? "§c§l⚡⚡⚡ Heavy Bully Dap!" : "§4§l\ud83d\udca5 MAX BULLY DAP!"));
                p1.sendMessage(Text.literal(msg), true);
                p2.sendMessage(Text.literal(msg), true);
                Vec3d pos1 = p1.getPos();
                Vec3d pos2 = p2.getPos();
                double dx = pos1.x - pos2.x;
                double dz = pos1.z - pos2.z;
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len < 0.001) {
                    dx = 0.0;
                    dz = 1.0;
                    len = 1.0;
                }

                double ndx = dx / len;
                double ndz = dz / len;
                Vec3d targetPos = new Vec3d(pos2.x + ndx * 1.5, pos2.y, pos2.z + ndz * 1.5);
                float targetYaw = (float)(-Math.toDegrees(Math.atan2(-ndx, -ndz)));
                float p2Yaw = targetYaw + 180.0F;
                BullyDapHandler.FallSession fall = new BullyDapHandler.FallSession(id1, id2, pos1, targetPos, pos2, p1.getYaw(), targetYaw, normalized);
                fallSessions.put(id1, fall);
                p2.setYaw(p2Yaw);
                p2.setBodyYaw(p2Yaw);
                p2.setHeadYaw(p2Yaw);
                p2.setVelocity(Vec3d.ZERO);
                p2.velocityModified = true;
                p2.fallDistance = 0.0F;
                showBothHands(p1, p2);
            }
        }
    }

    private static void tickFallSessions(MinecraftServer server) {
        for (BullyDapHandler.FallSession f : new ArrayList<>(fallSessions.values())) {
            ServerPlayerEntity p1 = server.getPlayerManager().getPlayer(f.p1id);
            ServerPlayerEntity p2 = server.getPlayerManager().getPlayer(f.p2id);
            if (p1 != null && p2 != null) {
                ServerWorld world = p1.getServerWorld();
                f.tick++;
                double t = Math.min(1.0, f.tick / 6.0);
                double eased = 1.0 - Math.pow(1.0 - t, 2.0);
                Vec3d newPos = new Vec3d(
                    lerp(f.startPos.x, f.targetPos.x, eased),
                    lerp(f.startPos.y, f.targetPos.y, eased),
                    lerp(f.startPos.z, f.targetPos.z, eased)
                );
                float newYaw = (float)lerpAngle(f.startYaw, f.targetYaw, eased);
                p1.teleport(world, newPos.x, newPos.y, newPos.z, newYaw, 0.0F);
                p1.setYaw(newYaw);
                p1.setBodyYaw(newYaw);
                p1.setHeadYaw(newYaw);
                p1.setVelocity(Vec3d.ZERO);
                p1.velocityModified = true;
                p1.fallDistance = 0.0F;
                if (f.tick % 2 == 0) {
                    p1.swingHand(Hand.MAIN_HAND);
                }

                p2.teleport(world, f.p2FrozenPos.x, f.p2FrozenPos.y, f.p2FrozenPos.z, p2.getYaw(), 0.0F);
                p2.setVelocity(Vec3d.ZERO);
                p2.velocityModified = true;
                p2.fallDistance = 0.0F;
                if (f.tick >= 6) {
                    fallSessions.remove(f.p1id);
                    finishFall(p1, p2, f.normalized);
                }
            } else {
                fallSessions.remove(f.p1id);
                inBullyDap.remove(f.p1id);
                inBullyDap.remove(f.p2id);
            }
        }
    }

    private static void finishFall(ServerPlayerEntity p1, ServerPlayerEntity p2, float normalized) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        PoseNetworking.broadcastAnimState(p1, CoopAnimationHandler.AnimState.BULLY_DAP_P1.ordinal());
        PoseNetworking.broadcastAnimState(p2, CoopAnimationHandler.AnimState.BULLY_DAP_HIT_P2.ordinal());
        BullyDapHandler.ActiveDap dap = new BullyDapHandler.ActiveDap(id1, id2, normalized);
        dap.ready = true;
        dap.startMs = System.currentTimeMillis();
        activeDaps.put(id1, dap);
        activeDaps.put(id2, dap);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double lerpAngle(double a, double b, double t) {
        double diff = (b - a + 540.0) % 360.0 - 180.0;
        return a + diff * t;
    }

    private static void tickActiveDaps(MinecraftServer server) {
        for (BullyDapHandler.ActiveDap s : new ArrayList<>(activeDaps.values())) {
            if (activeDaps.containsKey(s.p1id) && s.ready) {
                long elapsed = s.elapsed();
                ServerPlayerEntity p1 = server.getPlayerManager().getPlayer(s.p1id);
                ServerPlayerEntity p2 = server.getPlayerManager().getPlayer(s.p2id);
                if (p1 != null && p2 != null) {
                    ServerWorld world = p1.getServerWorld();
                    if (!s.hitP1Done && elapsed >= 542L) {
                        s.hitP1Done = true;
                        PoseNetworking.broadcastAnimState(p1, CoopAnimationHandler.AnimState.BULLY_DAP_HIT_P1.ordinal());
                        Vec3d cp = p1.getPos().add(p2.getPos()).multiply(0.5);
                        fireConnect(p1, p2, s.normalized, cp, world);
                    }

                    if (!s.particlesDone && elapsed >= 792L) {
                        s.particlesDone = true;
                        spawnHandMeetParticles(world, p1.getPos().add(p2.getPos()).multiply(0.5), s.normalized);
                    }

                    if (!s.cleanupDone && elapsed >= 3925L) {
                        s.cleanupDone = true;
                        PoseNetworking.broadcastAnimState(p1, 0);
                        PoseNetworking.broadcastAnimState(p2, 0);
                        hideHands(p1, p2);
                        inBullyDap.remove(s.p1id);
                        inBullyDap.remove(s.p2id);
                        removeActiveDap(s);
                    }
                } else {
                    removeActiveDap(s);
                }
            }
        }
    }

    private static void removeActiveDap(BullyDapHandler.ActiveDap s) {
        activeDaps.remove(s.p1id);
        activeDaps.remove(s.p2id);
    }

    private static void fireConnect(ServerPlayerEntity p1, ServerPlayerEntity p2, float n, Vec3d pos, ServerWorld world) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();

        for (ServerPlayerEntity pl : p1.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(pl, new BullyDapHandler.BullyDapEffectsPayload(n, pos.x, pos.y + 1.3, pos.z));
        }

        int base = 10 + (int)(n * 20.0F);
        world.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y + 1.3, pos.z, base, 0.3, 0.3, 0.3, 0.12);
        world.spawnParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y + 1.3, pos.z, base / 2, 0.3, 0.3, 0.3, 0.08);
        if (n >= 0.33F) {
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 1.0, pos.z, (int)(n * 8.0F), 0.4, 0.2, 0.4, 0.0);
        }

        if (n >= 0.67F) {
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 1 + (int)(n * 2.0F), 0.1, 0.1, 0.1, 0.0);
            world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.3, pos.z, (int)(n * 15.0F), 0.5, 0.4, 0.5, 0.12);
        }

        if (n >= 1.0F) {
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 0.5, pos.z, 1, 0.3, 0.3, 0.3, 0.0);
        }

        float vol = 1.0F + n * 0.8F;
        world.playSound(null, pos.x, pos.y, pos.z, ModSounds.DAP_HIT, SoundCategory.PLAYERS, vol, 1.0F);
        if (n >= 0.33F) {
            world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, vol, 0.9F);
        }

        if (n >= 0.67F) {
            world.playSound(null, pos.x, pos.y, pos.z, ModSounds.IMPACT, SoundCategory.PLAYERS, vol * 1.2F, 1.0F);
        }

        if (n >= 0.33F) {
            world.createExplosion(null, pos.x, pos.y, pos.z, 2.0F + n * 4.0F, false, ExplosionSourceType.NONE);
        }

        double kbR = 4.0 + n * 8.0;
        double kbS = 0.4 + n * 1.2;
        Box box = new Box(
            pos.x - kbR, pos.y - kbR, pos.z - kbR, pos.x + kbR, pos.y + kbR, pos.z + kbR
        );

        for (Entity e : world.getOtherEntities(null, box)) {
            if (!e.getUuid().equals(id1) && !e.getUuid().equals(id2)) {
                double d = e.getPos().distanceTo(pos);
                if (!(d < 0.5) && !(d > kbR)) {
                    Vec3d dr = e.getPos().subtract(pos).normalize();
                    double s = (1.0 - d / kbR) * kbS;
                    e.addVelocity(dr.x * s, s * 0.4, dr.z * s);
                    e.velocityModified = true;
                }
            }
        }
    }

    private static void spawnHandMeetParticles(ServerWorld world, Vec3d pos, float n) {
        Vec3d h = pos.add(0.0, 1.3, 0.0);
        int tier = n < 0.33F ? 1 : (n < 0.67F ? 2 : (n < 1.0F ? 3 : 4));
        switch (tier) {
            case 1:
                world.spawnParticles(ParticleTypes.CRIT, h.x, h.y, h.z, 12, 0.2, 0.2, 0.2, 0.1);
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT, h.x, h.y, h.z, 8, 0.2, 0.2, 0.2, 0.08);
                break;
            case 2:
                world.spawnParticles(ParticleTypes.CRIT, h.x, h.y, h.z, 20, 0.3, 0.2, 0.3, 0.12);
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT, h.x, h.y, h.z, 12, 0.2, 0.2, 0.2, 0.1);
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, h.x, h.y, h.z, 4, 0.3, 0.1, 0.3, 0.0);
                break;
            case 3:
                world.spawnParticles(ParticleTypes.CRIT, h.x, h.y, h.z, 30, 0.3, 0.3, 0.3, 0.15);
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT, h.x, h.y, h.z, 18, 0.3, 0.3, 0.3, 0.12);
                world.spawnParticles(ParticleTypes.END_ROD, h.x, h.y, h.z, 10, 0.4, 0.3, 0.4, 0.1);
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, h.x, h.y, h.z, 6, 0.4, 0.1, 0.4, 0.0);
                world.playSound(null, h.x, h.y, h.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.2F, 1.1F);
                break;
            case 4:
                world.spawnParticles(ParticleTypes.CRIT, h.x, h.y, h.z, 45, 0.4, 0.3, 0.4, 0.18);
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT, h.x, h.y, h.z, 25, 0.3, 0.3, 0.3, 0.14);
                world.spawnParticles(ParticleTypes.END_ROD, h.x, h.y, h.z, 18, 0.5, 0.4, 0.5, 0.12);
                world.spawnParticles(ParticleTypes.FLASH, h.x, h.y, h.z, 3, 0.1, 0.1, 0.1, 0.0);
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, h.x, h.y, h.z, 10, 0.5, 0.1, 0.5, 0.0);
                world.playSound(null, h.x, h.y, h.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.5F, 1.0F);
                world.playSound(null, h.x, h.y, h.z, ModSounds.IMPACT, SoundCategory.PLAYERS, 1.5F, 1.2F);
        }
    }

    private static void showBothHands(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        for (ServerPlayerEntity pl : p1.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(pl, new ChargedDapHandler.FireDapFirstPersonPayload(p1.getUuid(), true));
            ServerPlayNetworking.send(pl, new ChargedDapHandler.FireDapFirstPersonPayload(p2.getUuid(), true));
        }
    }

    private static void hideHands(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        for (ServerPlayerEntity pl : p1.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(pl, new ChargedDapHandler.FireDapFirstPersonPayload(p1.getUuid(), false));
            ServerPlayNetworking.send(pl, new ChargedDapHandler.FireDapFirstPersonPayload(p2.getUuid(), false));
        }
    }

    private static boolean isOnCooldown(UUID id) {
        Long e = cooldowns.get(id);
        if (e == null) {
            return false;
        } else if (System.currentTimeMillis() >= e) {
            cooldowns.remove(id);
            return false;
        } else {
            return true;
        }
    }

    public static boolean isInBullyDap(UUID id) {
        return inBullyDap.contains(id) || fallSessions.containsKey(id);
    }

    public static boolean wasPrimed(UUID id) {
        return primedVisual.contains(id);
    }

    public static boolean isPrimed(UUID id) {
        return false;
    }

    public static void cleanup(UUID id) {
        primedVisual.remove(id);
        inBullyDap.remove(id);
        cooldowns.remove(id);
        pending.remove(id);
        BullyDapHandler.FallSession f = fallSessions.remove(id);
        if (f != null) {
            inBullyDap.remove(f.p1id);
            inBullyDap.remove(f.p2id);
        }

        BullyDapHandler.ActiveDap s = activeDaps.remove(id);
        if (s != null) {
            activeDaps.remove(s.p1id);
            activeDaps.remove(s.p2id);
        }
    }

    private static class ActiveDap {
        final UUID p1id;
        final UUID p2id;
        final float normalized;
        boolean ready = false;
        long startMs = 0L;
        boolean hitP1Done = false;
        boolean particlesDone = false;
        boolean cleanupDone = false;

        ActiveDap(UUID a, UUID b, float n) {
            this.p1id = a;
            this.p2id = b;
            this.normalized = n;
        }

        long elapsed() {
            return System.currentTimeMillis() - this.startMs;
        }
    }

    public record BullyDapEffectsPayload(float normalized, double x, double y, double z) implements CustomPayload {
        public static final Id<BullyDapHandler.BullyDapEffectsPayload> ID = new Id(new Identifier("cooptest", "bully_dap_effects"));
        public static final PacketCodec<PacketByteBuf, BullyDapHandler.BullyDapEffectsPayload> CODEC = PacketCodec.of((p, buf) -> {
            buf.writeFloat(p.normalized());
            buf.writeDouble(p.x());
            buf.writeDouble(p.y());
            buf.writeDouble(p.z());
        }, buf -> new BullyDapHandler.BullyDapEffectsPayload(buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BullySimpleFlashPayload() implements CustomPayload {
        public static final Id<BullyDapHandler.BullySimpleFlashPayload> ID = new Id(new Identifier("cooptest", "bully_simple_flash"));
        public static final PacketCodec<PacketByteBuf, BullyDapHandler.BullySimpleFlashPayload> CODEC = PacketCodec.of(
            (p, buf) -> {}, buf -> new BullyDapHandler.BullySimpleFlashPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static class FallSession {
        final UUID p1id;
        final UUID p2id;
        final Vec3d startPos;
        final Vec3d targetPos;
        final Vec3d p2FrozenPos;
        final float startYaw;
        final float targetYaw;
        final float normalized;
        int tick = 0;

        FallSession(UUID p1, UUID p2, Vec3d startPos, Vec3d targetPos, Vec3d p2FrozenPos, float startYaw, float targetYaw, float normalized) {
            this.p1id = p1;
            this.p2id = p2;
            this.startPos = startPos;
            this.targetPos = targetPos;
            this.p2FrozenPos = p2FrozenPos;
            this.startYaw = startYaw;
            this.targetYaw = targetYaw;
            this.normalized = normalized;
        }
    }

    private static class PendingRelease {
        final long timestamp;
        final double height;

        PendingRelease(long t, double h) {
            this.timestamp = t;
            this.height = h;
        }
    }
}
