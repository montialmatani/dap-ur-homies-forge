package com.cooptest;

import com.cooptest.fabricshim.fabric.api.itemgroup.v1.ItemGroupEvents;
import com.cooptest.forge.CoopRegistries;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;

public class MahitoItems {
    public static final Item BLACK_HOOD = new Item(new Item.Settings().maxCount(1));
    public static Potion MAHITO_POTION;
    public static Potion TODO_POTION;

    public static void register() {
        CoopRegistries.item("black_hood", BLACK_HOOD);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> entries.add(BLACK_HOOD));
        MAHITO_POTION = CoopRegistries.potion("mahito_stuff", new Potion(new StatusEffectInstance(ModEffects.MAHITO, 1200, 0)));
        TODO_POTION = CoopRegistries.potion("todo_potion", new Potion(new StatusEffectInstance(ModEffects.TODO, 2400, 0)));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(content -> {
            content.add(createMahitoPotion());
            content.add(createTodoPotion());
        });
    }

    public static ItemStack createMahitoPotion() {
        return PotionUtil.setPotion(new ItemStack(Items.POTION), MAHITO_POTION);
    }

    public static ItemStack createTodoPotion() {
        return PotionUtil.setPotion(new ItemStack(Items.POTION), TODO_POTION);
    }
}
