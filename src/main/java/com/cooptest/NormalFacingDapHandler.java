package com.cooptest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents.AllowDamage;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.player.UseEntityCallback;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
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
import net.minecraft.server.MinecraftServer;

public class NormalFacingDapHandler {
    private static final double FACE_DIST = 1.3;
    private static final long IMPACT_AT_MS = 420L;
    private static final long HOLD_WINDOW_START_MS = 630L;
    private static final long HOLD_SWAP_AT_MS = 2130L;
    private static final long MOVE_RELEASE_AT_MS = 2540L;
    private static final long HIT_FACE_LEN_MS = 3125L;
    private static final long HOLD_ENTRY_MS = 0L;
    private static final long HOLD_END_MS = 850L;
    private static final long HOLD_GRACE_MS = 300L;
    private static final long MIN_HOLD_MS = 250L;
    private static final float IMPACT_SHAKE_AMOUNT = 3.0F;
    private static final long IMPACT_SHAKE_MS = 80L;
    private static final float GRIP_SHAKE_AMOUNT = 2.0F;
    private static final long GRIP_SHAKE_MS = 150L;
    private static final long IDLE_SHAKE_INTERVAL_MS = 120L;
    private static final float IDLE_SHAKE_AMOUNT = 0.55F;
    private static final long IDLE_SHAKE_MS = 200L;
    private static final long SHAKE_SFX_INTERVAL_MS = 170L;
    private static final long WINDOW_SHAKE_INTERVAL_MS = 180L;
    private static final float WINDOW_SHAKE_AMOUNT = 0.7F;
    private static final long WINDOW_SHAKE_MS = 220L;
    private static final float END_SHAKE_AMOUNT = 0.9F;
    private static final long END_SHAKE_MS = 120L;
    private static final float FIZZLE_SHAKE_AMOUNT = 0.4F;
    private static final long FIZZLE_SHAKE_MS = 100L;
    private static final int HOLD_PARTICLE_INTERVAL = 20;
    private static final boolean SHOW_HOLD_TIMER = true;
    private static final float DAMAGE_CANCEL_THRESHOLD = 4.0F;
    private static final long CLICK_WINDOW = 2000L;
    private static final int ANIM_NONE = 0;
    private static final int ANIM_DAP_HIT_FACE = 81;
    private static final int ANIM_DAP_HOLD = 109;
    private static final int ANIM_HOLD_LOOP = 84;
    private static final int ANIM_DAP_HOLD_END = 111;
    public static final Identifier LOOP_HOLD_ID = new Identifier("cooptest", "dap_loop_hold");
    public static final Identifier SESSION_STATE_ID = new Identifier("cooptest", "face_dap_session");
    public static final Identifier SHAKE_ID = new Identifier("cooptest", "face_dap_shake");
    private static final Map<UUID, NormalFacingDapHandler.Session> sessions = new HashMap<>();
    private static final Map<UUID, UUID> clickMap = new HashMap<>();
    private static final Map<UUID, Long> clickTime = new HashMap<>();

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(NormalFacingDapHandler.DapLoopHoldPayload.ID, NormalFacingDapHandler.DapLoopHoldPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NormalFacingDapHandler.FaceDapSessionPayload.ID, NormalFacingDapHandler.FaceDapSessionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NormalFacingDapHandler.FaceDapShakePayload.ID, NormalFacingDapHandler.FaceDapShakePayload.CODEC);
    }

    public static boolean isActive(UUID id) {
        return sessions.containsKey(id);
    }

    public static boolean isHolding(UUID id) {
        NormalFacingDapHandler.Session s = sessions.get(id);
        return s != null && (s.stage == NormalFacingDapHandler.Stage.HOLD_ENTRY || s.stage == NormalFacingDapHandler.Stage.HOLD_IDLE);
    }

    public static void onLoopHold(ServerPlayerEntity player) {
        NormalFacingDapHandler.Session s = sessions.get(player.getUuid());
        if (s != null) {
            long now = System.currentTimeMillis();
            if (player.getUuid().equals(s.a)) {
                s.holdA = now;
            } else {
                s.holdB = now;
            }
        }
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
            NormalFacingDapHandler.DapLoopHoldPayload.ID, (payload, context) -> context.server().execute(() -> onLoopHold(context.player()))
        );
        ServerTickEvents.END_SERVER_TICK.register(NormalFacingDapHandler::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((AllowDamage)(entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity victim) {
                if (amount < 4.0F) {
                    return true;
                } else {
                    NormalFacingDapHandler.Session s = sessions.get(victim.getUuid());
                    if (s == null) {
                        return true;
                    } else {
                        MinecraftServer server = victim.getServer();
                        if (server != null) {
                            hardCancel(server, s);
                        }

                        return true;
                    }
                }
            } else {
                return true;
            }
        });
        UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            } else if (player instanceof ServerPlayerEntity sp) {
                if (sp.isSneaking()) {
                    return ActionResult.PASS;
                } else {
                    NormalFacingDapHandler.Session s = sessions.get(sp.getUuid());
                    if (s != null && s.stage == NormalFacingDapHandler.Stage.HOLD_IDLE) {
                        MinecraftServer server = sp.getServer();
                        if (server != null) {
                            beginEnd(server, s);
                        }

                        return ActionResult.SUCCESS;
                    } else {
                        return ActionResult.PASS;
                    }
                }
            } else {
                return ActionResult.PASS;
            }
        });
    }

    private static void tick(MinecraftServer server) {
        if (!sessions.isEmpty()) {
            long now = System.currentTimeMillis();

            for (NormalFacingDapHandler.Session s : new HashSet<>(sessions.values())) {
                ServerPlayerEntity a = server.getPlayerManager().getPlayer(s.a);
                ServerPlayerEntity b = server.getPlayerManager().getPlayer(s.b);
                if (a != null && b != null && a.isAlive() && b.isAlive()) {
                    s.tickCounter++;
                    if (s.stage == NormalFacingDapHandler.Stage.HIT
                        || s.stage == NormalFacingDapHandler.Stage.HOLD_ENTRY
                        || s.stage == NormalFacingDapHandler.Stage.HOLD_IDLE) {
                        pin(a, s.yawA);
                        pin(b, s.yawB);
                    }

                    switch (s.stage) {
                        case HIT:
                            tickHit(s, a, b, now);
                            break;
                        case HOLD_ENTRY:
                            if (s.inStage(now) >= 0L) {
                                broadcast(a, 84);
                                broadcast(b, 84);
                                s.stage(NormalFacingDapHandler.Stage.HOLD_IDLE, now);
                                PactHandler.onHandshakeStart(a, b);
                            }
                            break;
                        case HOLD_IDLE:
                            tickHoldIdle(server, s, a, b, now);
                            break;
                        case ENDING:
                            if (s.inStage(now) >= 850L) {
                                cleanup(server, s, true);
                            }
                            break;
                        case CANCELLING:
                            if (s.inStage(now) >= 200L) {
                                cleanup(server, s, true);
                            }
                    }
                } else {
                    hardCancel(server, s);
                }
            }
        }
    }

    private static void tickHit(NormalFacingDapHandler.Session s, ServerPlayerEntity a, ServerPlayerEntity b, long now) {
        long e = s.elapsed(now);
        if (!s.moveReleased && e >= 2540L) {
            s.moveReleased = true;
            ServerPlayNetworking.send(a, new NormalFacingDapHandler.FaceDapSessionPayload(false));
            ServerPlayNetworking.send(b, new NormalFacingDapHandler.FaceDapSessionPayload(false));
        }

        if (!s.impactDone && e >= 420L) {
            s.impactDone = true;
            PactHandler.onHandshakeCounted(a, b);
            impact(a, b);
            shake(a, b, 3.0F, 80L);
        }

        if (e >= 630L && e < 2130L && now - s.lastIdleShake >= 180L) {
            s.lastIdleShake = now;
            shake(a, b, 0.7F, 220L);
        }

        if (e >= 2130L) {
            boolean bothHeld = s.holding(s.a, now) && s.holding(s.b, now) && s.holdA - s.startMs >= 630L && s.holdB - s.startMs >= 630L;
            if (bothHeld) {
                broadcast(a, 84);
                broadcast(b, 84);
                s.stage(NormalFacingDapHandler.Stage.HOLD_IDLE, now);
                PactHandler.onHandshakeStart(a, b);
                playAt(a, b, SoundEvents.field_14840, 0.7F, 0.8F);
                shake(a, b, 2.0F, 150L);
            } else {
                fizzle(a, b);
                shake(a, b, 0.4F, 100L);
                s.stage(NormalFacingDapHandler.Stage.CANCELLING, now);
            }
        }
    }

    private static void tickHoldIdle(MinecraftServer server, NormalFacingDapHandler.Session s, ServerPlayerEntity a, ServerPlayerEntity b, long now) {
        boolean stillHolding = s.holding(s.a, now) && s.holding(s.b, now);
        if (!stillHolding && s.inStage(now) >= 250L) {
            beginEnd(server, s);
        } else {
            PactHandler.onHold(a);
            PactHandler.onHold(b);
            if (now - s.lastIdleShake >= 120L) {
                s.lastIdleShake = now;
                shake(a, b, 0.55F, 200L);
            }

            if (now - s.lastShakeSfx >= 170L) {
                s.lastShakeSfx = now;
                playAt(a, b, SoundEvents.field_14914, 0.35F, 1.7F);
            }

            if (s.tickCounter % 20 == 0) {
                ServerWorld w = (ServerWorld)a.getWorld();
                Vec3d m = mid(a, b);
                w.spawnParticles(ParticleTypes.field_11205, m.x, m.y, m.z, 1, 0.03, 0.03, 0.03, 0.005);
            }

            if (!PactHandler.isSealing(s.a)) {
                long held = s.inStage(now) / 1000L;
                Text t = Text.literal("§6\ud83e\udd1d §f" + held + "s");
                a.sendMessage(t, true);
                b.sendMessage(t, true);
            }
        }
    }

    public static void start(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        if (!sessions.containsKey(id1) && !sessions.containsKey(id2)) {
            MinecraftServer server = p1.getServer();
            if (server != null) {
                if (id1.compareTo(id2) > 0) {
                    ServerPlayerEntity tmp = p1;
                    p1 = p2;
                    p2 = tmp;
                    id1 = p1.getUuid();
                    id2 = tmp.getUuid();
                }

                Vec3d diff = p2.getPos().subtract(p1.getPos());
                Vec3d flat = new Vec3d(diff.x, 0.0, diff.z).normalize();
                Vec3d mid = p1.getPos().add(p2.getPos()).multiply(0.5);
                Vec3d pos1 = mid.subtract(flat.multiply(0.65));
                Vec3d pos2 = mid.add(flat.multiply(0.65));
                float yaw1 = (float)Math.toDegrees(Math.atan2(-flat.x, flat.z));
                float yaw2 = yaw1 + 180.0F;
                p1.teleport((ServerWorld)p1.getWorld(), pos1.x, p1.getY(), pos1.z, yaw1, p1.getPitch());
                p2.teleport((ServerWorld)p2.getWorld(), pos2.x, p2.getY(), pos2.z, yaw2, p2.getPitch());
                applyYaw(p1, yaw1);
                applyYaw(p2, yaw2);
                p1.swingHand(Hand.field_5808, true);
                p2.swingHand(Hand.field_5808, true);
                long now = System.currentTimeMillis();
                NormalFacingDapHandler.Session s = new NormalFacingDapHandler.Session(id1, id2, yaw1, yaw2, now);
                sessions.put(id1, s);
                sessions.put(id2, s);
                ServerPlayNetworking.send(p1, new ChargedDapHandler.PerfectDapFreezePayload(true));
                ServerPlayNetworking.send(p2, new ChargedDapHandler.PerfectDapFreezePayload(true));
                ServerPlayNetworking.send(p1, new NormalFacingDapHandler.FaceDapSessionPayload(true));
                ServerPlayNetworking.send(p2, new NormalFacingDapHandler.FaceDapSessionPayload(true));
                broadcast(p1, 81);
                broadcast(p2, 81);
            }
        }
    }

    private static void shake(ServerPlayerEntity a, ServerPlayerEntity b, float amount, long ms) {
        NormalFacingDapHandler.FaceDapShakePayload p = new NormalFacingDapHandler.FaceDapShakePayload(amount, ms);
        if (a != null) {
            ServerPlayNetworking.send(a, p);
        }

        if (b != null) {
            ServerPlayNetworking.send(b, p);
        }
    }

    private static void impact(ServerPlayerEntity a, ServerPlayerEntity b) {
        ServerWorld w = (ServerWorld)a.getWorld();
        Vec3d m = mid(a, b);
        w.playSound(null, m.x, m.y, m.z, ModSounds.DAP_HIT, SoundCategory.field_15248, 1.5F, 1.0F);
        w.playSound(null, m.x, m.y, m.z, SoundEvents.field_15016, SoundCategory.field_15248, 1.0F, 1.2F);
        w.spawnParticles(ParticleTypes.field_11205, m.x, m.y, m.z, 12, 0.2, 0.2, 0.2, 0.1);
        w.spawnParticles(ParticleTypes.field_11208, m.x, m.y, m.z, 6, 0.15, 0.15, 0.15, 0.07);
        w.spawnParticles(ParticleTypes.field_17909, m.x, m.y, m.z, 2, 0.0, 0.0, 0.0, 0.0);
    }

    private static void fizzle(ServerPlayerEntity a, ServerPlayerEntity b) {
        ServerWorld w = (ServerWorld)a.getWorld();
        Vec3d m = mid(a, b);
        w.playSound(null, m.x, m.y, m.z, SoundEvents.field_15102, SoundCategory.field_15248, 0.8F, 1.4F);
        w.playSound(null, m.x, m.y, m.z, ModSounds.DAP_MISS, SoundCategory.field_15248, 0.7F, 1.0F);
        w.spawnParticles(ParticleTypes.field_11251, m.x, m.y, m.z, 14, 0.2, 0.2, 0.2, 0.02);
    }

    private static void playAt(ServerPlayerEntity a, ServerPlayerEntity b, SoundEvent sound, float vol, float pitch) {
        ServerWorld w = (ServerWorld)a.getWorld();
        Vec3d m = mid(a, b);
        w.playSound(null, m.x, m.y, m.z, sound, SoundCategory.field_15248, vol, pitch);
    }

    private static Vec3d mid(ServerPlayerEntity a, ServerPlayerEntity b) {
        return a.getPos().add(b.getPos()).multiply(0.5).add(0.0, 1.3, 0.0);
    }

    private static void beginEnd(MinecraftServer server, NormalFacingDapHandler.Session s) {
        if (s.stage != NormalFacingDapHandler.Stage.ENDING && s.stage != NormalFacingDapHandler.Stage.CANCELLING) {
            long now = System.currentTimeMillis();
            ServerPlayerEntity a = server.getPlayerManager().getPlayer(s.a);
            ServerPlayerEntity b = server.getPlayerManager().getPlayer(s.b);
            s.stage(NormalFacingDapHandler.Stage.ENDING, now);

            for (ServerPlayerEntity p : new ServerPlayerEntity[]{a, b}) {
                if (p != null) {
                    ServerPlayNetworking.send(p, new ChargedDapHandler.PerfectDapFreezePayload(false));
                    ServerPlayNetworking.send(p, new NormalFacingDapHandler.FaceDapSessionPayload(false));
                    broadcast(p, 111);
                }
            }

            shake(a, b, 0.9F, 120L);
        }
    }

    private static void hardCancel(MinecraftServer server, NormalFacingDapHandler.Session s) {
        cleanup(server, s, true);
    }

    private static void cleanup(MinecraftServer server, NormalFacingDapHandler.Session s, boolean clearAnim) {
        sessions.remove(s.a);
        sessions.remove(s.b);
        PactHandler.cleanup(s.a);
        PactHandler.cleanup(s.b);
        long cd = System.currentTimeMillis() + ChargedDapHandler.cooldownMs();
        ChargedDapHandler.cooldowns.put(s.a, cd);
        ChargedDapHandler.cooldowns.put(s.b, cd);

        for (UUID id : new UUID[]{s.a, s.b}) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) {
                ServerPlayNetworking.send(p, new ChargedDapHandler.PerfectDapFreezePayload(false));
                ServerPlayNetworking.send(p, new NormalFacingDapHandler.FaceDapSessionPayload(false));
                if (clearAnim) {
                    broadcast(p, 0);
                }
            }
        }
    }

    public static void cleanup(UUID id) {
        clickMap.remove(id);
        clickTime.remove(id);
        NormalFacingDapHandler.Session s = sessions.get(id);
        if (s != null) {
            sessions.remove(s.a);
            sessions.remove(s.b);
            PactHandler.cleanup(s.a);
            PactHandler.cleanup(s.b);
        }
    }

    public static void recordRightClick(ServerPlayerEntity sp, ServerPlayerEntity target) {
        clickMap.put(sp.getUuid(), target.getUuid());
        clickTime.put(sp.getUuid(), System.currentTimeMillis());
        sp.sendMessage(Text.literal("§e✦ Waiting for homie..."), true);
    }

    public static boolean isConfirmed(UUID id1, UUID id2) {
        long now = System.currentTimeMillis();
        UUID c1 = clickMap.get(id1);
        Long t1 = clickTime.get(id1);
        UUID c2 = clickMap.get(id2);
        Long t2 = clickTime.get(id2);
        return id2.equals(c1) && t1 != null && now - t1 < 2000L && id1.equals(c2) && t2 != null && now - t2 < 2000L;
    }

    public static boolean isConfirmedOneSide(UUID who, UUID target) {
        UUID c = clickMap.get(who);
        Long t = clickTime.get(who);
        return target.equals(c) && t != null && System.currentTimeMillis() - t < 2000L;
    }

    public static void clearConfirm(UUID id1, UUID id2) {
        clickMap.remove(id1);
        clickTime.remove(id1);
        if (id2 != null) {
            clickMap.remove(id2);
            clickTime.remove(id2);
        }
    }

    private static void pin(ServerPlayerEntity p, float yaw) {
        p.setVelocity(0.0, 0.0, 0.0);
        p.velocityModified = true;
        applyYaw(p, yaw);
    }

    private static void applyYaw(ServerPlayerEntity p, float yaw) {
        p.setYaw(yaw);
        p.setBodyYaw(yaw);
        p.setHeadYaw(yaw);
    }

    private static void broadcast(ServerPlayerEntity p, int ordinal) {
        PoseNetworking.broadcastAnimState(p, ordinal);
    }

    public record DapLoopHoldPayload() implements CustomPayload {
        public static final Id<NormalFacingDapHandler.DapLoopHoldPayload> ID = new Id(NormalFacingDapHandler.LOOP_HOLD_ID);
        public static final PacketCodec<PacketByteBuf, NormalFacingDapHandler.DapLoopHoldPayload> CODEC = PacketCodec.unit(
            new NormalFacingDapHandler.DapLoopHoldPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FaceDapSessionPayload(boolean active) implements CustomPayload {
        public static final Id<NormalFacingDapHandler.FaceDapSessionPayload> ID = new Id(NormalFacingDapHandler.SESSION_STATE_ID);
        public static final PacketCodec<PacketByteBuf, NormalFacingDapHandler.FaceDapSessionPayload> CODEC = PacketCodec.of(
            (v, buf) -> buf.writeBoolean(v.active()), buf -> new NormalFacingDapHandler.FaceDapSessionPayload(buf.readBoolean())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FaceDapShakePayload(float amount, long durationMs) implements CustomPayload {
        public static final Id<NormalFacingDapHandler.FaceDapShakePayload> ID = new Id(NormalFacingDapHandler.SHAKE_ID);
        public static final PacketCodec<PacketByteBuf, NormalFacingDapHandler.FaceDapShakePayload> CODEC = PacketCodec.of((v, buf) -> {
            buf.writeFloat(v.amount());
            buf.writeLong(v.durationMs());
        }, buf -> new NormalFacingDapHandler.FaceDapShakePayload(buf.readFloat(), buf.readLong()));

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static final class Session {
        final UUID a;
        final UUID b;
        final float yawA;
        final float yawB;
        NormalFacingDapHandler.Stage stage = NormalFacingDapHandler.Stage.HIT;
        final long startMs;
        long stageStartMs;
        boolean impactDone;
        long holdA;
        long holdB;
        long lastIdleShake;
        long lastShakeSfx;
        boolean moveReleased;
        int tickCounter;

        Session(UUID a, UUID b, float yawA, float yawB, long now) {
            this.a = a;
            this.b = b;
            this.yawA = yawA;
            this.yawB = yawB;
            this.startMs = now;
            this.stageStartMs = now;
        }

        long elapsed(long now) {
            return now - this.startMs;
        }

        long inStage(long now) {
            return now - this.stageStartMs;
        }

        void stage(NormalFacingDapHandler.Stage s, long now) {
            this.stage = s;
            this.stageStartMs = now;
        }

        boolean holding(UUID id, long now) {
            long h = id.equals(this.a) ? this.holdA : this.holdB;
            return h != 0L && now - h <= 300L;
        }
    }

    private static enum Stage {
        HIT,
        HOLD_ENTRY,
        HOLD_IDLE,
        ENDING,
        CANCELLING;
    }
}
