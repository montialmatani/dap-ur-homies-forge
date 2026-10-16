package com.cooptest.forge;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.potion.Potion;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Forge registers content through DeferredRegister instead of Fabric's direct Registry.register calls.
 * Entries keep the original "testcoop" namespace so worlds, sounds.json and lang keys line up with the Fabric mod.
 */
public final class CoopRegistries {
    public static final String NS = "testcoop";
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(RegistryKeys.SOUND_EVENT, NS);
    public static final DeferredRegister<StatusEffect> EFFECTS = DeferredRegister.create(RegistryKeys.STATUS_EFFECT, NS);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(RegistryKeys.ITEM, NS);
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(RegistryKeys.POTION, NS);

    private CoopRegistries() {}

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
        EFFECTS.register(modBus);
        ITEMS.register(modBus);
        POTIONS.register(modBus);
    }

    public static <T extends SoundEvent> T sound(Identifier id, T sound) {
        SOUNDS.register(id.getPath(), () -> sound);
        return sound;
    }

    public static <T extends StatusEffect> T effect(String path, T effect) {
        EFFECTS.register(path, () -> effect);
        return effect;
    }

    public static <T extends Item> T item(String path, T item) {
        ITEMS.register(path, () -> item);
        return item;
    }

    public static <T extends Potion> T potion(String path, T potion) {
        POTIONS.register(path, () -> potion);
        return potion;
    }
}
