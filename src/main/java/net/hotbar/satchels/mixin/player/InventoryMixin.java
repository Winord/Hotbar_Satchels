package net.hotbar.satchels.mixin.player;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * satchel's contents when it's active, so the satchel's 6 slots behave like an extension of
 * the hotbar.
 * <p>
 * Regression note (post-launch bugfix): the client-side "deselect on pick" hook that used to live
 * here was removed. In 1.21.1 it was narrowly scoped to {@code setPickedItem} (F-key/middle-click
 * pick, the only thing that wrote {@code selected} outside normal navigation). In 26.1,
 * {@code setSelectedSlot(int)} became the *universal* write point for every selection path —
 * scroll wheel, number keys, server sync, pick-item alike (confirmed via a full-jar bytecode
 * scan: {@code Minecraft}, {@code MouseHandler}, {@code ClientPacketListener},
 * {@code ServerGamePacketListenerImpl} and {@code Inventory} itself all call it directly). A
 * generic hook on that method fired the "deactivate satchel" logic on *any* selection change,
 * including a player simply scrolling onto one of the satchel's own hotbar slots — which
 * incorrectly closed the satchel instead of just selecting that slot. Since pick-item is now
 * fully server-authoritative (see {@code ServerGamePacketListenerImplMixin}, which already has
 * the correctly-scoped server-side twin of this check), the client no longer needs — or can
 * correctly perform — this detection on its own; it now just waits for the server's
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

    // 26.1: renamed from getSelected() to getSelectedItem() (confirmed via javap on the real
    // merged jar). Also: Inventory#getDestroySpeed(BlockState) no longer exists at all — that
    // logic moved to Player#getDestroySpeed(BlockState), and its bytecode calls
    // this.inventory.getSelectedItem() directly. So once this override is retargeted, the
    // satchel's selected item is picked up automatically by Player's destroy-speed calc — the
    // old separate satchels$getDestroySpeed mixin (targeting a method that no longer exists) is
    // gone; it's not needed anymore, not just moved.
    @ModifyReturnValue(method = "getSelectedItem", at = @At("RETURN"))
    public ItemStack satchels$getSelected(ItemStack original) {
        SatchelData satchelData = SatchelData.get(player);
        if (satchelData.isActive() && satchelData.isSlotInSatchel(selected)) {
            int satchelIndex = satchelData.convertToSatchelIndex(selected);
            return satchelData.getSatchelInventory().getItem(satchelIndex);
        }
        return original;
    }

    @Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V", at = @At("HEAD"), cancellable = true)
    public void satchels$placeItemBackInInventory(ItemStack stack, boolean update, CallbackInfo ci) {
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

    // 26.1: clearOrCountMatchingItems's signature and internals changed completely (confirmed
    // via javap -c) — it's now (Predicate, int, Container), and internally does exactly 3 calls:
    // ContainerHelper.clearOrCountMatchingItems(this, ...), then (container param, ...), then
    // the ItemStack overload on the menu's carried/cursor stack. The old @WrapOperation targeted
    // the *second* occurrence of the Container-overload call by ordinal, which in 1.21.1 was
    // some "extra compartment" pass; in the new bytecode ordinal=1 lands on the caller-supplied
    // Container param instead (e.g. the crafting grid, for the /clear command's craft-slots
    // arg) — not satchel-relevant at all, so wrapping that specific call is the wrong target now.
    // Switched to injecting at RETURN and adding the satchel's extra clearing on top of whatever
    // the vanilla method already cleared — same net effect, but doesn't depend on the internal
    // call structure staying stable.
    @Inject(method = "clearOrCountMatchingItems", at = @At("RETURN"), cancellable = true)
    public void satchels$clearOrCountMatchingItems(Predicate<ItemStack> predicate, int i, Container container, CallbackInfoReturnable<Integer> cir) {
        int cleared = cir.getReturnValue();
        boolean bl = i == 0;
        SatchelData satchelData = SatchelData.get(player);

        int extraCleared = ContainerHelper.clearOrCountMatchingItems(satchelData.getSatchelInventory(), predicate, i - cleared, bl);

        ItemStack satchelSlot = satchelData.getSatchelSlotStack();
        extraCleared += ContainerHelper.clearOrCountMatchingItems(satchelSlot, predicate, i - cleared - extraCleared, bl);

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
