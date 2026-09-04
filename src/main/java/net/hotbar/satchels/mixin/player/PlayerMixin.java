package net.hotbar.satchels.mixin.player;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.hotbar.satchels.compat.SatchelsCompat;
import net.hotbar.satchels.content.satchel.IHaveSatchelData;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * Attaches {@link SatchelData} to every {@code Player} and wires it into save/load,
 * equipment-slot writes, death drops, curse-of-vanishing cleanup, and projectile selection.
 * <p>
 * {@code satchels$prioritizeSatchelProjectiles} returns the first matching projectile found in
 * the satchel directly, with no hook for other mods to intercept the choice — worth revisiting
 * if a companion mod ever needs to influence this selection too.
 */
@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements IHaveSatchelData {
    @Shadow
    @Final
    Inventory inventory;
    @Unique
    private final SatchelData satchels$satchelData = new SatchelData((Player) (Object) this);

    protected PlayerMixin(EntityType<? extends LivingEntity> p_20966_, Level p_20967_) {
        super(p_20966_, p_20967_);
    }

    @Override
    public SatchelData satchels$getSatchelData() {
        return satchels$satchelData;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    public void satchels$addAdditionalData(CompoundTag compoundTag, CallbackInfo ci) {
        CompoundTag tag = satchels$satchelData.serializeNBT(this.registryAccess());
        compoundTag.put(SatchelData.KEY_SATCHEL, tag);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    public void satchels$readAdditionalData(CompoundTag compoundTag, CallbackInfo ci) {
        satchels$satchelData.deserializeNBT(this.registryAccess(), compoundTag.getCompound(SatchelData.KEY_SATCHEL));
    }

    // Satchel Inventory Hooks
    @Inject(method = "setItemSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;set(ILjava/lang/Object;)Ljava/lang/Object;", ordinal = 0), cancellable = true)
    public void satchels$setSatchelSlotIfNeeded(EquipmentSlot equipmentSlot, ItemStack itemStack, CallbackInfo ci) {
        if (satchels$satchelData.isActive()) {
            if (!satchels$satchelData.isSlotInSatchel(this.inventory.selected)) return;
            int satchelIndex = satchels$satchelData.convertToSatchelIndex(this.inventory.selected);

            SatchelInventory satchelInventory = satchels$satchelData.getSatchelInventory();
            this.onEquipItem(equipmentSlot, satchelInventory.getItems().set(satchelIndex, itemStack), itemStack);
            ci.cancel();
        }
    }

    @Inject(method = "dropEquipment", at = @At("TAIL"))
    public void satchels$dropSatchelEquipment(CallbackInfo ci) {
        SatchelData satchelData = SatchelData.get((Player) (Object) this);
        if (!this.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            // Always drop the satchel's stored contents ourselves — nothing else knows
            // about them, regardless of which compat currently manages the equipped slot.
            satchelData.getSatchelInventory().dropAll(true);

            // The equipped-satchel *bag item* itself is a different story. Under
            // AccessoriesCompat, satchelData.satchelSlotStack is only a mirror of what's
            // actually equipped (see AccessoriesCompat#accessoryChangeMaybeSatchel) — the
            // real stack lives in AccessoriesCapability's own container, and Accessories
            // drops its equipped accessories on death itself. Dropping the mirrored copy
            // here too would duplicate the bag. Only VanillaCompat has no other system
            // backing the equipped slot (SatchelEquipmentSlot has no backing Container —
            // SatchelData is the sole source of truth there), so only that path needs us
            // to drop it manually. SatchelsCompat.VANILLA never loads while Accessories is
            // present, so this check alone is enough to tell the two paths apart.
            if (!SatchelsCompat.ACCESSORIES.isLoaded()) {
                ItemStack slotStack = satchelData.getSatchelSlotStack();
                if (!slotStack.isEmpty()) satchelData.getPlayer().drop(slotStack, true, false);
            }
        }
    }

    @Inject(method = "destroyVanishingCursedItems", at = @At("TAIL"))
    public void satchels$destroyVanishingCursedItems(CallbackInfo ci) {
        SatchelData satchelData = SatchelData.get((Player) (Object) this);
        for (int i = 0; i < satchelData.getSatchelInventory().getContainerSize(); i++) {
            ItemStack itemStack = satchelData.getSatchelInventory().getItem(i);
            if (!itemStack.isEmpty() && EnchantmentHelper.has(itemStack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                satchelData.getSatchelInventory().removeItemNoUpdate(i);
            }
        }

        ItemStack satchelStack = satchelData.getSatchelSlotStack();
        if (!satchelStack.isEmpty() && EnchantmentHelper.has(satchelStack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
            satchelData.setSatchelSlotStack(ItemStack.EMPTY);
        }
    }

    @Inject(method = "getProjectile", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ProjectileWeaponItem;getAllSupportedProjectiles()Ljava/util/function/Predicate;", shift = At.Shift.AFTER), cancellable = true)
    public void satchels$prioritizeSatchelProjectiles(ItemStack weapon, CallbackInfoReturnable<ItemStack> cir, @Local Predicate<ItemStack> supportedPredicate) {
        Player player = (Player) (Object) this;
        SatchelData satchelData = SatchelData.get(player);
        for (ItemStack item : satchelData.getSatchelInventory().getItems()) {
            if (supportedPredicate.test(item)) cir.setReturnValue(item);
        }
    }
}
