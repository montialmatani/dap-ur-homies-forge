package com.cooptest.mixin;

import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.inventory.RecipeInputInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({CraftingResultSlot.class})
public class CraftingResultSlotMixin {
    @Shadow
    @Final
    private RecipeInputInventory input;

    @Unique
    private boolean isMahitoPotion(ItemStack stack) {
        if (!stack.isOf(Items.POTION)) {
            return false;
        }
        Potion potion = PotionUtil.getPotion(stack);
        if (potion == null || potion == Potions.EMPTY) {
            return false;
        }
        return Registries.POTION.getId(potion).toString().contains("mahito");
    }

    @Inject(
        method = {"onTakeItem"},
        at = {@At("HEAD")}
    )
    private void onTakeMahitoPotion(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (this.isMahitoPotion(stack)) {
            for (int i = 0; i < this.input.size(); i++) {
                this.input.setStack(i, ItemStack.EMPTY);
            }

            this.input.markDirty();
        }
    }
}
