package com.cooptest;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;

public final class DapHearts {
    private DapHearts() {
    }

    public static void clean(ServerPlayerEntity p) {
        CoopMovesConfig c = CoopMovesConfig.get();
        add(p, c.dapHeartsClean);
        if (p != null && p.isAlive() && c.dapCleanHeal > 0.0F) {
            p.heal(c.dapCleanHeal * 2.0F);
        }
    }

    public static void perfect(ServerPlayerEntity p) {
        add(p, CoopMovesConfig.get().dapHeartsPerfect);
    }

    public static void great(ServerPlayerEntity p) {
        add(p, CoopMovesConfig.get().dapHeartsGreat);
    }

    public static void flair(ServerPlayerEntity p, boolean cleanTier) {
        CoopMovesConfig c = CoopMovesConfig.get();
        add(p, cleanTier ? c.dapHeartsFlairClean : c.dapHeartsFlairBig);
    }

    public static void add(ServerPlayerEntity p, float hearts) {
        CoopMovesConfig c = CoopMovesConfig.get();
        if (p != null && c.enableDapHearts && p.isAlive() && !(hearts <= 0.0F)) {
            float addHp = hearts * 2.0F;
            float maxHp = Math.max(addHp, c.dapHeartsMax * 2.0F);
            float before = p.getAbsorptionAmount();
            float target = Math.min(maxHp, before + addHp);
            int amp = Math.max(0, (int)Math.ceil(target / 4.0F) - 1);
            int ticks = Math.max(1, c.dapHeartsSeconds) * 20;
            p.removeStatusEffect(StatusEffects.ABSORPTION);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, ticks, amp, false, true));
            p.setAbsorptionAmount(target);
            if (p.getWorld() instanceof ServerWorld w) {
                w.spawnParticles(
                    ParticleTypes.WAX_ON, p.getX(), p.getY() + 1.9, p.getZ(), 4 + (int)(target / 2.0F), 0.3, 0.15, 0.3, 0.02
                );
                w.playSound(
                    null,
                    p.getX(),
                    p.getY(),
                    p.getZ(),
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SoundCategory.PLAYERS,
                    0.5F,
                    0.9F + Math.min(1.0F, target / 20.0F)
                );
            }
        }
    }
}
