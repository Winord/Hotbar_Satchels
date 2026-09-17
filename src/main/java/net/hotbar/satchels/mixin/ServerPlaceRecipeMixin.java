package net.hotbar.satchels.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * {@code ServerPlaceRecipe} has no instance {@code recipeClicked} method any more: the
 * recipe-book click goes through {@code AbstractCraftingMenu#handlePlacement}, which calls a
 * static {@code placeRecipe(...)} factory method directly. {@code stackedContents} is no
 * longer a shadowable field — item availability is a local {@code StackedItemContents} inside
 * {@code placeRecipe}, captured below via MixinExtras {@code @Local}.
 * <p>
 * {@code moveItemToGrid} takes {@code (Slot, Holder<Item>, int)}, not {@code (Slot, ItemStack,
 * int)} — the caller only knows which ingredient {@code Holder<Item>} matched, not a concrete
 * stack, mirroring {@code Inventory#findSlotMatchingCraftingIngredient}; see
 * {@link SatchelInventory#findSlotMatchingCraftingIngredient}.
 */
@Mixin(ServerPlaceRecipe.class)
public class ServerPlaceRecipeMixin {

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private Inventory inventory;

    @Inject(
            method = "placeRecipe",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/recipebook/ServerPlaceRecipe$CraftingMenuAccess;fillCraftSlotsStackedContents(Lnet/minecraft/world/entity/player/StackedItemContents;)V",
                    shift = At.Shift.AFTER
            )
    )
    private static void satchels$fillWithSatchelContents(
            ServerPlaceRecipe.CraftingMenuAccess menu,
            int gridWidth,
            int gridHeight,
            List inputGridSlots,
            List slotsToClear,
            Inventory inventory,
            RecipeHolder recipe,
            boolean useMaxItems,
            boolean allowDroppingItemsToClear,
            CallbackInfoReturnable<RecipeBookMenu.PostPlaceAction> cir,
            @Local StackedItemContents availableItems
    ) {
        SatchelData satchelData = SatchelData.get(inventory.player);
        if (satchelData.canAccess()) satchelData.getSatchelInventory().fillStackedContents(availableItems);
    }

    @ModifyReturnValue(method = "moveItemToGrid", at = @At(value = "RETURN", ordinal = 0))
    private int satchels$searchSatchel(int original, Slot targetSlot, Holder<Item> itemInInventory, int count) {
        // Falls back to `original` (not a hardcoded -1) when the satchel is inaccessible or has
        // no match: -1 is vanilla's sentinel to abort the whole placeRecipe loop, which would
        // wrongly cut off a still-in-progress vanilla search (original >= 0, "keep going").
        SatchelData data = SatchelData.get(this.inventory.player);

        if (!data.canAccess()) return original;
        SatchelInventory satchelInventory = data.getSatchelInventory();

        ItemStack itemInTargetSlot = targetSlot.getItem();
        int s = satchelInventory.findSlotMatchingCraftingIngredient(itemInInventory, itemInTargetSlot);
        if (s == -1) {
            return original;
        }

        ItemStack found = satchelInventory.getItem(s);
        ItemStack taken = count < found.getCount()
                ? satchelInventory.removeItem(s, count)
                : satchelInventory.removeItemNoUpdate(s);
        int takenCount = taken.getCount();

        if (itemInTargetSlot.isEmpty()) {
            targetSlot.set(taken);
        } else {
            itemInTargetSlot.grow(takenCount);
        }

        return count - takenCount;
    }
}
