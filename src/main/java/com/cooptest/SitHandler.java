package com.cooptest;

import com.cooptest.client.CoopAnimationHandler;
import com.mojang.brigadier.context.CommandContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Hand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;

public class SitHandler {
    private static final Map<UUID, Double> sittingPlayers = new HashMap<>();
    private static final Map<UUID, UUID> reachingSitter = new HashMap<>();
    private static final Set<String> activePickup = new HashSet<>();

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(SitHandler.SitFHoldPayload.ID, SitHandler.SitFHoldPayload.CODEC);
    }

    public static boolean isSitting(UUID id) {
        return sittingPlayers.containsKey(id);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
            SitHandler.SitFHoldPayload.ID, (payload, context) -> context.server().execute(() -> onFHold(context.player(), payload.holding()))
        );
        ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
            for (Entry<UUID, Double> e : new HashMap<>(sittingPlayers).entrySet()) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p != null) {
                    double sitY = e.getValue() - 0.5;
                    p.setVelocity(0.0, 0.0, 0.0);
                    p.velocityModified = true;
                    if (Math.abs(p.getY() - sitY) > 0.05) {
                        p.teleport(p.getServerWorld(), p.getX(), sitY, p.getZ(), Set.of(), p.getYaw(), p.getPitch());
                    }
                }
            }
        });
    }

    public static int executeSit(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ((ServerCommandSource)ctx.getSource()).getPlayer();
        if (player == null) {
            return 0;
        } else {
            UUID id = player.getUuid();
            if (isSitting(id)) {
                if (!isInPickup(id)) {
                    standup(player, null);
                }
            } else {
                sit(player);
            }

            return 1;
        }
    }

    private static void sit(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        double originalY = player.getY();
        double sitY = originalY - 0.5;
        sittingPlayers.put(id, originalY);
        player.teleport(player.getServerWorld(), player.getX(), sitY, player.getZ(), Set.of(), player.getYaw(), player.getPitch());
        ServerPlayNetworking.send(player, new ChargedDapHandler.PerfectDapFreezePayload(true));
        PoseNetworking.broadcastAnimState(player, CoopAnimationHandler.AnimState.SITTING.ordinal());
        player.sendMessage(Text.literal("§7[Sitting — a friend can hold F to help you up]"), true);
    }

    private static void onFHold(ServerPlayerEntity helper, boolean holding) {
        UUID hid = helper.getUuid();
        if (!isSitting(hid)) {
            if (!holding) {
                UUID sitterId = reachingSitter.remove(hid);
                if (sitterId == null) {
                    return;
                }

                ServerPlayerEntity sitter = helper.getServer().getPlayerManager().getPlayer(sitterId);
                if (sitter == null || !isSitting(sitterId)) {
                    return;
                }

                if (helper.distanceTo(sitter) > 1.5F) {
                    PoseNetworking.broadcastAnimState(helper, 0);
                    return;
                }

                startPickup(helper, sitter);
            } else {
                ServerPlayerEntity nearest = null;
                double closest = 3.0;

                for (UUID sid : sittingPlayers.keySet()) {
                    ServerPlayerEntity s = helper.getServer().getPlayerManager().getPlayer(sid);
                    if (s != null && helper.distanceTo(s) < closest) {
                        closest = helper.distanceTo(s);
                        nearest = s;
                    }
                }

                if (nearest == null) {
                    return;
                }

                if (isInPickup(nearest.getUuid())) {
                    return;
                }

                reachingSitter.put(hid, nearest.getUuid());
                PoseNetworking.broadcastAnimState(helper, CoopAnimationHandler.AnimState.REACH_DOWN.ordinal());
            }
        }
    }

    private static void startPickup(ServerPlayerEntity helper, ServerPlayerEntity sitter) {
        UUID hid = helper.getUuid();
        UUID sid = sitter.getUuid();
        String k = hid + ":" + sid;
        Double originalY = sittingPlayers.get(sid);
        double sitY = originalY != null ? originalY - 0.5 : sitter.getY();
        activePickup.add(k);
        Vec3d diff = sitter.getPos().subtract(helper.getPos());
        float helperYaw = (float)Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float sitterYaw = helperYaw + 180.0F;
        helper.setYaw(helperYaw);
        helper.setBodyYaw(helperYaw);
        helper.setHeadYaw(helperYaw);
        sitter.setYaw(sitterYaw);
        sitter.setBodyYaw(sitterYaw);
        sitter.setHeadYaw(sitterYaw);
        helper.swingHand(Hand.field_5808, true);
        ServerPlayNetworking.send(helper, new ChargedDapHandler.PerfectDapFreezePayload(true));
        ServerPlayNetworking.send(sitter, new ChargedDapHandler.PerfectDapFreezePayload(true));
        PoseNetworking.broadcastAnimState(helper, CoopAnimationHandler.AnimState.REACH_PICKUP.ordinal());
        PoseNetworking.broadcastAnimState(sitter, CoopAnimationHandler.AnimState.STAND_UP.ordinal());
        MinecraftServer server = helper.getServer();
        schedule(server, 2880L, () -> {
            ServerPlayerEntity h = server.getPlayerManager().getPlayer(hid);
            ServerPlayerEntity s = server.getPlayerManager().getPlayer(sid);
            if (h != null && s != null) {
                Vec3d dir = s.getPos().subtract(h.getPos()).normalize();
                Vec3d mid = h.getPos().add(0.0, 1.2, 0.0).add(dir.multiply(0.5));
                h.getServerWorld().playSound(null, mid.x, mid.y, mid.z, ModSounds.DAP_HIT, SoundCategory.field_15248, 1.2F, 0.8F);
                h.getServerWorld().playSound(null, mid.x, mid.y, mid.z, SoundEvents.field_15016, SoundCategory.field_15248, 1.0F, 1.0F);
                h.getServerWorld().spawnParticles(ParticleTypes.field_11205, mid.x, mid.y, mid.z, 8, 0.15, 0.15, 0.15, 0.06);
                h.getServerWorld().spawnParticles(ParticleTypes.field_11208, mid.x, mid.y, mid.z, 4, 0.1, 0.1, 0.1, 0.04);
            }
        });
        long LIFT_START_MS = 3880L;
        long LIFT_END_MS = 5170L;
        int LIFT_STEPS = 10;
        long stepInterval = 129L;

        for (int i = 0; i <= 10; i++) {
            int step = i;
            long delay = 3880L + step * 129L;
            schedule(server, delay, () -> {
                ServerPlayerEntity s = server.getPlayerManager().getPlayer(sid);
                if (s != null) {
                    if (step == 0) {
                        sittingPlayers.remove(sid);
                    }

                    if (originalY != null) {
                        double t = step / 10.0;
                        double liftY = sitY + (originalY - sitY) * t;
                        s.teleport(s.getServerWorld(), s.getX(), liftY, s.getZ(), Set.of(), s.getYaw(), s.getPitch());
                    }
                }
            });
        }

        double SITTER_PUSH_FORWARD = 0.2;
        double HELPER_PUSH_BACKWARD = 0.2;
        schedule(
            server,
            4290L,
            () -> {
                ServerPlayerEntity h = server.getPlayerManager().getPlayer(hid);
                ServerPlayerEntity s = server.getPlayerManager().getPlayer(sid);
                if (h != null && s != null) {
                    Vec3d dir2 = s.getPos().subtract(h.getPos()).normalize();
                    Vec3d newHelperPos = h.getPos().add(dir2.multiply(-0.2));
                    h.teleport(
                        h.getServerWorld(), newHelperPos.x, h.getY(), newHelperPos.z, Set.of(), h.getYaw(), h.getPitch()
                    );
                    Vec3d newSitterPos = s.getPos().add(dir2.multiply(-0.2));
                    s.teleport(
                        s.getServerWorld(), newSitterPos.x, s.getY(), newSitterPos.z, Set.of(), s.getYaw(), s.getPitch()
                    );
                }
            }
        );
        schedule(server, 5200L, () -> {
            ServerPlayerEntity h = server.getPlayerManager().getPlayer(hid);
            ServerPlayerEntity s = server.getPlayerManager().getPlayer(sid);
            if (h != null) {
                ServerPlayNetworking.send(h, new ChargedDapHandler.PerfectDapFreezePayload(false));
            }

            if (s != null) {
                ServerPlayNetworking.send(s, new ChargedDapHandler.PerfectDapFreezePayload(false));
            }
        });
        schedule(server, 6100L, () -> {
            activePickup.remove(k);
            ServerPlayerEntity h = server.getPlayerManager().getPlayer(hid);
            ServerPlayerEntity s = server.getPlayerManager().getPlayer(sid);
            if (h != null) {
                PoseNetworking.broadcastAnimState(h, 0);
            }

            if (s != null) {
                PoseNetworking.broadcastAnimState(s, 0);
            }
        });
    }

    private static void standup(ServerPlayerEntity player, Double originalY) {
        UUID id = player.getUuid();
        Double oy = sittingPlayers.remove(id);
        if (oy != null) {
            player.teleport(player.getServerWorld(), player.getX(), oy, player.getZ(), Set.of(), player.getYaw(), player.getPitch());
        }

        ServerPlayNetworking.send(player, new ChargedDapHandler.PerfectDapFreezePayload(false));
        PoseNetworking.broadcastAnimState(player, 0);
    }

    private static boolean isInPickup(UUID id) {
        return activePickup.stream().anyMatch(k -> k.contains(id.toString()));
    }

    private static void schedule(MinecraftServer server, long ms, Runnable r) {
        new Timer(true).schedule(new TimerTask() {
            @Override
            public void run() {
                server.execute(r);
            }
        }, ms);
    }

    public static void cleanup(UUID id) {
        sittingPlayers.remove(id);
        reachingSitter.remove(id);
        activePickup.removeIf(k -> k.contains(id.toString()));
    }

    public record SitFHoldPayload(boolean holding) implements CustomPayload {
        public static final Id<SitHandler.SitFHoldPayload> ID = new Id(new Identifier("cooptest", "sit_f_hold"));
        public static final PacketCodec<PacketByteBuf, SitHandler.SitFHoldPayload> CODEC = PacketCodec.of(
            (v, buf) -> buf.writeBoolean(v.holding()), buf -> new SitHandler.SitFHoldPayload(buf.readBoolean())
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
