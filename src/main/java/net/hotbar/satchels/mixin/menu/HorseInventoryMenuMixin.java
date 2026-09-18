package net.hotbar.satchels.mixin.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.hotbar.satchels.SatchelsCommonConfig;
import net.hotbar.satchels.api.MenuWithSatchel;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelEquipmentSlot;
import net.hotbar.satchels.content.satchel.SatchelInventorySlot;
import net.hotbar.satchels.compat.SatchelsCompat;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(HorseInventoryMenu.class)
public abstract class HorseInventoryMenuMixin extends AbstractContainerMenu {
    public HorseInventoryMenuMixin(MenuType<?> p_40115_, int p_40116_) {
        super(p_40115_, p_40116_);
    }

    @Shadow
    @Final
    private Container horseContainer;

    @SuppressWarnings("Convert2MethodRef")
    @Inject(method = "<init>", at = @At("TAIL"))
    public void satchels$addMoreSlots(int p_39656_, Inventory inventory, Container p_39658_, AbstractHorse p_39659_, int p_352384_, CallbackInfo ci) {
        SatchelData satchelData = SatchelData.get(inventory.player);

        MenuWithSatchel.addInventorySlots(satchelData, s -> this.addSlot(s), 8, 170, 18);

        // The equipment-slot indicator ("show me the equipped/carried satchel here") used to be
        // wired only into InventoryMenuMixin. Horse's location key is always the fixed
        // "minecraft:horse" (unlike a generic menu, it has no real MenuType to look up — see
        // AbstractContainerMenuMixin's javadoc — so it's hardcoded here rather than resolved),
        // which is also why this can't reuse the client-only SatchelMenuLocation helper: this
        // mixin runs on the server too, and that class is stripped from server jars.
        if (SatchelsCompat.VANILLA.isLoaded() && SatchelsCommonConfig.isAllowed(ResourceLocation.withDefaultNamespace("horse"))) {
            this.addSlot(new SatchelEquipmentSlot(inventory.player, 0, 0));
        }
    }

    /**
     * Ported from the 26.1.x {@code AbstractMountInventoryMenuMixin} fix: shift-clicking a
     * {@code SatchelInventorySlot} inside the horse menu must move the item to the player's
     * own inventory/hotbar, not fall through to vanilla's {@code quickMoveStack}, which knows
     * nothing about satchel slots and was silently eating the stack.
     */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void satchels$quickMoveFromSatchel(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        Slot slot = this.slots.get(slotIndex);
        if (!(slot instanceof SatchelInventorySlot) || !slot.hasItem()) return;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        int playerInvStart = 2 + this.horseContainer.getContainerSize();
        int playerInvEnd = playerInvStart + 27;
        int hotbarEnd = playerInvEnd + 9;

        if (!this.moveItemStackTo(stack, playerInvStart, hotbarEnd, false)) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();

        cir.setReturnValue(stack.getCount() == original.getCount() ? ItemStack.EMPTY : original);
    }
}
