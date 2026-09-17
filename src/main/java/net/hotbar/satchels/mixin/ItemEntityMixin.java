package net.hotbar.satchels.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.hotbar.satchels.content.satchel.SatchelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ItemEntity.class)
public class ItemEntityMixin {
    @WrapOperation(method = "playerTouch", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    public boolean satchels$playerTouch(Inventory inventory, ItemStack stack, Operation<Boolean> original) {
        SatchelData satchelData = SatchelData.get(inventory.player);

        ItemStack preTriggerSnapshot = stack.copy();

        boolean success;
        if (satchelData.isActive()) {
            success = satchelData.getSatchelInventory().pickup(stack) || original.call(inventory, stack);
        } else {
            success = original.call(inventory, stack) || (satchelData.canAccess() && satchelData.getSatchelInventory().pickup(stack));
        }

        if (success && inventory.player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.INVENTORY_CHANGED.trigger(serverPlayer, serverPlayer.getInventory(), preTriggerSnapshot);
        }

        return success;
    }
}