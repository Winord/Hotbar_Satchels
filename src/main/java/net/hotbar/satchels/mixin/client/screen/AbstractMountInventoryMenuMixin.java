package net.hotbar.satchels.mixin.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractMountInventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.hotbar.satchels.content.satchel.SatchelInventorySlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMountInventoryMenu.class)
public abstract class AbstractMountInventoryMenuMixin extends AbstractContainerMenu {
    public AbstractMountInventoryMenuMixin(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    @Shadow
    @Final
    protected Container mountContainer;

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void satchels$quickMoveFromSatchel(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        Slot slot = this.slots.get(slotIndex);
        if (!(slot instanceof SatchelInventorySlot) || !slot.hasItem()) return;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        int playerInvStart = 2 + this.mountContainer.getContainerSize();
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
