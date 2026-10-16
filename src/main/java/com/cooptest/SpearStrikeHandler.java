package com.cooptest;

import com.cooptest.bros.BrosHandler;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.command.v2.CommandRegistrationCallback;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarting;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.math.Box;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.registry.Registries;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import net.minecraft.world.World.ExplosionSourceType;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public final class SpearStrikeHandler {
    private static final Map<UUID, SpearStrikeHandler.State> states = new HashMap<>();
    private static final List<SpearStrikeHandler.NukeStage> nukeStages = new ArrayList<>();
    private static int serverTick;
    private static boolean missileOn = false;

    private SpearStrikeHandler() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(SpearStrikeHandler.SpearHoldPayload.ID, SpearStrikeHandler.SpearHoldPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpearStrikeHandler.SpearLockPayload.ID, SpearStrikeHandler.SpearLockPayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SpearStrikeHandler.SpearHoldPayload.ID, (p, ctx) -> {
            SpearStrikeHandler.State s = states.computeIfAbsent(ctx.player().getUuid(), k -> new SpearStrikeHandler.State());
            s.holding = p.holding();
            if (!s.holding) {
                s.lockedId = -1;
                s.missileSpent = false;
            }
        });
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            serverTick++;

            try {
                tickNukes();
            } catch (Throwable var3) {
                System.err.println("[Spear] nuke failed: " + var3);
                nukeStages.clear();
            }

            try {
                tick(server);
            } catch (Throwable var2) {
                System.err.println("[Spear] tick failed: " + var2);
                states.clear();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> {
            states.clear();
            nukeStages.clear();
        });
        ServerLifecycleEvents.SERVER_STARTING.register((ServerStarting)server -> missileOn = CoopMovesConfig.get().spearMissileMode);
        CommandRegistrationCallback.EVENT
            .register(
                (CommandRegistrationCallback)(dispatcher, reg, env) -> dispatcher.register(
                    (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("spearmissile")
                                    .requires(src -> src.hasPermissionLevel(2)))
                                .executes(ctx -> {
                                    ((ServerCommandSource)ctx.getSource())
                                        .sendFeedback(() -> Text.literal("§6Spear missile mode is " + (missileOn ? "§aON" : "§cOFF")), false);
                                    return 1;
                                }))
                            .then(CommandManager.literal("on").executes(ctx -> setMissile((ServerCommandSource)ctx.getSource(), true))))
                        .then(CommandManager.literal("off").executes(ctx -> setMissile((ServerCommandSource)ctx.getSource(), false)))
                )
            );
        CommandRegistrationCallback.EVENT
            .register(
                (CommandRegistrationCallback)(dispatcher, reg, env) -> dispatcher.register(
                    (LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("testspear")
                            .executes(ctx -> testLaunch(((ServerCommandSource)ctx.getSource()).getPlayer(), false)))
                        .then(CommandManager.literal("fire").executes(ctx -> testLaunch(((ServerCommandSource)ctx.getSource()).getPlayer(), true)))
                )
            );
    }

    private static int setMissile(ServerCommandSource src, boolean on) {
        missileOn = on;
        src.getServer()
            .getPlayerManager()
            .broadcast(
                Text.literal(
                    on ? "§c§l☢ SPEAR MISSILES ARMED §7— jump, hold right-click with a spear, aim at anything" : "§7Spear missiles disarmed"
                ),
                false
            );
        return 1;
    }

    public static void cleanup(UUID id) {
        states.remove(id);
    }

    public static boolean isFlying(ServerPlayerEntity p) {
        SpearStrikeHandler.State s = states.get(p.getUuid());
        return s != null && s.holding && !p.isOnGround() && !p.hasVehicle();
    }

    public static boolean isSpearUnused(ItemStack stack) {
        return stack != null && !stack.isEmpty() ? Registries.ITEM.getId(stack.getItem()).getPath().endsWith("spear") : false;
    }

    private static int testLaunch(ServerPlayerEntity p, boolean fire) {
        if (p == null) {
            return 0;
        } else {
            SpearStrikeHandler.State s = states.computeIfAbsent(p.getUuid(), k -> new SpearStrikeHandler.State());
            s.testThrown = true;
            s.testTicks = 0;
            s.hitThisThrow.clear();
            s.chain = 0;
            if (fire) {
                p.setOnFireFor(8.0F);
            }

            Vec3d look = p.getRotationVector();
            setClientVelocity(p, new Vec3d(look.x * 1.4, Math.max(0.6, look.y * 1.4 + 0.5), look.z * 1.4));
            p.sendMessage(
                Text.literal(fire ? "§c☢ Test launch (on fire) — hold right-click with a spear" : "§eTest launch — hold right-click with a spear"),
                true
            );
            return 1;
        }
    }

    private static void tick(MinecraftServer server) {
        if (!states.isEmpty()) {
            CoopMovesConfig c = CoopMovesConfig.get();
            Iterator<Entry<UUID, SpearStrikeHandler.State>> it = states.entrySet().iterator();

            while (it.hasNext()) {
                Entry<UUID, SpearStrikeHandler.State> e = it.next();
                SpearStrikeHandler.State s = e.getValue();
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p != null && p.isAlive()) {
                    Vec3d pos = new Vec3d(p.getX(), p.getY(), p.getZ());
                    s.motion = s.lastPos == null ? Vec3d.ZERO : pos.subtract(s.lastPos);
                    s.lastPos = pos;
                    if (s.testThrown && ++s.testTicks > 4 && p.isOnGround()) {
                        s.testThrown = false;
                    }

                    boolean airborne = !p.hasVehicle() && !p.isOnGround();
                    boolean missileReady = p.getMainHandStack().isEmpty()
                        && PoseNetworking.poseStates.getOrDefault(p.getUuid(), PoseState.NONE) == PoseState.NONE
                        && !BrosHandler.isEngaged(p.getUuid());
                    if (missileOn && c.enableSpearStrike && s.holding && airborne && missileReady && !s.missileSpent) {
                        s.ticks++;
                        missile(p, s, c);
                    } else {
                        if (!airborne) {
                            s.missileSpent = false;
                        }

                        if (!isThrown(p, s)) {
                            s.hitThisThrow.clear();
                            s.lockedId = -1;
                            sendLock(p, s, -2, 0.0F, 0.0F, false);
                            s.chain = 0;
                            if (!s.holding && !s.testThrown) {
                                it.remove();
                            }
                        } else if (BrosHandler.isEngaged(e.getKey())) {
                            s.lockedId = -1;
                            sendLock(p, s, -2, 0.0F, 0.0F, false);
                        } else if (c.enableSpearStrike && s.holding && p.getMainHandStack().isEmpty()) {
                            s.ticks++;
                            strike(p, s, c);
                        } else {
                            s.lockedId = -1;
                            sendLock(p, s, -2, 0.0F, 0.0F, false);
                        }
                    }
                } else {
                    it.remove();
                }
            }
        }
    }

    private static boolean isThrown(ServerPlayerEntity p, SpearStrikeHandler.State s) {
        if (p.hasVehicle() || p.isOnGround()) {
            return false;
        } else {
            return s.testThrown ? true : PoseNetworking.poseStates.getOrDefault(p.getUuid(), PoseState.NONE) == PoseState.GRABBED;
        }
    }

    private static void strike(ServerPlayerEntity p, SpearStrikeHandler.State s, CoopMovesConfig c) {
        if (p.getWorld() instanceof ServerWorld w) {
            boolean nukeArmed = c.enableSpearNuke && p.isOnFire();
            LivingEntity target = pickTarget(w, p, s, c);
            if (target == null) {
                s.lockedId = -1;
            } else {
                if (s.lockedId != target.getId()) {
                    s.lockedId = target.getId();
                    w.playSound(
                        null,
                        p.getX(),
                        p.getY(),
                        p.getZ(),
                        (SoundEvent)SoundEvents.field_14622.comp_349(),
                        SoundCategory.field_15248,
                        0.5F,
                        nukeArmed ? 0.6F : 1.8F
                    );
                    if (nukeArmed) {
                        p.sendMessage(Text.literal("§4§l☢ NUKE ARMED"), true);
                    }
                }

                Vec3d from = p.getBoundingBox().getCenter();
                Vec3d center = target.getBoundingBox().getCenter();
                Vec3d toTarget = center.subtract(from);
                double d = toTarget.length();
                Vec3d dir = toTarget.multiply(1.0 / Math.max(d, 0.001));
                if (d <= c.spearHitRadius) {
                    if (nukeArmed) {
                        nuke(w, p, center, c);
                    } else {
                        double speed = s.motion.length();
                        float dmg = (float)Math.min((double)c.spearMaxDamage, c.spearBaseDamage + speed * c.spearSpeedDamage);
                        target.damage(w.getDamageSources().playerAttack(p), dmg);
                        target.takeKnockback(c.spearKnockback, -dir.x, -dir.z);
                        target.addVelocity(0.0, 0.3, 0.0);
                        if (target instanceof ServerPlayerEntity tp) {
                            tp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(tp));
                        }

                        s.hitThisThrow.add(target.getId());
                        s.lockedId = -1;
                        s.chain++;
                        sendLock(p, s, -2, 0.0F, 0.0F, false);
                        setClientVelocity(p, s.motion.multiply(0.45).add(0.0, c.spearBounce, 0.0));
                        w.spawnParticles(ParticleTypes.field_11227, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
                        w.spawnParticles(ParticleTypes.field_11205, center.x, center.y, center.z, 14, 0.3, 0.4, 0.3, 0.4);
                        w.playSound(
                            null,
                            center.x,
                            center.y,
                            center.z,
                            SoundEvents.field_15016,
                            SoundCategory.field_15248,
                            1.1F,
                            0.8F + s.chain * 0.1F
                        );
                        w.playSound(
                            null, center.x, center.y, center.z, SoundEvents.field_14840, SoundCategory.field_15248, 1.0F, 0.7F
                        );
                        p.sendMessage(
                            Text.literal(String.format("§6§lSPEAR STRIKE §7%.1f❤%s", dmg / 2.0F, s.chain > 1 ? "  §e§lx" + s.chain : "")), true
                        );
                    }
                } else {
                    sendLock(p, s, target.getId(), (float)c.spearHomingMinSpeed, (float)c.spearHomingTurn, false);
                    if (s.ticks % 2 == 0) {
                        Vec3d mid = from.add(toTarget.multiply(0.5));
                        DefaultParticleType fx = nukeArmed ? ParticleTypes.field_11240 : ParticleTypes.field_29644;
                        w.spawnParticles(fx, mid.x, mid.y, mid.z, 1, 0.0, 0.0, 0.0, 0.0);
                        w.spawnParticles(fx, center.x, center.y + 0.6, center.z, 1, 0.1, 0.1, 0.1, 0.0);
                    }
                }
            }
        }
    }

    private static void missile(ServerPlayerEntity p, SpearStrikeHandler.State s, CoopMovesConfig c) {
        if (p.getWorld() instanceof ServerWorld w) {
            boolean nukeArmed = c.enableSpearNuke && p.isOnFire();
            p.onLanding();
            LivingEntity target = null;
            if (s.lockedId >= 0 && w.getEntityById(s.lockedId) instanceof LivingEntity t && t.isAlive()) {
                target = t;
            } else {
                s.lockedId = -1;
                if (s.ticks % 3 == 0) {
                    target = acquire(w, p, c);
                }

                if (target != null) {
                    s.lockedId = target.getId();
                    w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.field_14702, SoundCategory.field_15248, 1.5F, 0.6F);
                    w.playSound(
                        null,
                        p.getX(),
                        p.getY(),
                        p.getZ(),
                        (SoundEvent)SoundEvents.field_14622.comp_349(),
                        SoundCategory.field_15248,
                        0.8F,
                        0.5F
                    );
                }
            }

            Vec3d from = p.getBoundingBox().getCenter();
            if (target != null) {
                if (target.getBoundingBox().expand(c.spearMissileHitRadius).intersects(p.getBoundingBox())) {
                    Vec3d at = target.getBoundingBox().getCenter();
                    if (nukeArmed) {
                        nuke(w, p, at, c);
                        return;
                    }

                    hurt(w, target, w.getDamageSources().playerAttack(p), c.spearMissileDamage);
                    Vec3d kb = at.subtract(from).normalize();
                    target.takeKnockback(c.spearKnockback * 2.0, -kb.x, -kb.z);
                    w.createExplosion(p, at.x, at.y, at.z, 2.0F, false, ExplosionSourceType.field_40888);
                    s.lockedId = -1;
                    s.missileSpent = true;
                    sendLock(p, s, -2, 0.0F, 0.0F, true);
                    setClientVelocity(p, new Vec3d(0.0, c.spearBounce, 0.0));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.field_5906, 80, 0, false, false));
                    p.sendMessage(Text.literal("§c§l\ud83d\udca5 DIRECT HIT §7" + target.getName().getString()), true);
                    return;
                }

                Vec3d wantDir = target.getBoundingBox().getCenter().subtract(from).normalize();
                if (s.ticks % 5 == 0) {
                    double dist = Math.sqrt(target.squaredDistanceTo(p));
                    p.sendMessage(Text.literal((nukeArmed ? "§4§l☢ " : "§c➤ ") + target.getName().getString() + " §7" + (int)dist + "m"), true);
                }
            } else {
                Vec3d wantDir = p.getRotationVector().normalize();
                if (s.ticks % 10 == 0) {
                    p.sendMessage(Text.literal("§7➤ searching… aim at something"), true);
                }
            }

            sendLock(p, s, target != null ? target.getId() : -1, (float)c.spearMissileSpeed, (float)c.spearMissileTurn, true);
            w.spawnParticles(
                nukeArmed ? ParticleTypes.field_11240 : ParticleTypes.field_11248, p.getX(), p.getY() + 0.9, p.getZ(), 2, 0.1, 0.1, 0.1, 0.01
            );
            if (s.ticks % 2 == 0) {
                w.spawnParticles(ParticleTypes.field_17430, p.getX(), p.getY() + 0.9, p.getZ(), 1, 0.05, 0.05, 0.05, 0.005);
            }

            if (s.ticks % 12 == 0) {
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.field_14702, SoundCategory.field_15248, 0.7F, 0.8F);
            }
        }
    }

    private static LivingEntity acquire(ServerWorld w, ServerPlayerEntity p, CoopMovesConfig c) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVector().normalize();
        double range = c.spearMissileRange;
        double cosCone = Math.cos(Math.toRadians(c.spearMissileConeDeg));
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (LivingEntity t : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(range), x -> x.isAlive() && x != p)) {
            if (!(t instanceof ArmorStandEntity) && !(t instanceof ServerPlayerEntity sp && sp.isSpectator())) {
                Vec3d to = t.getBoundingBox().getCenter().subtract(eye);
                double dist = to.length();
                if (!(dist > range) && !(dist < 0.001)) {
                    double cos = to.multiply(1.0 / dist).dotProduct(look);
                    if (!(cos < cosCone)) {
                        double score = (1.0 - cos) * 1000.0 + dist;
                        if (score < bestScore) {
                            bestScore = score;
                            best = t;
                        }
                    }
                }
            }
        }

        return best;
    }

    private static void hurt(ServerWorld w, LivingEntity t, DamageSource src, float amount) {
        if (t instanceof EnderDragonEntity dragon && dragon.getBodyParts().length > 0) {
            dragon.getBodyParts()[0].damage(src, amount);
        } else {
            t.damage(src, amount);
        }
    }

    private static LivingEntity pickTarget(ServerWorld w, ServerPlayerEntity p, SpearStrikeHandler.State s, CoopMovesConfig c) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVector().normalize();
        double range = c.spearAimRange;
        double cosCone = Math.cos(Math.toRadians(c.spearAimConeDeg));
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (LivingEntity t : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(range), x -> x.isAlive() && x != p)) {
            if (!(t instanceof ArmorStandEntity)
                && !(t instanceof ServerPlayerEntity sp && sp.isSpectator())
                && !s.hitThisThrow.contains(t.getId())
                && !t.hasPassenger(p)
                && !p.hasPassenger(t)) {
                Vec3d to = t.getBoundingBox().getCenter().subtract(eye);
                double dist = to.length();
                if (!(dist > range) && !(dist < 0.001)) {
                    double cos = to.multiply(1.0 / dist).dotProduct(look);
                    boolean locked = t.getId() == s.lockedId;
                    if (!(cos < (locked ? cosCone - 0.25 : cosCone))) {
                        double score = dist * (2.0 - cos) * (locked ? 0.6 : 1.0);
                        if (score < bestScore) {
                            bestScore = score;
                            best = t;
                        }
                    }
                }
            }
        }

        return best;
    }

    private static void nuke(ServerWorld w, ServerPlayerEntity p, Vec3d at, CoopMovesConfig c) {
        float core = Math.min(c.spearNukePower, 40.0F);
        nukeStages.add(new SpearStrikeHandler.NukeStage(w, at, serverTick, core, true, p));

        for (int i = 0; i < 6; i++) {
            double a = (Math.PI * 2) * i / 6.0;
            nukeStages.add(
                new SpearStrikeHandler.NukeStage(w, at.add(Math.cos(a) * 4.0, 0.0, Math.sin(a) * 4.0), serverTick + 3, core * 0.6F, false, p)
            );
            double b = a + (Math.PI / 6);
            nukeStages.add(
                new SpearStrikeHandler.NukeStage(w, at.add(Math.cos(b) * 8.0, 0.0, Math.sin(b) * 8.0), serverTick + 6, core * 0.45F, false, p)
            );
        }

        states.remove(p.getUuid());
    }

    private static void tickNukes() {
        if (!nukeStages.isEmpty()) {
            CoopMovesConfig c = CoopMovesConfig.get();
            ExplosionSourceType type = c.noGriefMode ? ExplosionSourceType.field_40888 : ExplosionSourceType.field_40891;
            Iterator<SpearStrikeHandler.NukeStage> it = nukeStages.iterator();

            while (it.hasNext()) {
                SpearStrikeHandler.NukeStage n = it.next();
                if (serverTick >= n.atTick()) {
                    it.remove();
                    ServerWorld w = n.world();
                    Vec3d at = n.pos();
                    w.createExplosion(n.owner(), at.x, at.y, at.z, n.power(), true, type);
                    if (n.core()) {
                        double r = c.spearNukeRadius;
                        Box box = new Box(
                            at.x - r, at.y - r, at.z - r, at.x + r, at.y + r, at.z + r
                        );

                        for (LivingEntity t : w.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                            if (t != n.owner()) {
                                double dist = Math.sqrt(t.squaredDistanceTo(at));
                                if (!(dist > r)) {
                                    float dmg = (float)(c.spearNukeDamage * (1.0 - 0.7 * dist / r));
                                    hurt(w, t, w.getDamageSources().explosion(n.owner(), n.owner()), dmg);
                                }
                            }
                        }

                        ServerPlayerEntity o = n.owner();
                        if (c.spearNukeKillsStriker && o != null && o.isAlive() && !o.isRemoved()) {
                            o.damage(w.getDamageSources().explosion(o, o), 10000.0F);
                        }

                        w.spawnParticles(ParticleTypes.field_17909, at.x, at.y, at.z, 3, 0.0, 0.0, 0.0, 0.0);
                        w.spawnParticles(ParticleTypes.field_11221, at.x, at.y, at.z, 4, 2.0, 1.0, 2.0, 0.0);

                        for (int i = 0; i < 40; i++) {
                            double h = i * 0.4;
                            double rr = i > 28 ? (i - 28) * 0.5 : 0.6;
                            w.spawnParticles(ParticleTypes.field_17430, at.x, at.y + h, at.z, 2, rr, 0.2, rr, 0.01);
                        }

                        w.spawnParticles(ParticleTypes.field_11239, at.x, at.y + 1.0, at.z, 40, 3.0, 1.0, 3.0, 0.2);
                        w.playSound(null, at.x, at.y, at.z, SoundEvents.field_14865, SoundCategory.field_15248, 6.0F, 0.5F);
                        w.playSound(null, at.x, at.y, at.z, SoundEvents.field_38830, SoundCategory.field_15248, 4.0F, 0.6F);
                    }
                }
            }
        }
    }

    private static void sendLock(ServerPlayerEntity p, SpearStrikeHandler.State s, int targetId, float speed, float turn, boolean missile) {
        if (s.sentLock != targetId || s.ticks % 10 == 0) {
            s.sentLock = targetId;
            ServerPlayNetworking.send(p, new SpearStrikeHandler.SpearLockPayload(targetId, speed, turn, missile));
        }
    }

    private static void setClientVelocity(ServerPlayerEntity p, Vec3d v) {
        p.setVelocity(v);
        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
    }

    private record NukeStage(ServerWorld world, Vec3d pos, int atTick, float power, boolean core, ServerPlayerEntity owner) {
    }

    public record SpearHoldPayload(boolean holding) implements CustomPayload {
        public static final Id<SpearStrikeHandler.SpearHoldPayload> ID = new Id(new Identifier("cooptest", "spear_hold"));
        public static final PacketCodec<PacketByteBuf, SpearStrikeHandler.SpearHoldPayload> CODEC = PacketCodec.of(
            (v, buf) -> buf.writeBoolean(v.holding()), buf -> new SpearStrikeHandler.SpearHoldPayload(buf.readBoolean())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SpearLockPayload(int targetId, float speed, float turn, boolean missile) implements CustomPayload {
        public static final Id<SpearStrikeHandler.SpearLockPayload> ID = new Id(new Identifier("cooptest", "spear_lock"));
        public static final PacketCodec<PacketByteBuf, SpearStrikeHandler.SpearLockPayload> CODEC = PacketCodec.of((v, buf) -> {
            buf.writeVarInt(v.targetId());
            buf.writeFloat(v.speed());
            buf.writeFloat(v.turn());
            buf.writeBoolean(v.missile());
        }, buf -> new SpearStrikeHandler.SpearLockPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static final class State {
        boolean holding;
        boolean testThrown;
        int testTicks;
        Vec3d lastPos;
        Vec3d motion = Vec3d.ZERO;
        final Set<Integer> hitThisThrow = new HashSet<>();
        int lockedId = -1;
        int ticks;
        int sentLock = -2;
        int chain;
        boolean missileSpent;
    }
}
