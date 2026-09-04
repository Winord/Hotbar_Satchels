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

        // Mirrors AccessoriesCompat#canUnequipSatchel on the vanilla path: right-clicking a
        // satchel to equip it always went through SatchelEquipmentSlot#setByPlayer unconditionally,
        // silently overwriting (and dropping the contents of) a non-empty worn satchel. There is
        // no equivalent CanUnequipCallback gate here since this path bypasses Slot#mayPickup
        // entirely, so the check has to happen before the swap is attempted at all.
        if (current.is(ModTags.SATCHEL) && !SatchelData.get(player).getSatchelInventory().isEmpty()) return false;

        ItemStack held = player.getItemInHand(hand).copy();

        // Write the previously-equipped satchel back into the hand BEFORE swapping the equip
        // slot. SatchelEquipmentSlot#set (via slot.setByPlayer below) resizes SatchelData's
        // satchel inventory to the newly-equipped item's tier immediately (updateTierFromStack),
        // which can grow the range of hotbar indices SatchelData#isSlotInSatchel treats as
        // "inside the satchel" (e.g. diamond/golden's 6 slots -> netherite's 9). If the hand's
        // hotbar index falls into that newly-grown range, PlayerMixin#satchels$setSatchelSlotIfNeeded
        // redirects any setItemSlot write landing there into the satchel's own storage instead
        // of the real hotbar slot. Doing this write first, while the satchel is still sized to
        // its old (smaller-or-equal) tier, guarantees the hand's index isn't yet considered part
        // of the satchel — so `current` lands in the real hotbar slot as intended instead of
        // being stashed inside the very satchel just equipped, with the original held stack
        // left behind uncleared as a duplicate.
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