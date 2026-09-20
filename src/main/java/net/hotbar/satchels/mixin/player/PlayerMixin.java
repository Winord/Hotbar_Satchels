package net.hotbar.satchels.mixin.player;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.hotbar.satchels.compat.SatchelsCompat;
import net.hotbar.satchels.content.satchel.IHaveSatchelData;
import net.hotbar.satchels.content.satchel.SatchelData;
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
    public void satchels$addAdditionalData(net.minecraft.world.level.storage.ValueOutput output, CallbackInfo ci) {
        CompoundTag tag = satchels$satchelData.serializeNBT(this.registryAccess());
        output.store(SatchelData.KEY_SATCHEL, CompoundTag.CODEC, tag);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    public void satchels$readAdditionalData(net.minecraft.world.level.storage.ValueInput input, CallbackInfo ci) {
        CompoundTag tag = input.read(SatchelData.KEY_SATCHEL, CompoundTag.CODEC).orElseGet(CompoundTag::new);
        satchels$satchelData.deserializeNBT(this.registryAccess(), tag);
    }

    // Equipment-slot write redirect lives in LivingEntityMixin#satchels$setSatchelSlotIfNeeded —
    // setItemSlot is declared there, not on Player, as of 26.1.

    @Inject(method = "dropEquipment", at = @At("TAIL"))
    public void satchels$dropSatchelEquipment(net.minecraft.server.level.ServerLevel serverLevel, CallbackInfo ci) {
        SatchelData satchelData = SatchelData.get((Player) (Object) this);
        if (!serverLevel.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            // Always drop the satchel's stored contents ourselves — nothing else knows about them.
            satchelData.getSatchelInventory().dropAll(true);

            // The equipped bag item itself is different: under a slot-compat module (Ohmega,
            // Trinkets), satchelSlotStack is only a mirror — the real stack lives in that mod's
            // own inventory, which drops its own equipped items on death. Only VanillaCompat has
            // no backing system of its own, so only that path needs a manual drop here.
            if (SatchelsCompat.VANILLA.isLoaded()) {
                ItemStack slotStack = satchelData.getSatchelSlotStack();

                if (!slotStack.isEmpty()) satchelData.getPlayer().drop(slotStack, true, Prediction.SERVER_ONLY);
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
