package net.hotbar.satchels.mixin.player;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.CompoundTag;
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

    // 26.1: Entity#addAdditionalSaveData/readAdditionalSaveData no longer take a raw CompoundTag —
    // the actual descriptor the mixin engine reported at runtime is
    // (Lnet/minecraft/world/level/storage/ValueOutput;...)V / (...ValueInput;...)V. That part is
    // a hard fact straight from the loaded class, not a guess.
    //
    // What IS a hypothesis (not decompiled in this session — flagging per the port protocol):
    // ValueOutput#store(String, Codec<T>, T) / ValueInput#read(String, Codec<T>) returning
    // Optional<T>. This mirrors the real vanilla "ValueInput/ValueOutput" NBT-agnostic save
    // refactor and lets us keep bridging through CompoundTag (CompoundTag.CODEC is already used
    // elsewhere in SatchelData, e.g. its own equipped-stack serialization) rather than rewriting
    // SatchelData's whole (de)serializeNBT contract around these interfaces directly. If this
    // doesn't compile, the compiler error will show ValueOutput/ValueInput's *actual* member
    // names — send that back rather than let me guess again.
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

    // Satchel Inventory Hooks — see LivingEntityMixin#satchels$setSatchelSlotIfNeeded: setItemSlot
    // moved to LivingEntity in 26.1 (Player no longer overrides it), and its whole body changed
    // shape, so the injection moved there too. Kept the doc note here since this is the first
    // place someone reading PlayerMixin would look for it.

    // 26.1: dropEquipment gained a ServerLevel parameter (confirmed via javap — it's now
    // dropEquipment(ServerLevel), not a bare no-arg method). @Inject requires the handler's own
    // parameters (before CallbackInfo) to match the target's exactly, so the old zero-arg handler
    // is a signature mismatch that would fail to apply at class-load time — same failure mode as
    // the two originally-reported crashes, just not reached yet. Fixed by adding the ServerLevel
    // param and using it directly instead of the old this.level() instanceof pattern match.
    @Inject(method = "dropEquipment", at = @At("TAIL"))
    public void satchels$dropSatchelEquipment(net.minecraft.server.level.ServerLevel serverLevel, CallbackInfo ci) {
        SatchelData satchelData = SatchelData.get((Player) (Object) this);
        if (
                // 26.1: GameRules.getBoolean(GameRule<Boolean>) removed; confirmed via javap the
                // instance now exposes a generic <T> T get(GameRule<T>) instead.
                !serverLevel.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            // Always drop the satchel's stored contents ourselves — nothing else knows
            // about them, regardless of which compat currently manages the equipped slot.
            satchelData.getSatchelInventory().dropAll(true);

            // The equipped-satchel *bag item* itself is a different story. Under
            // TrinketsCompat, satchelData.satchelSlotStack is only a mirror of what's
            // actually equipped (see TrinketsCompat#equipmentChangedMaybeSatchel) — the
            // real stack lives in Trinkets' own TrinketAttachment/inventory, and Trinkets
            // drops its equipped trinkets on death itself. Dropping the mirrored copy
            // here too would duplicate the bag. Only VanillaCompat has no other system
            // backing the equipped slot (SatchelEquipmentSlot has no backing Container —
            // SatchelData is the sole source of truth there), so only that path needs us
            // to drop it manually. SatchelsCompat.VANILLA never loads while Trinkets Updated
            // is present, so this check alone is enough to tell the two paths apart.
            if (!SatchelsCompat.TRINKETS.isLoaded()) {
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