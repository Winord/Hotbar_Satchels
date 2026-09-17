package net.hotbar.satchels.compat.vanilla;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.hotbar.satchels.ModTags;
import net.hotbar.satchels.api.SatchelAccess;
import net.hotbar.satchels.compat.CompatEntrypoint;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelEquipmentSlot;
import net.hotbar.satchels.content.satchel.SatchelItem;

/**
 * Registers the vanilla satchel-access predicates/getters: canAccess and isVisible are
 * driven by whether a satchel is equipped in the {@link SatchelEquipmentSlot}, resolved
 * through {@link SatchelData#getSatchelSlotStack()}.
 */
public class VanillaCompat implements CompatEntrypoint {
    @Override
    public void initialize() {
        SatchelAccess.SATCHEL_EQUIP_CALLBACKS.add(this::equipSatchel);
        SatchelAccess.CAN_ACCESS_PREDICATES.add(this::canAccessSatchel);
        SatchelAccess.IS_VISIBLE_PREDICATES.add(this::isSatchelVisible);
        SatchelAccess.SATCHEL_STACK_GETTERS.add(this::getSatchel);
        SatchelAccess.SATCHEL_TINT_GETTERS.add(this::getSatchelTint);
    }

    private boolean equipSatchel(Player player, InteractionHand hand) {
        SatchelEquipmentSlot slot = (SatchelEquipmentSlot) player.inventoryMenu.slots.stream()
                .filter(p -> p instanceof SatchelEquipmentSlot)
                .findFirst()
                .orElse(null);

        if (slot == null) return false;

        ItemStack current = slot.getItem();

        // Mirrors the compat modules' canUnequipSatchel check on the vanilla path: this path
        // bypasses Slot#mayPickup entirely, so a non-empty worn satchel has to be blocked here
        // before the swap, or it'd silently overwrite (and drop the contents of) the old one.
        if (current.is(ModTags.SATCHEL) && !SatchelData.get(player).getSatchelInventory().isEmpty()) return false;

        ItemStack held = player.getItemInHand(hand).copy();

        // Writes the previously-equipped satchel back into the hand BEFORE swapping the equip
        // slot. slot.setByPlayer below resizes SatchelData's inventory to the new item's tier
        // immediately, which can grow the "inside the satchel" hotbar index range (e.g. 6 slots
        // -> 9) — if the hand's index falls into that newly-grown range, PlayerMixin would
        // redirect this write into the satchel's own storage instead of the real hotbar slot.
        // Doing the write first, while still sized to the old tier, avoids that.
        player.setItemInHand(hand, current.copy());
        slot.setByPlayer(held, current);
        return true;
    }

    private ItemStack getSatchel(Player player) {
        return SatchelData.get(player).getSatchelSlotStack();
    }

    private boolean canAccessSatchel(Player player) {
        return getSatchel(player).is(ModTags.SATCHEL);
    }

    private int getSatchelTint(Player player) {
        ItemStack satchel = getSatchel(player);
        if (satchel.isEmpty()) return -1;
        return DyedItemColor.getOrDefault(getSatchel(player), SatchelItem.DEFAULT_COLOR);
    }

    private boolean isSatchelVisible(Player player) {
        return !getSatchel(player).isEmpty();
    }
}