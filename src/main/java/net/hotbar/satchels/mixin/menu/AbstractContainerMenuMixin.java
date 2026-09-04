package net.hotbar.satchels.mixin.menu;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.hotbar.satchels.ModTags;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelEquipmentSlot;
import net.hotbar.satchels.content.satchel.SatchelInventory;
import net.hotbar.satchels.content.satchel.SatchelInventorySlot;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
@Debug(export = true)
@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {
    @Shadow
    @Final
    public NonNullList<Slot> slots;

    /**
     * Lets a satchel-for-satchel swap in {@link SatchelEquipmentSlot} go through even though
     * {@link Slot#mayPickup} on that slot normally forbids it whenever the equipped satchel's
     * own storage is non-empty (see {@code SatchelEquipmentSlot#mayPickup}) — that block exists
     * to stop the equipped stack from being *taken out* while it still holds items, not to stop
     * swapping it for a same/different-tier satchel. Requiring
     * {@code SatchelData#getSatchelInventory().isEmpty()} here on top of the tag check is the
     * bug fix: without it this override unconditionally bypassed the {@code mayPickup} guard for
     * any satchel-for-satchel swap, so replacing a non-empty equipped satchel with another one
     * silently dropped its contents (later resized away by
     * {@code SatchelData#updateTierFromStack}, which — like {@code SatchelInventory#resizeTo} —
     * assumes on the vanilla path what {@code AccessoriesCompat#canUnequipSatchel} already
     * guarantees on the Accessories path: a tier change only ever happens while empty).
     */
    @WrapOperation(
            method = "doClick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/Slot;mayPickup(Lnet/minecraft/world/entity/player/Player;)Z",
                    ordinal = 1
            )
    )
    private boolean satchels$allowSwappingSatchelEquipment(Slot slot, Player player, Operation<Boolean> original, @Local(ordinal = 0) ItemStack held, @Local(ordinal = 1) ItemStack contained) {
        if (original.call(slot, player)) return true;

        if (slot instanceof SatchelEquipmentSlot) {
            return held.is(ModTags.SATCHEL) && contained.is(ModTags.SATCHEL)
                    && SatchelData.get(player).getSatchelInventory().isEmpty();
        }

        return false;
    }

    @Definition(id = "p_150432_", local = @Local(type = int.class, ordinal = 1, argsOnly = true))
    @Expression("p_150432_ < 9")
    @ModifyExpressionValue(method = "doClick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean satchels$allowSwappingFromSatchelHotbar(boolean original, int to, int from, ClickType p_150433_, Player p_150434_) {
        return original || this.slots.get(from) instanceof SatchelInventorySlot;
    }

    @Definition(id = "SWAP", field = "Lnet/minecraft/world/inventory/ClickType;SWAP:Lnet/minecraft/world/inventory/ClickType;")
    @Expression("? == SWAP")
    @WrapOperation(method = "doClick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getItem(I)Lnet/minecraft/world/item/ItemStack;", ordinal = 0), slice = @Slice(from = @At("MIXINEXTRAS:EXPRESSION")))
    private ItemStack satchels$getFromSatchelHotbar(Inventory instance, int slotIndex, Operation<ItemStack> original, int p_150431_, int p_150432_, ClickType p_150433_, Player player) {
        SatchelData satchelData = SatchelData.get(player);
        Slot slot = this.slots.get(slotIndex);
        return slot instanceof SatchelInventorySlot ?
                satchelData.getSatchelInventory().getItem(slot.getContainerSlot()) :
                original.call(instance, slotIndex);
    }

    @Definition(id = "SWAP", field = "Lnet/minecraft/world/inventory/ClickType;SWAP:Lnet/minecraft/world/inventory/ClickType;")
    @Expression("? == SWAP")
    @WrapOperation(method = "doClick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setItem(ILnet/minecraft/world/item/ItemStack;)V"), slice = @Slice(from = @At("MIXINEXTRAS:EXPRESSION")))
    private void satchels$setToSatchelHotbar(Inventory instance, int slotIndex, ItemStack stack, Operation<Void> original, int p_150431_, int p_150432_, ClickType p_150433_, Player player) {
        SatchelData satchelData = SatchelData.get(player);
        Slot slot = this.slots.get(slotIndex);
        // Writes here go straight through Container#setItem, bypassing Slot#mayPlace entirely —
        // this is what let the number-key hotbar swap put a satchel inside itself (no
        // Accessories needed). Route through SatchelInventory#canPlaceItem (the same "no
        // satchel inside a satchel" rule the ordinary GUI path checks via
        // SatchelInventorySlot#mayPlace) so this raw path can't move a SATCHEL-tagged stack in.
        if (slot instanceof SatchelInventorySlot satchelSlot) {
            SatchelInventory inventory = satchelData.getSatchelInventory();
            if (inventory.canPlaceItem(satchelSlot.getContainerSlot(), stack)) inventory.setItem(satchelSlot.getContainerSlot(), stack);
        } else original.call(instance, slotIndex, stack);
    }
}