package net.hotbar.satchels.compat.trinkets;

import dev.yumi.commons.TriState;
import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.TrinketSlotUtils;
import eu.pb4.trinkets.api.TrinketsApi;
import eu.pb4.trinkets.api.event.TrinketCanUnequipCallback;
import eu.pb4.trinkets.api.event.TrinketEquipmentChangedCallback;
import net.hotbar.satchels.ModTags;
import net.hotbar.satchels.api.SatchelAccess;
import net.hotbar.satchels.compat.CompatEntrypoint;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Trinkets Updated integration (replaces {@code AccessoriesCompat} on 26.x).
 * <p>
 * Equips/unequips the satchel through the {@code chest/satchel} trinket slot,
 * prevents unequipping while it has contents, plays the equip sound, drops the
 * satchel's contents on unequip, and tracks the dye tint per player.
 * <p>
 * The slot is data-driven: {@code data/trinkets/entities/player/chest/satchel.json}
 * must be present in the mod's resources (group {@code chest}, slot name {@code satchel}).
 * Items are tagged via {@code data/trinkets/tags/item/chest/satchel.json}.
 * <p>
 * {@code TriState} here is {@code dev.yumi.commons.TriState} (a Trinkets Updated / Yumi
 * transitive dep), NOT {@code net.fabricmc.fabric.api.util.TriState}.
 */
public class TrinketsCompat implements CompatEntrypoint {
    /**
     * Trinkets slot id for our satchel: group=chest, slot=satchel.
     * Must match the JSON files under {@code data/trinkets/}.
     */
    public static final String SLOT_ID = "chest/satchel";

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

        TrinketCanUnequipCallback.EVENT.register(this::canUnequipSatchel);
        TrinketEquipmentChangedCallback.EVENT.register(this::equipmentChangedMaybeSatchel);
    }

    // -------------------------------------------------------------------------
    // Equip from hand
    // -------------------------------------------------------------------------

    public boolean equipSatchel(Player player, InteractionHand hand) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(player);
        if (attachment == null) return false;

        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || !stack.is(ModTags.SATCHEL)) return false;

        // Find the first available slot in chest/satchel group.
        var inventory = attachment.getInventory(SLOT_ID);
        if (inventory == null) return false;

        // Iterate through the inventory slots looking for an empty one.
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            TrinketSlotAccess access = inventory.getOrCreateSlotAccess(i);
            if (!TrinketSlotUtils.isSlotCompatible(access, stack)) continue;
            if (!TrinketSlotUtils.mayPlace(access, stack)) continue;

            ItemStack existing = access.get();
            // Place into the slot (swap if already occupied with a different item).
            access.set(stack.copy());
            player.setItemInHand(hand, existing.isEmpty() ? ItemStack.EMPTY : existing);
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Respawn — mirror satchel into SatchelData so SatchelInventory resizes correctly.
    // -------------------------------------------------------------------------

    public void onPlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        // Trinkets handles inventory copy across death/respawn itself; we only
        // need to ensure SatchelData.satchelSlotStack stays consistent.
        ItemStack equipped = getSatchelStack(newPlayer);
        if (!equipped.isEmpty()) {
            SatchelData.get(newPlayer).setSatchelSlotStack(equipped.copy());
        }
    }

    // -------------------------------------------------------------------------
    // Stack getters
    // -------------------------------------------------------------------------

    public int getSatchelTint(Player player) {
        return satchelTints.getOrDefault(player, -1);
    }

    public ItemStack getSatchelStack(Player player) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(player);
        if (attachment == null) return ItemStack.EMPTY;

        Optional<TrinketSlotAccess> first = attachment.findFirst(s -> s.is(ModTags.SATCHEL));
        return first.map(TrinketSlotAccess::get).orElse(ItemStack.EMPTY);
    }

    /**
     * Rendering-only: also checks cosmetic slots so a satchel worn purely cosmetically
     * is still drawn by {@code SatchelLayer}.
     */
    public ItemStack getSatchelVisualStack(Player player) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(player);
        if (attachment == null) return ItemStack.EMPTY;

        // Check functional slot first.
        Optional<TrinketSlotAccess> first = attachment.findFirst(s -> s.is(ModTags.SATCHEL));
        if (first.isPresent()) return first.get().get();

        // Fall back: cosmetic slot.
        var inventory = attachment.getInventory(SLOT_ID);
        if (inventory == null) return ItemStack.EMPTY;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            TrinketSlotAccess cosmetic = inventory.getCosmeticSlotAccess(i);
            if (cosmetic != null && cosmetic.isValid()) {
                ItemStack cosmeticStack = cosmetic.get();
                if (!cosmeticStack.isEmpty() && cosmeticStack.is(ModTags.SATCHEL)) {
                    return cosmeticStack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public boolean playerCanAccessSatchel(Player player) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(player);
        if (attachment == null) return false;
        return attachment.isEquipped(ModTags.SATCHEL);
    }

    public boolean playerSatchelIsVisible(Player player) {
        TrinketAttachment attachment = TrinketsApi.getAttachment(player);
        if (attachment == null) return false;

        // Check functional slots for visibility flag.
        Optional<TrinketSlotAccess> first = attachment.findFirst(s -> s.is(ModTags.SATCHEL));
        if (first.isPresent()) {
            return first.get().isVisible();
        }

        // Check cosmetic slots.
        var inventory = attachment.getInventory(SLOT_ID);
        if (inventory == null) return false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            TrinketSlotAccess cosmetic = inventory.getCosmeticSlotAccess(i);
            if (cosmetic != null && cosmetic.isValid()) {
                ItemStack cs = cosmetic.get();
                if (!cs.isEmpty() && cs.is(ModTags.SATCHEL)) return cosmetic.isVisible();
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Callbacks
    // -------------------------------------------------------------------------

    /** Prevents unequipping a satchel that still has items in it. */
    public TriState canUnequipSatchel(ItemStack stack, TrinketSlotAccess slot,
                                      LivingEntity entity, boolean canUnequipDefault) {
        if (!stack.is(ModTags.SATCHEL)) return TriState.DEFAULT;
        if (!(entity instanceof Player player)) return TriState.DEFAULT;

        SatchelData satchelData = SatchelData.get(player);
        boolean isEmpty = satchelData.getSatchelInventory().isEmpty();
        return isEmpty ? TriState.DEFAULT : TriState.FALSE;
    }

    /**
     * Called whenever a trinket slot changes. Mirrors equip/unequip of the satchel
     * into {@link SatchelData} so inventory sizing, tier tracking, and rendering stay
     * consistent.
     */
    public void equipmentChangedMaybeSatchel(ItemStack previous, ItemStack current,
                                             TrinketSlotAccess slot, LivingEntity entity) {
        if (!(entity instanceof Player player)) return;

        boolean satchelUnequipped = previous.is(ModTags.SATCHEL) && !current.is(ModTags.SATCHEL);

        if (current.is(ModTags.SATCHEL)) {
            satchelTints.put(player, DyedItemColor.getOrDefault(current, SatchelItem.DEFAULT_COLOR));
            if (!player.firstTick) SatchelItem.playEquipSound(player);

            // Mirror into SatchelData — drives tier tracking and inventory resizing.
            SatchelData.get(player).setSatchelSlotStack(current.copy());
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