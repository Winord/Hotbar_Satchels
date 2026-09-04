package net.hotbar.satchels.mixin.client.screen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.NonNullList;
import net.hotbar.satchels.content.satchel.SatchelEquipmentSlot;
import net.hotbar.satchels.content.satchel.SatchelInventorySlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Prevents satchel slots from being duplicated into the creative-inventory tab-switch logic.
 * Uses {@code CreativeModeInventoryScreen.SlotWrapper} and its {@code target} field, both
 * opened up via {@code satchels.accesswidener}.
 */
@Mixin(CreativeModeInventoryScreen.class)
public class CreativeModeInventoryScreenMixin {
    @WrapOperation(method = "selectTab", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;add(Ljava/lang/Object;)Z", ordinal = 2))
    public boolean satchels$dontAddSatchelsSlots(NonNullList<?> list, Object object, Operation<Boolean> original) {
        if (object instanceof CreativeModeInventoryScreen.SlotWrapper slotWrapper) {
            if (slotWrapper.target instanceof SatchelInventorySlot) return false;
            if (slotWrapper.target instanceof SatchelEquipmentSlot) return false;
        }
        return original.call(list, object);
    }
}
