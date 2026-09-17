package net.hotbar.satchels.mixin.player;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code setItemSlot(EquipmentSlot, ItemStack)} is declared on {@code LivingEntity}, not
 * {@code Player}, and as of 26.1 has a single unified body,
 * {@code this.onEquipItem(slot, this.equipment.set(slot, itemStack), itemStack)}, with no
 * per-slot branching left to hook mid-method — hence the cancellable {@code @At("HEAD")}
 * injection here, targeting {@code LivingEntity} (every mob, not just players; the
 * {@code instanceof Player} guard below restricts the actual redirect).
 * <p>
 * <b>The explicit {@code MAINHAND} check is required, not incidental:</b> without it, this
 * injection fires for every equipment slot, so right-clicking a chestplate/elytra out of a
 * satchel slot gets redirected into the satchel's storage instead of actually equipping, and
 * the following hand-clear write (also routed through {@code setItemSlot} now) wipes that same
 * satchel index back to empty — the item vanishes instead of equipping.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "setItemSlot", at = @At("HEAD"), cancellable = true)
    private void satchels$setSatchelSlotIfNeeded(EquipmentSlot equipmentSlot, ItemStack itemStack, CallbackInfo ci) {
        if (equipmentSlot != EquipmentSlot.MAINHAND) return;
        if (!(((Object) this) instanceof Player player)) return;

        SatchelData satchelData = SatchelData.get(player);
        if (!satchelData.isActive()) return;

        int selected = player.getInventory().selected;
        if (!satchelData.isSlotInSatchel(selected)) return;
        int satchelIndex = satchelData.convertToSatchelIndex(selected);

        SatchelInventory satchelInventory = satchelData.getSatchelInventory();
        player.onEquipItem(equipmentSlot, satchelInventory.getItems().set(satchelIndex, itemStack), itemStack);
        ci.cancel();
    }
}