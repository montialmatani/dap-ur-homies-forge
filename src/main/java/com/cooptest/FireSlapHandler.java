package com.cooptest;

import com.cooptest.fabricshim.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;

public class FireSlapHandler {
    private static final float MIN_FIRE_LEVEL = 0.95F;
    private static final double SLAP_KNOCKBACK = 2.5;
    private static final double SLAP_VERTICAL = 0.5;

    public static void register() {
        AttackEntityCallback.EVENT.register((AttackEntityCallback)(player, world, hand, entity, hitResult) -> {
            if (world.isClient) {
                return ActionResult.PASS;
            } else if (player instanceof ServerPlayerEntity serverPlayer) {
                if (entity instanceof LivingEntity target) {
                    if (entity instanceof PlayerEntity) {
                        return ActionResult.PASS;
                    } else {
                        float fireLevel = ChargedDapHandler.fireLevel.getOrDefault(player.getUuid(), 0.0F);
                        if (fireLevel < 0.95F) {
                            return ActionResult.PASS;
                        } else {
                            executeFireSlap(serverPlayer, target);
                            return ActionResult.PASS;
                        }
                    }
                } else {
                    return ActionResult.PASS;
                }
            } else {
                return ActionResult.PASS;
            }
        });
    }

    private static void executeFireSlap(ServerPlayerEntity player, LivingEntity target) {
        ServerWorld world = player.getServerWorld();
        Vec3d playerPos = player.getPos();
        Vec3d targetPos = target.getPos();
        Vec3d direction = targetPos.subtract(playerPos).normalize();
        target.setVelocity(direction.x * 2.5, 0.5, direction.z * 2.5);
        target.velocityModified = true;
        target.setOnFireFor(2.0F);
        double x = target.getX();
        double y = target.getY() + target.getHeight() / 2.0F;
        double z = target.getZ();
        world.spawnParticles(ParticleTypes.field_11240, x, y, z, 8, 0.3, 0.3, 0.3, 0.05);
        world.spawnParticles(ParticleTypes.field_11251, x, y, z, 5, 0.2, 0.2, 0.2, 0.02);
        world.spawnParticles(ParticleTypes.field_11205, x, y, z, 6, 0.3, 0.3, 0.3, 0.1);
        world.playSound(null, x, y, z, SoundEvents.field_14999, SoundCategory.field_15248, 1.0F, 0.8F);
        world.playSound(null, x, y, z, SoundEvents.field_14842, SoundCategory.field_15248, 0.5F, 1.2F);
        player.sendMessage(Text.literal("§c FIRE SLAP! "), true);
        ChargedDapHandler.fireLevel.put(player.getUuid(), 0.5F);
    }
}
