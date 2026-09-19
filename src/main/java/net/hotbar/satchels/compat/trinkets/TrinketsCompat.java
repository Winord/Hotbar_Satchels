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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Trinkets Updated integration.
 * <p>
 * <b>Primary slot-compat on 26.2.</b> Archived for the whole 26.1.x cycle due to an upstream
 * slot-id/visual-position desync bug (see {@code satchels-port-decisions-26_1.md}); re-enabled
 * on 26.2 once {@code 4.1.0-rc.1+26.2} reworked that slot-id/visual-position split specifically
 * to prevent the desync (see {@code satchels-port-decisions-26_2.md} §2 for the verification
 * status). {@link net.hotbar.satchels.compat.ohmega.OhmegaCompat} is now only the fallback,
 * active solely when Trinkets isn't installed — see {@code SatchelsCompat}.
 * <p>
 * Equips/unequips the satchel through the {@code chest/satchel} trinket slot, prevents
 * unequipping while it has contents, plays the equip sound, drops the satchel's contents on
 * unequip, and tracks the dye tint per player. The slot is data-driven via
 * {@code data/trinkets/entities/player/chest/satchel.json} / {@code .../tags/item/chest/satchel.json}.
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

    private static final Logger LOGGER = LoggerFactory.getLogger("Satchels/Trinkets");

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

        var inventory = attachment.getInventory(SLOT_ID);
        if (inventory == null) return false;

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            TrinketSlotAccess access = inventory.getOrCreateSlotAccess(i);
            if (!TrinketSlotUtils.isSlotCompatible(access, stack)) continue;
            if (!TrinketSlotUtils.mayPlace(access, stack)) continue;

            ItemStack existing = access.get();
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

        Optional<TrinketSlotAccess> first = attachment.findFirst(s -> s.is(ModTags.SATCHEL));
        if (first.isPresent()) return first.get().get();

        // Fallback: cosmetic slot.
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

        Optional<TrinketSlotAccess> first = attachment.findFirst(s -> s.is(ModTags.SATCHEL));
        if (first.isPresent()) {
            return first.get().isVisible();
        }

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

        // DIAGNOSTIC (temporary): re-verify against the live TrinketAttachment before trusting
        // this event's `previous`/`current` args. If Trinkets fires a transient/incorrect
        // "changed" event (e.g. during its own attachment resync) while the satchel is in fact
        // still equipped, acting on the stale args here would wrongly dropAll() the satchel's
        // contents — which looks exactly like "items inside the satchel vanish" to the player,
        // even though nothing ever touched the satchel's own storage slots or their indices.
        TrinketAttachment attachmentNow = TrinketsApi.getAttachment(player);
        boolean stillEquippedNow = attachmentNow != null
                && attachmentNow.findFirst(s -> s.is(ModTags.SATCHEL)).isPresent();
        if (stillEquippedNow) {
            LOGGER.warn(
                    "Satchels/Trinkets: equipmentChangedMaybeSatchel reported an unequip for {} "
                            + "(previous={}, current={}, slot={}) but a live TrinketAttachment "
                            + "re-check still finds a satchel equipped — treating as a spurious "
                            + "event and skipping dropAll(). If you see this log, please report it "
                            + "together with what you were doing at the time.",
                    player.getName().getString(), previous, current, slot);
            return;
        }

        satchelTints.remove(player);

        SatchelData satchelData = SatchelData.get(player);
        satchelData.getSatchelInventory().dropAll(false);
        if (satchelData.isActive()) satchelData.setActive(false, true);
        satchelData.setSatchelSlotStack(ItemStack.EMPTY);
        satchelData.sendData();
    }
}