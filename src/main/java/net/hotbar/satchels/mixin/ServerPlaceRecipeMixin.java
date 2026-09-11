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
 * 26.1: {@code ServerPlaceRecipe} was rebuilt from the ground up — decompiled the real class to
 * confirm the new shape (not guessed). There is no more instance {@code recipeClicked} method:
 * the recipe-book click now goes through {@code AbstractCraftingMenu#handlePlacement}, which
 * calls the new {@code public static placeRecipe(CraftingMenuAccess, int, int, List, List,
 * Inventory, RecipeHolder, boolean, boolean)} factory method directly — {@code ServerPlaceRecipe}
 * itself is now only constructed internally by that static method (private constructor).
 * <p>
 * {@code stackedContents} is gone as a field entirely; item availability is built as a local
 * {@code StackedItemContents} inside {@code placeRecipe} itself, right before
 * {@code menu.fillCraftSlotsStackedContents(availableItems)} — captured below via MixinExtras
 * {@code @Local} since it's no longer a shadowable field.
 * <p>
 * {@code moveItemToGrid} also changed shape: {@code (Slot, Holder<Item>, int)} instead of
 * {@code (Slot, ItemStack, int)}, since the caller now only knows which ingredient
 * {@code Holder<Item>} matched, not a concrete stack — mirrors
 * {@code Inventory#findSlotMatchingCraftingIngredient(Holder<Item>, ItemStack)}, which is why
 * {@link SatchelInventory#findSlotMatchingCraftingIngredient} was added to match it.
 */
@Mixin(ServerPlaceRecipe.class)
public class ServerPlaceRecipeMixin {

    // 26.1: `inventory` is still a private final instance field on ServerPlaceRecipe (confirmed
    // via decompile) — Mixin can shadow private fields regardless of Java-level visibility, this
    // is unrelated to the `stackedContents` field having been removed entirely (see below).
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
        // Deliberate behavior note vs. the pre-port code: the 1.21.1 version returned a hardcoded
        // -1 here (both when the satchel is inaccessible and when it has no match), rather than
        // `original`. -1 is vanilla's sentinel for "give up entirely" in the while-loop caller
        // (see the decompiled placeRecipe above), so blindly returning -1 would wrongly abort
        // vanilla's own loop even in the case where vanilla's own inventory search had already
        // partially succeeded this round (original >= 0, meaning "still need more, keep going").
        // Falling back to `original` here only changes behavior when it was previously masking a
        // non-negative in-progress vanilla result — flagging in case this diverges from an
        // intentional choice in the pre-port code that isn't obvious from the diff alone.
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
