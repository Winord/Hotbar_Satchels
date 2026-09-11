package net.hotbar.satchels.content.satchel;

import net.minecraft.resources.Identifier;
import net.hotbar.satchels.Satchels;

/**
 * The three satchel tiers: Golden (3 slots), Diamond (6), Netherite (9, the full hotbar). Each
 * tier is backed by its own {@link SatchelItem} instance ({@code ModItems.SATCHEL_GOLDEN}/
 * {@code _DIAMOND}/{@code _NETHERITE}) rather than a shared item with a data component, so tier
 * is a first-class item identity rather than derived state.
 * <p>
 * This enum only carries tier-identity data (slot count, item id, texture ids). It deliberately
 * does <b>not</b> know about recipes, advancements, or loot — those stay in their own datagen
 * providers so this class doesn't become a dumping ground for unrelated per-tier config.
 */
public enum SatchelTier {
    GOLDEN("satchel_golden", 3, "golden"),
    DIAMOND("satchel_diamond", 6, "diamond"),
    NETHERITE("satchel_netherite", 9, "netherite");

    /**
     * Highest slot count across all tiers (Netherite's 9). {@code MenuWithSatchel} always
     * reserves this many {@code SatchelInventorySlot}s up front — regardless of the currently
     * equipped tier, or whether a satchel is equipped at all — rather than only
     * {@code getContainerSize()}'s worth. This matters most for {@code InventoryMenu}: unlike a
     * chest or the Accessories screen (a fresh menu built at open time, correctly snapshotting
     * whatever tier is worn then), the player's own inventory menu is constructed exactly once
     * per session, typically before any satchel is ever equipped. Sizing its satchel slots to
     * the tier at that single construction moment would freeze them at 0 forever — no future
     * equip would ever add real {@link net.minecraft.world.inventory.Slot} objects for them,
     * so vanilla's per-tick {@code AbstractContainerMenu#broadcastChanges} (which only inspects
     * slots that exist in {@code menu.slots}) would never notice or sync satchel storage
     * changes to the client — the survival inventory screen would never show satchel contents,
     * and items picked up while a satchel was active wouldn't visually appear until a chest or
     * the Accessories screen was opened (which builds its own fresh menu). Reserving the max up
     * front instead means the slots always exist; {@code SatchelInventorySlot} checks the
     * *live* {@code getContainerSize()} to decide whether each one is currently active, so
     * slots beyond the equipped tier's count are simply inert (not rendered, not interactable)
     * instead of missing.
     */
    public static final int MAX_SLOT_COUNT = java.util.Arrays.stream(values())
            .mapToInt(SatchelTier::getSlotCount)
            .max()
            .orElse(0);

    private final String itemPath;
    private final int slotCount;
    private final String textureSuffix;

    SatchelTier(String itemPath, int slotCount, String textureSuffix) {
        this.itemPath = itemPath;
        this.slotCount = slotCount;
        this.textureSuffix = textureSuffix;
    }

    /** Item registry path, e.g. {@code satchel_golden} — full id is {@code satchels:satchel_golden}. */
    public String getItemPath() {
        return itemPath;
    }

    /** Number of storage slots this tier's {@link SatchelInventory} holds. */
    public int getSlotCount() {
        return slotCount;
    }

    /** The item-icon model's layer1 (clip) texture, e.g. {@code satchels:item/satchel_clip_golden}. */
    public Identifier getClipTexture() {
        return Satchels.at("item/satchel_clip_" + textureSuffix);
    }

    /**
     * Id of this tier's "worn on the back" model, e.g. {@code satchels:satchel_worn_golden}
     * — a static Blockbench-geometry model (not datagen'd, see {@code SatchelsModelProvider}).
     * <p>
     * 26.1: the old Fabric "extra model" registration API ({@code ModelLoadingPlugin.Context
     * #addModels}) was removed with no direct replacement. Traced {@code ModelManager} /
     * {@code ClientItemInfoLoader} in the real jar and confirmed any json under
     * {@code assets/<ns>/items/} is auto-baked and retrievable via
     * {@code ModelManager#getItemModel(Identifier)} regardless of whether it's tied to a
     * registered Item — so the extra-model dance isn't needed at all anymore. This id must
     * therefore point at the client-item wrapper file under {@code assets/satchels/items/}
     * (NOT the raw geometry file under {@code models/item/}, which is referenced *from inside*
     * that wrapper instead) — see {@code assets/satchels/items/satchel_worn_golden.json} etc.
     */
    public Identifier getWornModelId() {
        return Satchels.at("satchel_worn_" + textureSuffix);
    }
}
