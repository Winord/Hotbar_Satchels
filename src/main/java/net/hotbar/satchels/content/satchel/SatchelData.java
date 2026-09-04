package net.hotbar.satchels.content.satchel;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.hotbar.satchels.ModSounds;
import net.hotbar.satchels.ModTags;
import net.hotbar.satchels.api.SatchelAccess;
import net.hotbar.satchels.client.SatchelsClientConfig;
import net.hotbar.satchels.network.packets.SatchelSlotUpdatePacketS2C;
import net.hotbar.satchels.network.packets.SatchelStatusPacketS2C;
import net.hotbar.satchels.util.NbtSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Per-player satchel state: the satchel's own inventory (3/6/9 slots depending on the equipped
 * tier — see {@link SatchelTier}), the "equipped satchel" item stack (worn slot, separate from
 * what's inside the storage slots), the hotbar offset, and the open/closed flag.
 * <p>
 * The equipped-satchel stack is stored directly on this object rather than as a separate
 * attachment type, since {@code SatchelData} is already a unique per-player mixin field (see
 * {@code PlayerMixin}) — it's serialized together with the rest of the state. Changes are
 * pushed to any client observing the player (needed for rendering the worn-satchel layer via
 * {@code SatchelLayer}) through {@link #syncSatchelSlotToObservers()} and
 * {@code SatchelSlotUpdatePacketS2C}, sent to everyone in {@code PlayerLookup.tracking(...)}
 * plus the player themself.
 */
public class SatchelData implements NbtSerializable<CompoundTag> {
    public static final String KEY_SATCHEL = "satchels:satchel_data";
    private static final String KEY_ACTIVE = "Active";
    private static final String KEY_INVENTORY = "Inventory";
    private static final String KEY_SLOT_ITEM = "SlotItem";

    private final Player player;

    private final SatchelInventory satchelInventory;

    private int hotbarOffset;
    private boolean active;
    private ItemStack satchelSlotStack = ItemStack.EMPTY;

    /**
     * Tier of the currently (or most recently) equipped satchel — {@code null} until a
     * satchel is equipped for the first time. Drives {@link #satchelInventory}'s size; see
     * {@link #updateTierFromStack(ItemStack)}.
     */
    private SatchelTier currentTier;

    public SatchelData(Player player) {
        this.player = player;
        this.satchelInventory = new SatchelInventory(this);
        this.hotbarOffset = 0;
        // Tier (and so slot-start) isn't known until a satchel is actually equipped — see
        // updateTierFromStack, which sets it correctly once that happens.
    }

    public static SatchelData get(Player player) {
        return ((IHaveSatchelData) player).satchels$getSatchelData();
    }

    public void copyFrom(SatchelData data) {
        List<ItemStack> original = data.satchelInventory.getItems();

        // Resize first: the target may still be at its zero-slot default (e.g. respawn
        // without keepInventory, where setSatchelSlotStack — and the resize it triggers —
        // is skipped) while the source already has a real tier-sized inventory.
        this.satchelInventory.resizeTo(original.size());
        this.currentTier = data.currentTier;

        for (int i = 0; i < original.size(); i++) satchelInventory.setItem(i, original.get(i).copy());
    }

    /**
     * Sends the owning client everything needed to rebuild an accurate client-side mirror of
     * this {@code SatchelData} right after their {@code LocalPlayer} gets recreated: initial
     * login, respawn, or a dimension change (all three go through
     * {@code ClientboundRespawnPacket} client-side — see
     * {@code ServerPlayerMixin#satchels$onChangeDimension}'s javadoc). {@link #sendData()}
     * alone only restores the open/closed flag; without also resending the equipped stack, the
     * fresh client-side mirror keeps {@code currentTier == null} (a 0-slot
     * {@code satchelInventory}) until *something else* happens to independently retrigger
     * {@code setSatchelSlotStack} client-side (any live equip-slot change does this normally
     * during play — that's the only path that was updating it before this method existed).
     * <p>
     * Until that first retrigger, {@code SatchelInventorySlot#isActive()}/{@code mayPlace()}'s
     * live tier-bounds check (see that class) treats every satchel slot as out of range — even
     * though the actual stored items already arrived correctly and on schedule via the
     * ordinary {@code InventoryMenu} content sync, since that menu's slot *list* doesn't depend
     * on any of this. The symptom is the satchel row rendering as if empty right after
     * (re)joining, until the player takes something out or puts something in — which happens to
     * fire that equip-change event and fix the tier out of band. Calling this here closes that
     * gap by proactively sending the real equipped stack (and so the real tier) as part of the
     * same resync, instead of waiting on an unrelated interaction to do it by accident.
     * <p>
     * Also sends a full inventory snapshot via {@link #sendInventoryToClient()} so the hotbar
     * overlay can render item icons even when no container menu is open (the vanilla
     * {@code InventoryMenu} broadcastChanges sync only runs while a menu is open).
     */
    public void resyncToClient() {
        this.sendData();
        this.syncSatchelSlotToObservers();
        this.sendInventoryToClient();
    }

    /**
     * Sends the full contents of {@link #satchelInventory} to the owning client as a
     * {@code SatchelInventorySyncPacketS2C} snapshot.
     * <p>
     * Called on join/respawn (via {@link #resyncToClient()}) and whenever the satchel is
     * toggled active, so the hotbar overlay always has an up-to-date item list even when
     * no container menu is open to run vanilla's slot-tracking sync.
     */
    public void sendInventoryToClient() {
        if (!(this.player instanceof ServerPlayer serverPlayer)) return;
        ServerPlayNetworking.send(serverPlayer,
                new net.hotbar.satchels.network.packets.SatchelInventorySyncPacketS2C(
                        this.satchelInventory.getItems().stream()
                                .map(net.minecraft.world.item.ItemStack::copy)
                                .collect(java.util.stream.Collectors.toList())
                ));
    }

    public void sendData() {
        if (!(this.player instanceof ServerPlayer serverPlayer)) throw new AssertionError("sendData should only be called on the server");
        ServerPlayNetworking.send(serverPlayer, new SatchelStatusPacketS2C(this.active));
    }

    public boolean canAccess() {
        return SatchelAccess.CAN_ACCESS_PREDICATES.stream().anyMatch(p -> p.test(player));
    }

    // region Equipped satchel slot (worn item, separate from the 6 storage slots)
    @NotNull
    public ItemStack getSatchelSlotStack() {
        return satchelSlotStack;
    }

    public boolean isSatchelSlotItemValid(@NotNull ItemStack stack) {
        return stack.is(ModTags.SATCHEL);
    }

    /** Replaces the equipped-satchel item stack and syncs the change to observing clients. */
    public void setSatchelSlotStack(@NotNull ItemStack stack) {
        this.satchelSlotStack = stack;
        this.updateTierFromStack(stack);
        this.syncSatchelSlotToObservers();
    }

    /**
     * Resizes {@link #satchelInventory} to match the newly-equipped stack's tier. Safe to call
     * unconditionally: {@code AccessoriesCompat}'s {@code CanUnequipCallback} handler already
     * guarantees a satchel can only be swapped for a different-tier one while empty, so a
     * resize here never has contents to preserve or drop. An empty stack (unequip) is a no-op.
     */
    private void updateTierFromStack(@NotNull ItemStack stack) {
        if (!(stack.getItem() instanceof SatchelItem satchelItem)) return;

        this.currentTier = satchelItem.getTier();
        this.satchelInventory.resizeTo(this.currentTier.getSlotCount());

        // Runtime isLocalPlayer() check to reach into client-only config from shared code (this
        // class runs on both sides). Once the local player's tier is known, apply — and sync
        // to the server — that tier's persisted hotbar slot-start.
        if (player.isLocalPlayer()) {
            SatchelsClientConfig.applyPersistedOffsetForTier(this, this.currentTier);
        }
    }

    /** The tier of the currently (or most recently) equipped satchel, or {@code null} if none has been equipped yet. */
    public SatchelTier getCurrentTier() {
        return currentTier;
    }

    public void syncSatchelSlotToObservers() {
        if (!(this.player instanceof ServerPlayer serverPlayer)) return;

        SatchelSlotUpdatePacketS2C packet = new SatchelSlotUpdatePacketS2C(serverPlayer.getId(), this.satchelSlotStack.copy());
        ServerPlayNetworking.send(serverPlayer, packet);
        for (ServerPlayer observer : PlayerLookup.tracking(serverPlayer)) {
            ServerPlayNetworking.send(observer, packet);
        }
    }
    // endregion

    // region Utilities
    public boolean isSlotInSatchel(int slot) {
        int offset = this.getHotbarOffset();
        return (offset <= slot) && (slot < offset + this.satchelInventory.getContainerSize());
    }

    public int convertToSatchelIndex(int slot) {
        return slot - this.getHotbarOffset();
    }
    // endregion

    // region Getters & Setters
    public Player getPlayer() {
        return player;
    }

    public SatchelInventory getSatchelInventory() {
        return satchelInventory;
    }

    public int getHotbarOffset() {
        return hotbarOffset;
    }

    public void setHotbarOffset(int hotbarOffset) {
        this.hotbarOffset = hotbarOffset;

        player.inventoryMenu.slots.forEach(s -> {
            if (s instanceof SatchelInventorySlot ss) ss.updateX();
        });

        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.slots.forEach(s -> {
                if (s instanceof SatchelInventorySlot ss) ss.updateX();
            });
        }
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean to, boolean audible) {
        this.active = to;

        if (!audible) return;
        float pitch = 0.9f + (player.getRandom().nextFloat() / 5);

        Vec3 soundPos = player.position();
        if (to) {
            player.level().playSound(null,
                    soundPos.x, soundPos.y, soundPos.z,
                    ModSounds.SATCHEL_OPEN.get(), SoundSource.PLAYERS,
                    1, pitch
            );
        } else {
            player.level().playSound(null,
                    soundPos.x, soundPos.y, soundPos.z,
                    ModSounds.SATCHEL_CLOSE.get(), SoundSource.PLAYERS,
                    1, pitch
            );
        }
    }
    // endregion

    // region Serialization
    @Override
    @NotNull
    public CompoundTag serializeNBT(@NotNull HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_ACTIVE, active);

        CompoundTag inventory = this.satchelInventory.serializeNBT(provider);
        tag.put(KEY_INVENTORY, inventory);

        if (!satchelSlotStack.isEmpty()) {
            tag.put(KEY_SLOT_ITEM, satchelSlotStack.save(provider, new CompoundTag()));
        }

        return tag;
    }

    @Override
    public void deserializeNBT(@NotNull HolderLookup.Provider provider, @NotNull CompoundTag tag) {
        this.active = tag.getBoolean(KEY_ACTIVE);

        // Equipped stack first: this resizes satchelInventory to the right tier (via
        // updateTierFromStack) *before* the inventory contents below are loaded into it —
        // loading order matters, otherwise slots past the default zero-size would be silently
        // dropped by SatchelInventory#deserializeNBT's bounds check.
        if (tag.contains(KEY_SLOT_ITEM)) {
            ItemStack.parse(provider, tag.getCompound(KEY_SLOT_ITEM)).ifPresent(stack -> {
                this.satchelSlotStack = stack;
                this.updateTierFromStack(stack);
            });
        }

        this.satchelInventory.deserializeNBT(provider, tag.getCompound(KEY_INVENTORY));
    }
    // endregion
}
