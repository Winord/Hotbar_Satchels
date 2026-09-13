package net.hotbar.satchels.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.hotbar.satchels.content.satchel.SatchelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {
    @WrapOperation(method = "playerTouch", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    public boolean satchels$playerTouch(Inventory inventory, ItemStack stack, Operation<Boolean> original) {
        SatchelData satchelData = SatchelData.get(inventory.player);
        ItemStack pickedUpKind = stack.copy(); // знімок ДО pickup(), бо pickup() спустошує stack

        boolean result = satchelData.isActive()
                ? satchelData.getSatchelInventory().pickup(stack) || original.call(inventory, stack)
                : original.call(inventory, stack) || (satchelData.canAccess() && satchelData.getSatchelInventory().pickup(stack));

        // Сатчел — окремий Container, тому vanilla-листенер (ServerPlayer#2), що фільтрує
        // slot.container == player.getInventory(), ніколи не побачить цю зміну сам.
        if (result && inventory.player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.INVENTORY_CHANGED.trigger(serverPlayer, serverPlayer.getInventory(), pickedUpKind);
        }

        return result;
    }
}
