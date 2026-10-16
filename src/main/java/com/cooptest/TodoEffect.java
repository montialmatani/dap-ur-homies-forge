package com.cooptest;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectCategory;

public class TodoEffect extends StatusEffect {
    public TodoEffect() {
        super(StatusEffectCategory.field_18271, 3381759);
    }

    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return false;
    }

    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
    }
}
