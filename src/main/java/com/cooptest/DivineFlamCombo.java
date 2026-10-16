package com.cooptest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import com.cooptest.fabricshim.fabric.api.networking.v1.PayloadTypeRegistry;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import com.cooptest.fabricshim.mc.CustomPayload;
import com.cooptest.fabricshim.mc.PacketCodec;
import com.cooptest.fabricshim.mc.CustomPayload.Id;

public class DivineFlamCombo {
    private static final Map<UUID, Long> comboWindowStart = new HashMap<>();
    private static final Map<UUID, UUID> comboPartner = new HashMap<>();
    private static final Map<UUID, Long> comboFreezeEnd = new HashMap<>();
    private static final long COMBO_WINDOW_MS = 1460L;
    private static final long COMBO_FREEZE_MS = 3800L;
    public static final Identifier DIVINE_J_PRESS_ID = new Identifier("cooptest", "divine_j_press");
    public static final Identifier DIVINE_START_ID = new Identifier("cooptest", "divine_start");

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(DivineFlamCombo.DivineJPressPayload.ID, DivineFlamCombo.DivineJPressPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DivineFlamCombo.DivineStartPayload.ID, DivineFlamCombo.DivineStartPayload.CODEC);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(DivineFlamCombo.DivineJPressPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> onPlayerPressJ(player));
        });
    }

    public static void startDivineFlame(ServerPlayerEntity p1, ServerPlayerEntity p2, Vec3d midpoint) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();
        comboWindowStart.put(id1, now);
        comboWindowStart.put(id2, now);
        comboPartner.put(id1, id2);
        comboPartner.put(id2, id1);
        PoseNetworking.broadcastAnimState(p1, 36);
        PoseNetworking.broadcastAnimState(p2, 36);
        comboFreezeEnd.put(id1, now + 1460L);
        comboFreezeEnd.put(id2, now + 1460L);
        ServerPlayNetworking.send(p1, new DivineFlamCombo.DivineStartPayload());
        ServerPlayNetworking.send(p2, new DivineFlamCombo.DivineStartPayload());
        ServerWorld world = p1.getServerWorld();
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z, ModSounds.EPIC_DAP, SoundCategory.field_15248, 1.5F, 1.1F);
    }

    private static void onPlayerPressJ(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        Long windowStart = comboWindowStart.get(playerId);
        if (windowStart != null) {
            long elapsed = System.currentTimeMillis() - windowStart;
            if (elapsed > 1460L) {
                comboWindowStart.remove(playerId);
                comboPartner.remove(playerId);
            } else {
                UUID partnerId = comboPartner.get(playerId);
                if (partnerId != null) {
                    ServerPlayerEntity partner = player.getServer().getPlayerManager().getPlayer(partnerId);
                    if (partner != null) {
                        if (comboWindowStart.containsKey(partnerId)) {
                            executeCombo(player, partner);
                        }
                    }
                }
            }
        }
    }

    private static void executeCombo(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        UUID id1 = p1.getUuid();
        UUID id2 = p2.getUuid();
        long now = System.currentTimeMillis();
        comboWindowStart.remove(id1);
        comboWindowStart.remove(id2);
        comboPartner.remove(id1);
        comboPartner.remove(id2);
        comboFreezeEnd.put(id1, now + 3800L);
        comboFreezeEnd.put(id2, now + 3800L);
        PoseNetworking.broadcastAnimState(p1, 37);
        PoseNetworking.broadcastAnimState(p2, 38);
        ServerWorld world = p1.getServerWorld();
        Vec3d midpoint = p1.getPos().add(p2.getPos()).multiply(0.5);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.field_14970, SoundCategory.field_15248, 10.0F, 0.2F);
        world.playSound(null, midpoint.x, midpoint.y, midpoint.z, SoundEvents.field_15231, SoundCategory.field_15248, 5.0F, 0.5F);
        world.spawnParticles(ParticleTypes.field_11240, midpoint.x, midpoint.y + 1.0, midpoint.z, 100, 1.0, 1.0, 1.0, 0.2);
        world.spawnParticles(ParticleTypes.field_22246, midpoint.x, midpoint.y + 1.0, midpoint.z, 50, 0.8, 0.8, 0.8, 0.15);
        world.spawnParticles(ParticleTypes.field_11239, midpoint.x, midpoint.y + 1.0, midpoint.z, 30, 0.5, 0.5, 0.5, 0.1);
    }

    public static void tick(ServerWorld world) {
        long now = System.currentTimeMillis();

        for (Entry<UUID, UUID> entry : comboPartner.entrySet()) {
            UUID id1 = entry.getKey();
            UUID id2 = entry.getValue();
            ServerPlayerEntity p1 = world.getServer().getPlayerManager().getPlayer(id1);
            ServerPlayerEntity p2 = world.getServer().getPlayerManager().getPlayer(id2);
            if (p1 != null && p2 != null) {
                double distance = p1.getPos().distanceTo(p2.getPos());
                if (distance > 1.0) {
                    Vec3d p1Pos = p1.getPos();
                    Vec3d p2Pos = p2.getPos();
                    Vec3d direction = p2Pos.subtract(p1Pos).normalize();
                    double targetDistance = 0.8;
                    Vec3d midpoint = p1Pos.add(p2Pos).multiply(0.5);
                    Vec3d offset = direction.multiply(targetDistance / 2.0);
                    Vec3d targetP1 = midpoint.subtract(offset);
                    Vec3d targetP2 = midpoint.add(offset);
                    double dx = p2Pos.x - p1Pos.x;
                    double dz = p2Pos.z - p1Pos.z;
                    float yawP1 = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
                    float yawP2 = yawP1 + 180.0F;
                    p1.teleport(p1.getServerWorld(), targetP1.x, targetP1.y, targetP1.z, yawP1, p1.getPitch());
                    p2.teleport(p2.getServerWorld(), targetP2.x, targetP2.y, targetP2.z, yawP2, p2.getPitch());
                }
            }
        }

        comboWindowStart.entrySet().removeIf(entryx -> {
            if (now - (Long)entryx.getValue() > 1460L) {
                UUID id = (UUID)entryx.getKey();
                comboPartner.remove(id);
                ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);
                if (player != null) {
                    PoseNetworking.broadcastAnimState(player, 0);
                }

                return true;
            } else {
                return false;
            }
        });
        comboFreezeEnd.entrySet().removeIf(entryx -> {
            if (now >= (Long)entryx.getValue()) {
                ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer((UUID)entryx.getKey());
                if (player != null) {
                    PoseNetworking.broadcastAnimState(player, 0);
                }

                return true;
            } else {
                return false;
            }
        });
    }

    public static void cleanup(UUID playerId) {
        comboWindowStart.remove(playerId);
        comboPartner.remove(playerId);
        comboFreezeEnd.remove(playerId);
    }

    public static boolean isInComboFreeze(UUID playerId) {
        return comboFreezeEnd.containsKey(playerId);
    }

    public record DivineJPressPayload() implements CustomPayload {
        public static final Id<DivineFlamCombo.DivineJPressPayload> ID = new Id(DivineFlamCombo.DIVINE_J_PRESS_ID);
        public static final PacketCodec<PacketByteBuf, DivineFlamCombo.DivineJPressPayload> CODEC = PacketCodec.unit(
            new DivineFlamCombo.DivineJPressPayload()
        );

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record DivineStartPayload() implements CustomPayload {
        public static final Id<DivineFlamCombo.DivineStartPayload> ID = new Id(DivineFlamCombo.DIVINE_START_ID);
        public static final PacketCodec<PacketByteBuf, DivineFlamCombo.DivineStartPayload> CODEC = PacketCodec.unit(new DivineFlamCombo.DivineStartPayload());

        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
