package net.hotbar.satchels.mixin.player;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Prediction;
import net.hotbar.satchels.network.packets.ToggleSatchelPacketC2S;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * Redirects vanilla {@code Inventory} operations (selected-item lookup, destroy speed,
 * "place item back", pick-block, clearing/counting matching items) to also consider the
 * satchel's contents when it's active, so the satchel's slots behave like an extension of
 * the hotbar.
 * <p>
 * There is deliberately no client-side "deselect on pick" hook here: {@code setSelectedSlot(int)}
 * is the universal write point for every selection path (scroll wheel, number keys, server sync,
 * pick-item alike), so a generic hook on it can't distinguish "picked an item" from "scrolled
 * onto a satchel slot" and would incorrectly close the satchel on the latter. Pick-item's
 * satchel-priority logic is fully server-authoritative instead — see
 * {@code ServerGamePacketListenerImplMixin} — and the client just follows the server's
 * {@code SatchelStatusPacketS2C} like any other state sync.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow
    @Final
    public Player player;

    @Shadow
    public int selected;

    @Shadow
    public abstract int getSuitableHotbarSlot();

    @Shadow
    @Final
    public NonNullList<ItemStack> items;

    // Player#getDestroySpeed(BlockState) calls this.inventory.getSelectedItem() directly, so
    // the satchel's selected item is picked up automatically by the destroy-speed calc too —
    // no separate hook needed there.
    @ModifyReturnValue(method = "getSelectedItem", at = @At("RETURN"))
    public ItemStack satchels$getSelected(ItemStack original) {
        SatchelData satchelData = SatchelData.get(player);
        if (satchelData.isActive() && satchelData.isSlotInSatchel(selected)) {
            int satchelIndex = satchelData.convertToSatchelIndex(selected);
            return satchelData.getSatchelInventory().getItem(satchelIndex);
        }
        return original;
    }

    @Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)V", at = @At("HEAD"), cancellable = true)
    public void satchels$placeItemBackInInventory(ItemStack stack, boolean update, Prediction prediction, CallbackInfo ci) {
        SatchelData satchelData = SatchelData.get(player);
        if (!satchelData.canAccess()) return;
        if (satchelData.isActive() || !update) {
            boolean added = satchelData.getSatchelInventory().placeItemBackInInventory(stack);
            if (added) ci.cancel();
        }
    }

    @Inject(method = "removeFromSelected", at = @At("HEAD"), cancellable = true)
    public void satchels$removeFromSelected(boolean fullStack, CallbackInfoReturnable<ItemStack> cir) {
        SatchelData satchelData = SatchelData.get(player);
        if (satchelData.isActive() && satchelData.isSlotInSatchel(selected)) {
            int slot = satchelData.convertToSatchelIndex(selected);
            ItemStack satchelSelected = satchelData.getSatchelInventory().getItem(slot);
            if (satchelSelected.isEmpty()) cir.setReturnValue(ItemStack.EMPTY);
            else {
                cir.setReturnValue(satchelData.getSatchelInventory().removeItem(slot, fullStack ? satchelSelected.getCount() : 1));
            }
        }
    }

    @Inject(method = "clearOrCountMatchingItems", at = @At("RETURN"), cancellable = true)
    public void satchels$clearOrCountMatchingItems(Predicate<ItemStack> predicate, boolean simulate, int i, Container container, CallbackInfoReturnable<Integer> cir) {
        int cleared = cir.getReturnValue();
        SatchelData satchelData = SatchelData.get(player);

        int extraCleared = ContainerHelper.clearOrCountMatchingItems(satchelData.getSatchelInventory(), predicate, i - cleared, simulate);

        ItemStack satchelSlot = satchelData.getSatchelSlotStack();
        extraCleared += ContainerHelper.clearOrCountMatchingItems(satchelSlot, predicate, i - cleared - extraCleared, simulate);

        satchelData.setSatchelSlotStack(satchelSlot);
        if (satchelSlot.isEmpty()) {
            satchelData.getSatchelInventory().dropAll(false);
            if (satchelData.isActive()) {
                satchelData.setActive(false, true);
                satchelData.sendData();
            }
        }

        cir.setReturnValue(cleared + extraCleared);
    }

    @Inject(method = "removeItem(Lnet/minecraft/world/item/ItemStack;)V", at = @At(value = "TAIL"))
    public void satchels$removeItem(ItemStack stack, CallbackInfo ci) {
        SatchelData.get(player)
                .getSatchelInventory()
                .removeItem(stack);
    }

    @Inject(method = "pickSlot", at = @At("HEAD"), cancellable = true)
    public void satchels$putIntoSatchelIfActive(int slot, CallbackInfo ci) {
        SatchelData data = SatchelData.get(player);
        SatchelInventory satchelInventory = data.getSatchelInventory();
        if (!data.isActive()) return;

        int inventorySuitable = getSuitableHotbarSlot();
        int satchelSuitable = satchelInventory.getFreeSlot();

        if (!data.isSlotInSatchel(inventorySuitable) && (inventorySuitable < satchelSuitable + data.getHotbarOffset() || satchelSuitable == -1)) return;
        if (satchelSuitable == -1 && inventorySuitable != -1 && !data.isSlotInSatchel(inventorySuitable)) return;

        if (satchelSuitable != -1) selected = satchelSuitable + data.getHotbarOffset();
        int satchelSelected = data.convertToSatchelIndex(selected);

        ItemStack held = satchelInventory.getItem(satchelSelected);

        satchelInventory.setItem(satchelSelected, items.get(slot));
        items.set(slot, held);
        ci.cancel();
    }
}
