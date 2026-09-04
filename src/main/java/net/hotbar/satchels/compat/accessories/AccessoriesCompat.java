package net.hotbar.satchels.compat.accessories;

import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.EquipmentChecking;
import io.wispforest.accessories.api.events.AccessoryChangeCallback;
import io.wispforest.accessories.api.events.CanUnequipCallback;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import io.wispforest.accessories.api.slot.SlotReference;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.hotbar.satchels.ModTags;
import net.hotbar.satchels.api.SatchelAccess;
import net.hotbar.satchels.compat.CompatEntrypoint;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelItem;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Accessories integration: equips/unequips the satchel through the {@code satchel}
 * accessory slot, prevents unequipping while it has contents, plays the equip sound, drops
 * the satchel's contents on unequip, and tracks the dye tint per player.
 * <p>
 * Equipping goes through a single {@link AccessoriesCapability#attemptToEquipAccessory}
 * call — since the satchel item is only tagged for the {@code satchel} slot
 * ({@code data/accessories/tags/item/satchel.json}), this automatically searches only that
 * slot's container, no separate loop over every accessory container is needed.
 */
public class AccessoriesCompat implements CompatEntrypoint {
    public Map<Player, Integer> satchelTints = new WeakHashMap<>();

    @Override
    public void initialize() {
        SatchelAccess.SATCHEL_EQUIP_CALLBACKS.add(this::equipSatchel);
        SatchelAccess.CAN_ACCESS_PREDICATES.add(this::playerCanAccessSatchel);
        SatchelAccess.IS_VISIBLE_PREDICATES.add(this::playerSatchelIsVisible);
        SatchelAccess.SATCHEL_STACK_GETTERS.add(this::getSatchelStack);
        SatchelAccess.SATCHEL_VISUAL_STACK_GETTERS.add(this::getSatchelVisualStack);
        SatchelAccess.SATCHEL_TINT_GETTERS.add(this::getSatchelTint);
        SatchelAccess.PLAYER_RESPAWN_CALLBACKS.add(this::onPlayerRespawn);

        CanUnequipCallback.EVENT.register(this::canUnequipSatchel);
        AccessoryChangeCallback.EVENT.register(this::accessoryChangeMaybeSatchel);
    }

    public boolean equipSatchel(Player player, InteractionHand hand) {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) return false;

        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || !stack.is(ModTags.SATCHEL)) return false;

        var result = capability.attemptToEquipAccessory(stack.copy(), true);
        if (result == null) return false;

        SlotReference reference = result.first();
        if (reference == null) return false;

        // attemptToEquipAccessory doesn't mutate the passed-in stack, so we clear it from
        // the player's hand ourselves.
        player.setItemInHand(hand, ItemStack.EMPTY);

        Optional<ItemStack> previous = result.second();
        previous.ifPresent(prev -> player.setItemInHand(hand, prev));

        return true;
    }

    public void onPlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        AccessoriesCapability capability = AccessoriesCapability.get(newPlayer);
        if (capability == null) return;

        SlotEntryReference entry = capability.getFirstEquipped(s -> s.is(ModTags.SATCHEL));
        if (entry == null) return;

        AccessoriesContainer container = entry.reference().slotContainer();
        if (container == null) return;

        container.getAccessories().setPreviousItem(entry.reference().slot(), entry.stack().copy());
    }

    public int getSatchelTint(Player player) {
        return satchelTints.getOrDefault(player, -1);
    }

    public ItemStack getSatchelStack(Player player) {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) return ItemStack.EMPTY;

        SlotEntryReference entry = capability.getFirstEquipped(s -> s.is(ModTags.SATCHEL));
        return entry != null ? entry.stack() : ItemStack.EMPTY;
    }

    /**
     * Rendering-only counterpart to {@link #getSatchelStack}: passes
     * {@link EquipmentChecking#COSMETICALLY_OVERRIDABLE} instead of the {@code ACCESSORIES_ONLY}
     * default, so a satchel worn purely in the cosmetic slot (no functional satchel equipped)
     * is still found here for {@code SatchelLayer} to draw. Still checks the functional slot
     * list first and only substitutes in each slot's cosmetic stack when present, matching
     * Accessories' cosmetic-override contract for every other accessory type.
     */
    public ItemStack getSatchelVisualStack(Player player) {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) return ItemStack.EMPTY;

        SlotEntryReference entry = capability.getFirstEquipped(s -> s.is(ModTags.SATCHEL), EquipmentChecking.COSMETICALLY_OVERRIDABLE);
        return entry != null ? entry.stack() : ItemStack.EMPTY;
    }

    public boolean playerCanAccessSatchel(Player player) {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) return false;

        return capability.isEquipped(s -> s.is(ModTags.SATCHEL));
    }

    /**
     * Uses {@code COSMETICALLY_OVERRIDABLE} rather than {@code ACCESSORIES_ONLY} — the "does
     * anything need drawing at all" half of the cosmetic-slot handling ({@link #getSatchelVisualStack}
     * supplies *what* to draw; this predicate, via {@code SatchelLayer}'s visibility gate,
     * decides *whether* to call it). With {@code ACCESSORIES_ONLY} a satchel worn purely in the
     * cosmetic slot could never be found here, since the functional slot is empty.
     */
    public boolean playerSatchelIsVisible(Player player) {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) return false;

        SlotEntryReference entry = capability.getFirstEquipped(
                s -> s.is(ModTags.SATCHEL), EquipmentChecking.COSMETICALLY_OVERRIDABLE);
        if (entry == null) return false;

        AccessoriesContainer container = entry.reference().slotContainer();
        if (container == null) return false;

        return container.shouldRender(entry.reference().slot());
    }

    public TriState canUnequipSatchel(ItemStack stack, SlotReference reference) {
        if (!stack.is(ModTags.SATCHEL)) return TriState.DEFAULT;

        if (!(reference.entity() instanceof Player player)) return TriState.DEFAULT;

        SatchelData satchelData = SatchelData.get(player);
        boolean isEmpty = satchelData.getSatchelInventory().isEmpty();
        return isEmpty ? TriState.DEFAULT : TriState.FALSE;
    }

    public void accessoryChangeMaybeSatchel(ItemStack prevStack, ItemStack currentStack,
                                            SlotReference reference, io.wispforest.accessories.api.events.SlotStateChange stateChange) {
        if (!(reference.entity() instanceof Player player)) return;

        boolean satchelUnequipped = prevStack.is(ModTags.SATCHEL) && !currentStack.is(ModTags.SATCHEL);

        if (currentStack.is(ModTags.SATCHEL)) {
            satchelTints.put(player, DyedItemColor.getOrDefault(currentStack, SatchelItem.DEFAULT_COLOR));
            if (!player.firstTick) SatchelItem.playEquipSound(player);

            // Mirror into SatchelData: this is what drives tier tracking/inventory resizing.
            // AccessoriesCapability's own container is the source of truth for *which stack is
            // equipped* on this path, but SatchelData.satchelSlotStack is still what
            // SatchelInventory sizing (and worn-model selection, SatchelLayer) reads.
            SatchelData.get(player).setSatchelSlotStack(currentStack.copy());
            return;
        }

        if (!satchelUnequipped) return;

        satchelTints.remove(player);

        SatchelData satchelData = SatchelData.get(player);
        satchelData.getSatchelInventory().dropAll(false);
        if (satchelData.isActive()) satchelData.setActive(false, true);
        satchelData.setSatchelSlotStack(ItemStack.EMPTY);
        satchelData.sendData();
    }
}