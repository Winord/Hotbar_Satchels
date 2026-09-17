package net.hotbar.satchels.content.satchel;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import net.hotbar.satchels.ModTags;
import net.hotbar.satchels.util.NbtSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The satchel storage container — 3/6/9 slots depending on the equipped tier (see
 * {@link SatchelTier}), 0 until a satchel is first equipped. Implements
 * {@link NbtSerializable}&lt;{@link CompoundTag}&gt; for save/load, matching the
 * {@code Container} contract used for NBT persistence elsewhere in the codebase.
 */
public class SatchelInventory implements Container, NbtSerializable<CompoundTag>, StackedContentsCompatible {
    private final String KEY_ITEMS = "Items";
    private final String KEY_SLOT = "Slot";

    private final SatchelData parent;
    private List<ItemStack> items;

    /** No satchel equipped yet — zero slots until {@link #resizeTo(int)} is called. */
    public SatchelInventory(SatchelData parent) {
        this(parent, 0);
    }

    public SatchelInventory(SatchelData parent, int size) {
        this.parent = parent;
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    public SatchelInventory copy(SatchelData parent) {
        SatchelInventory copied = new SatchelInventory(parent, this.items.size());
        for (int i = 0; i < this.items.size(); i++) {
            copied.setItem(i, this.items.get(i).copy());
        }

        return copied;
    }

    /**
     * Resizes the backing list to {@code newSize} — used when the equipped satchel's tier
     * changes. Always safe to call: a tier swap is only possible while the old satchel is
     * empty, so this never has contents to preserve or drop.
     */
    public void resizeTo(int newSize) {
        if (newSize == this.items.size()) return;
        this.items = NonNullList.withSize(newSize, ItemStack.EMPTY);
    }

    // region Container
    @Override
    public int getContainerSize() { return items.size(); }

    /**
     * Guards every index-based {@link Container} accessor against a stale {@link Slot} left
     * over from a menu that was open when {@link #resizeTo(int)} shrank {@link #items}: an
     * out-of-range index is treated as "nothing there" instead of throwing
     * {@link IndexOutOfBoundsException}, making the phantom slot inert until the menu reopens.
     */
    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < items.size();
    }

    @Override
    @NotNull
    public ItemStack getItem(int slot) {
        return isValidSlot(slot) ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        if (isValidSlot(slot)) items.set(slot, stack);
    }

    @Override
    @NotNull
    public ItemStack removeItem(int slot, int amount) {
        return isValidSlot(slot) && !this.items.get(slot).isEmpty() ? ContainerHelper.removeItem(items, slot, amount) : ItemStack.EMPTY;
    }

    @Override
    @NotNull
    public ItemStack removeItemNoUpdate(int slot) {
        if (!isValidSlot(slot)) return ItemStack.EMPTY;
        ItemStack removed = this.items.get(slot);
        this.items.set(slot, ItemStack.EMPTY);
        return removed;
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    @Override
    public boolean isEmpty() {
        return this.items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public void setChanged() {
        this.parent.getPlayer().getInventory().setChanged();
        // Fires "satchel_full" when every slot of a Netherite Satchel holds a full stack
        // (server-side only). Non-stackable items (maxStackSize == 1) count as full at 1.
        Player player = this.parent.getPlayer();
        SatchelTier tier = this.parent.getCurrentTier();
        if (tier == SatchelTier.NETHERITE
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && !items.isEmpty()
                && items.stream().allMatch(s -> !s.isEmpty() && s.getCount() >= s.getMaxStackSize())) {
            net.hotbar.satchels.ModCriteria.triggerSatchelFull(serverPlayer);
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.isWithinEntityInteractionRange(this.parent.getPlayer(), player.entityInteractionRange());
    }

    /**
     * No satchel may ever hold another satchel — the single point of truth for that rule,
     * covering every insertion path (GUI clicks, shift-click, hotbar-key swap, hoppers) without
     * each needing its own tag check.
     */
    @Override
    public boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
        return isValidSlot(slot) && !stack.is(ModTags.SATCHEL);
    }
    // endregion Container

    // region Inventory Parity
    public void removeItem(ItemStack item) {
        for (int i = 0; i < this.items.size(); i++) {
            if (this.items.get(i) == item) {
                this.items.set(i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * A satchel item must never end up inside any satchel's storage (including its own),
     * even though {@link #canPlaceItem} isn't consulted on this path: this writes straight
     * into {@link #items}, bypassing the {@code Slot#mayPlace} machinery every other insertion
     * path goes through. Without this guard, walking over a dropped satchel while another is
     * active on the hotbar would pull it straight into storage. Returning {@code false} here
     * sends the caller back to the plain {@code Inventory#add(ItemStack)} fallback.
     */
    public boolean pickup(ItemStack stack) {
        if (stack.is(ModTags.SATCHEL)) return false;

        Inventory inventory = this.parent.getPlayer().getInventory();

        int offset = parent.getHotbarOffset();
        int right = parent.getHotbarOffset() + this.items.size();

        List<ItemStack> kindaHotbar = new ArrayList<>();
        for (int i = 0; i < offset; i++) kindaHotbar.add(inventory.getItem(i)); // uncovered left side of hotbar
        kindaHotbar.addAll(this.items);
        for (int i = right; i < 9; i++) kindaHotbar.add(inventory.getItem(i)); // uncovered right side of hotbar

        boolean success = addToInventory(stack, kindaHotbar);

        for (int i = 0; i < offset; i++) inventory.items.set(i, kindaHotbar.get(i)); // uncovered left side of hotbar
        for (int i = offset; i < right; i++) this.items.set(i - offset, kindaHotbar.get(i));
        for (int i = right; i < 9; i++) inventory.items.set(i, kindaHotbar.get(i));

        return success;
    }

    public boolean addToInventory(ItemStack ins, List<ItemStack> items) {
        int availableSlot = getSlotWithRemainingSpace(ins, items);
        if (availableSlot != -1) {
            addAt(availableSlot, ins, items);
        } else {
            for (int i = 0; i < items.size(); i++) {
                addAt(i, ins, items);
                if (ins.getCount() == 0) return true;
            }
            return false;
        }
        if (ins.getCount() > 0) return addToInventory(ins, items);
        return true;
    }

    public void addAt(int slot, ItemStack ins, List<ItemStack> items) {
        ItemStack original = items.get(slot);
        if (original.isEmpty()) {
            items.set(slot, ins.copyAndClear());
            items.get(slot).setPopTime(5);
        } else if (stackCanFitMore(original, ins)) {
            int inserted = Math.min(original.getMaxStackSize() - original.getCount(), ins.getCount());
            ins.shrink(inserted);

            original.setCount(original.getCount() + inserted);
            original.setPopTime(5);
        }
    }

    public void dropAll(boolean died) {
        for (int i = 0; i < items.size(); i++) {
            ItemStack itemStack = items.get(i);
            if (!itemStack.isEmpty()) {
                this.parent.getPlayer().drop(itemStack, died, !died);
                items.set(i, ItemStack.EMPTY);
            }
        }
    }

    public boolean placeItemBackInInventory(ItemStack inserted) {
        while (!inserted.isEmpty()) {
            int slot = this.getSlotWithRemainingSpace(inserted, items);
            if (slot == -1) {
                slot = this.getFreeSlot();
            }

            if (slot == -1) break;

            int j = inserted.getMaxStackSize() - this.getItem(slot).getCount();
            this.addAt(slot, inserted.split(j), items);
        }
        return inserted.isEmpty();
    }

    public int getSlotWithRemainingSpace(ItemStack inserted, List<ItemStack> items) {
        int selected = getSelectedSlot();
        if (selected != -1 && stackCanFitMore(items.get(selected), inserted)) return selected;

        for (int i = 0; i < items.size(); i++) {
            ItemStack here = items.get(i);
            if (this.stackCanFitMore(here, inserted)) return i;
        }
        return -1;
    }

    public boolean stackCanFitMore(ItemStack original, ItemStack inserted) {
        return !original.isEmpty() &&
                ItemStack.isSameItemSameComponents(original, inserted) &&
                original.isStackable() &&
                original.getCount() < this.getMaxStackSize(original);
    }

    public int getFreeSlot() {
        for (int i = 0; i < this.items.size(); i++) {
            if (this.items.get(i).isEmpty()) return i;
        }
        return -1;
    }

    private int getSelectedSlot() {
        int invSelected = this.parent.getPlayer().getInventory().selected;
        if (this.parent.isSlotInSatchel(invSelected)) return this.parent.convertToSatchelIndex(invSelected);
        return -1;
    }

    public int findSlotMatchingUnusedItem(ItemStack searchingFor) {
        for (int i = 0; i < this.items.size(); i++) {
            ItemStack found = this.items.get(i);
            if (!found.isEmpty()
                    && ItemStack.isSameItemSameComponents(searchingFor, found)
                    && !found.isDamaged()
                    && !found.isEnchanted()
                    && !found.has(DataComponents.CUSTOM_NAME)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Mirrors {@code Inventory#findSlotMatchingCraftingIngredient(Holder<Item>, ItemStack)} —
     * the recipe-book auto-craft pipeline matches by {@code Holder<Item>} rather than a
     * concrete stack. Same "usable for crafting" criteria as {@link #findSlotMatchingUnusedItem}.
     */
    public int findSlotMatchingCraftingIngredient(net.minecraft.core.Holder<net.minecraft.world.item.Item> item, ItemStack existingItem) {
        for (int i = 0; i < this.items.size(); i++) {
            ItemStack found = this.items.get(i);
            if (found.isEmpty()
                    || !found.is(item)
                    || found.isDamaged()
                    || found.isEnchanted()
                    || found.has(DataComponents.CUSTOM_NAME)) {
                continue;
            }
            if (!existingItem.isEmpty() && !ItemStack.isSameItemSameComponents(existingItem, found)) {
                continue;
            }
            return i;
        }

        return -1;
    }

    public int findSlotMatchingItem(ItemStack searchingFor) {
        for (int i = 0; i < this.items.size(); i++) {
            ItemStack found = this.items.get(i);
            if (!found.isEmpty() && ItemStack.isSameItemSameComponents(searchingFor, found)) {
                return i;
            }
        }

        return -1;
    }
    // endregion

    // region Serialization
    @Override
    @NotNull
    public CompoundTag serializeNBT(@NotNull HolderLookup.Provider provider) {
        ListTag listTag = new ListTag();
        net.minecraft.nbt.NbtOps nbtOps = net.minecraft.nbt.NbtOps.INSTANCE;

        for (int i = 0; i < this.items.size(); i++) {
            ItemStack slotContent = this.items.get(i);
            if (!slotContent.isEmpty()) {
                final int slot = i; // effectively-final capture for the lambda below
                ItemStack.CODEC
                    .encodeStart(provider.createSerializationContext(nbtOps), slotContent)
                    .resultOrPartial(e -> net.minecraft.util.Util.logAndPauseIfInIde("SatchelInventory save: " + e))
                    .ifPresent(encoded -> {
                        if (encoded instanceof CompoundTag itemTag) {
                            itemTag.putInt(KEY_SLOT, slot);
                            listTag.add(itemTag);
                        } else {
                            CompoundTag wrapper = new CompoundTag();
                            wrapper.putInt(KEY_SLOT, slot);
                            wrapper.put("Item", encoded);
                            listTag.add(wrapper);
                        }
                    });
            }
        }

        CompoundTag tag = new CompoundTag();
        tag.put(KEY_ITEMS, listTag);
        return tag;
    }

    @Override
    public void deserializeNBT(@NotNull HolderLookup.Provider provider, @NotNull CompoundTag tag) {
        ListTag tagList = tag.getList(KEY_ITEMS).orElseGet(ListTag::new);

        for (int i = 0; i < tagList.size(); i++) {
            CompoundTag itemTags = tagList.getCompound(i).orElseGet(CompoundTag::new);
            int slot = itemTags.getInt(KEY_SLOT).orElse(-1);
            if (slot >= 0 && slot < this.items.size()) {
                ItemStack.CODEC
                    .parse(provider.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), itemTags)
                    .resultOrPartial(e -> {})
                    .ifPresent((stack) -> this.items.set(slot, stack));
            }
        }
    }

    public void serializeIntoByteBuf(RegistryFriendlyByteBuf byteBuf) {
        ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(byteBuf, this.items);
    }

    public void deserializeFromByteBuf(RegistryFriendlyByteBuf byteBuf) {
        List<ItemStack> newItems = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(byteBuf);
        this.resizeTo(newItems.size());
        for (int i = 0; i < this.items.size(); i++) {
            this.items.set(i, newItems.get(i));
        }
    }

    /**
     * Applies a pre-decoded item list received from {@code SatchelInventorySyncPacketS2C}.
     * Resizes to match the incoming list (handles tier changes between sessions) and
     * replaces every slot in one pass.
     */
    public void deserializeFromByteBufList(List<ItemStack> newItems) {
        this.resizeTo(newItems.size());
        for (int i = 0; i < this.items.size(); i++) {
            this.items.set(i, newItems.get(i));
        }
    }
    // endregion

    // region StackedContentsCompatible
    @Override
    public void fillStackedContents(@NotNull StackedItemContents contents) {
        for (ItemStack itemstack : this.items) {
            contents.accountSimpleStack(itemstack);
        }
    }
    // endregion

    public SatchelData getParent() {
        return parent;
    }
    public List<ItemStack> getItems() { return items; }
}
