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
 * 26.1: {@code setItemSlot(EquipmentSlot, ItemStack)} moved — {@code Player} no longer overrides
 * it at all (confirmed via {@code javap}: it only shows up on {@code LivingEntity} now), so a
 * {@code @Mixin(Player.class)} injection targeting it (as {@code PlayerMixin} used to) can't find
 * it — Mixin only matches methods actually declared in the bytecode of the class(es) named in
 * {@code @Mixin(...)}, not ones merely inherited.
 * <p>
 * The method's whole body changed shape too, which is why this is a separate {@code @At("HEAD")}
 * injection rather than the old inner-call target: decompiled the real 26.1 body and it's now a
 * single line, {@code this.onEquipItem(slot, this.equipment.set(slot, itemStack), itemStack);} —
 * equipment storage moved from a raw {@code NonNullList<ItemStack>} (indexed by ordinal, which is
 * what the old {@code NonNullList#set(int, Object)} injection target was hooking) to a typed
 * container keyed directly by {@code EquipmentSlot}. There's no longer an inner call to hook
 * into mid-method the same way; a plain cancellable {@code HEAD} injection does the same job
 * (redirect before vanilla's own assignment happens at all) and is more resilient to this body
 * changing shape again in the future.
 * <p>
 * Since this now targets {@code LivingEntity} — which every mob extends, not just players — this
 * mixin applies to all living entities at the bytecode level; the {@code instanceof Player} guard
 * below is what actually restricts the satchel-redirect behavior to players, same as before.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "setItemSlot", at = @At("HEAD"), cancellable = true)
    private void satchels$setSatchelSlotIfNeeded(EquipmentSlot equipmentSlot, ItemStack itemStack, CallbackInfo ci) {
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