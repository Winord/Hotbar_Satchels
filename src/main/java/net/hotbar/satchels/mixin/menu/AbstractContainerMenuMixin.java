package net.hotbar.satchels.mixin.menu;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.hotbar.satchels.ModTags;
import net.hotbar.satchels.SatchelsCommonConfig;
import net.hotbar.satchels.compat.SatchelsCompat;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelEquipmentSlot;
import net.hotbar.satchels.content.satchel.SatchelInventory;
import net.hotbar.satchels.content.satchel.SatchelInventorySlot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Debug(export = true)
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Shadow
    @Final
    public NonNullList<Slot> slots;

    @Shadow
    public abstract Slot addSlot(Slot slot);

    @Shadow
    public abstract MenuType<?> getType();

    /**
     * Generalizes the {@code SatchelEquipmentSlot} indicator (the "put/take the satchel here"
     * slot, previously wired only into {@code InventoryMenuMixin}, so only shown in the
     * Survival GUI) to every {@code allowed_menus} screen.
     * <p>
     * Injects into {@code AbstractContainerMenu#addStandardInventorySlots(Container, int, int)}
     * rather than raw {@code addSlot} — confirmed via the decompiled 26.1.2 jar that every
     * vanilla menu which lays out the player's own inventory+hotbar row (chest, furnace,
     * hopper, anvil, horse, {@code InventoryMenu} itself, ...) funnels through this one
     * protected method, unlike 1.21.1 where no such shared helper existed and the equivalent
     * fix had to hook {@code addSlot} directly and detect the player {@link Inventory} by hand.
     * TAIL is used (rather than HEAD) purely so the slot is appended after the 36 slots this
     * call itself adds, keeping slot ordering predictable; it has no effect on the {@code
     * container instanceof Inventory} check below, since {@code container} is a method
     * parameter, not something the TAIL/HEAD choice can change.
     * <p>
     * {@code InventoryMenu} is excluded: it already gets its slot from
     * {@code InventoryMenuMixin}'s own hand-placed {@code addSlot} call (which also renders
     * correctly without any repositioning, unlike the generic case below), so adding it again
     * here would duplicate it. {@code HorseInventoryMenu} (and other {@code
     * AbstractMountInventoryMenu} subclasses) previously had no equipment slot in 26.1.X at
     * all — this hook is what adds it there for the first time, matching the 1.21.1 behavior.
     * <p>
     * {@code HorseInventoryMenu} needs a special-cased {@code location}: unlike menus opened
     * through the normal registered-{@code MenuType} flow, {@code AbstractMountInventoryMenu}'s
     * constructor calls {@code super(null, containerId)} (confirmed in the decompiled jar), so
     * {@code getType()} throws {@code UnsupportedOperationException} and
     * {@code BuiltInRegistries.MENU.getKey(...)} can never resolve it — the same reason the
     * client-only {@code SatchelMenuLocation.resolve} special-cases it instead of relying on
     * the registry lookup. This class can't reuse that method directly: it's
     * {@code @Environment(EnvType.CLIENT)}-only (references client screen classes) while this
     * mixin targets {@code AbstractContainerMenu}, which is loaded on the dedicated server too.
     * <p>
     * The new slot is added at a placeholder {@code (0, 0)} — {@code AbstractContainerMenuMixin}
     * has no idea what texture a client screen will use, so the real position is computed
     * per-frame client-side by {@code AbstractContainerScreenMixin} via
     * {@code SatchelEquipmentSlot#updateBase}.
     */
    @Inject(method = "addStandardInventorySlots", at = @At("TAIL"))
    private void satchels$addEquipmentSlotToGenericMenus(Container container, int left, int top, CallbackInfo ci) {
        if (!SatchelsCompat.VANILLA.isLoaded()) return;
        if (!(container instanceof Inventory playerInventory)) return;

        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (self instanceof InventoryMenu) return;

        Identifier location = self instanceof HorseInventoryMenu
                ? Identifier.withDefaultNamespace("horse")
                : satchels$safeMenuTypeKey();
        if (location == null || !SatchelsCommonConfig.isAllowed(location)) return;

        if (this.slots.stream().anyMatch(s -> s instanceof SatchelEquipmentSlot)) return;

        this.addSlot(new SatchelEquipmentSlot(playerInventory.player, 0, 0));
    }

    @Unique
    @Nullable
    private Identifier satchels$safeMenuTypeKey() {
        try {
            return BuiltInRegistries.MENU.getKey(this.getType());
        } catch (Exception ignored) {
            return null;
        }
    }

    @WrapOperation(
            method = "doClick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/Slot;mayPickup(Lnet/minecraft/world/entity/player/Player;)Z",
                    ordinal = 1
            )
    )
    private boolean satchels$allowSwappingSatchelEquipment(Slot slot, Player player, Operation<Boolean> original, @Local(ordinal = 0) ItemStack held, @Local(ordinal = 1) ItemStack contained) {
        if (original.call(slot, player)) return true;

        if (slot instanceof SatchelEquipmentSlot) {
            return held.is(ModTags.SATCHEL) && contained.is(ModTags.SATCHEL)
                    && SatchelData.get(player).getSatchelInventory().isEmpty();
        }

        return false;
    }

    @Definition(id = "p_150432_", local = @Local(type = int.class, ordinal = 1, argsOnly = true))
    @Expression("p_150432_ < 9")
    @ModifyExpressionValue(method = "doClick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean satchels$allowSwappingFromSatchelHotbar(boolean original, int to, int from, ContainerInput p_150433_, Player p_150434_) {
        return original || this.slots.get(from) instanceof SatchelInventorySlot;
    }

    @Definition(id = "SWAP", field = "Lnet/minecraft/world/inventory/ContainerInput;SWAP:Lnet/minecraft/world/inventory/ContainerInput;")
    @Expression("? == SWAP")
    @WrapOperation(method = "doClick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;getItem(I)Lnet/minecraft/world/item/ItemStack;", ordinal = 0), slice = @Slice(from = @At("MIXINEXTRAS:EXPRESSION")))
    private ItemStack satchels$getFromSatchelHotbar(Inventory instance, int slotIndex, Operation<ItemStack> original, int p_150431_, int p_150432_, ContainerInput p_150433_, Player player) {
        SatchelData satchelData = SatchelData.get(player);
        Slot slot = this.slots.get(slotIndex);
        return slot instanceof SatchelInventorySlot ?
                satchelData.getSatchelInventory().getItem(slot.getContainerSlot()) :
                original.call(instance, slotIndex);
    }

    @Definition(id = "SWAP", field = "Lnet/minecraft/world/inventory/ContainerInput;SWAP:Lnet/minecraft/world/inventory/ContainerInput;")
    @Expression("? == SWAP")
    @WrapOperation(method = "doClick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setItem(ILnet/minecraft/world/item/ItemStack;)V"), slice = @Slice(from = @At("MIXINEXTRAS:EXPRESSION")))
    private void satchels$setToSatchelHotbar(Inventory instance, int slotIndex, ItemStack stack, Operation<Void> original, int p_150431_, int p_150432_, ContainerInput p_150433_, Player player) {
        SatchelData satchelData = SatchelData.get(player);
        Slot slot = this.slots.get(slotIndex);
        if (slot instanceof SatchelInventorySlot satchelSlot) {
            SatchelInventory inventory = satchelData.getSatchelInventory();
            if (inventory.canPlaceItem(satchelSlot.getContainerSlot(), stack)) inventory.setItem(satchelSlot.getContainerSlot(), stack);
        } else original.call(instance, slotIndex, stack);
    }
}