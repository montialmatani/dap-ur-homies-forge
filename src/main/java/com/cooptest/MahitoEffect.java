package com.cooptest;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectCategory;

public class MahitoEffect extends StatusEffect {
    public MahitoEffect() {
        super(StatusEffectCategory.HARMFUL, 10040012);
    }

    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
    }
}
