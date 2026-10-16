package com.cooptest;

import com.cooptest.forge.CoopRegistries;
import net.minecraft.entity.effect.StatusEffect;

public class ModEffects {
    public static final StatusEffect MAHITO = new MahitoEffect();
    public static final StatusEffect TODO = new TodoEffect();

    public static void register() {
        CoopRegistries.effect("mahito", MAHITO);
        CoopRegistries.effect("todo", TODO);
    }
}
