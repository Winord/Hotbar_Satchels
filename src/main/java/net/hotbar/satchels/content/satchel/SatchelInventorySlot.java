package net.hotbar.satchels.content.satchel;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
public class SatchelInventorySlot extends Slot {
    private final int baseX;
    private final int baseY;
    private final SatchelData satchelData;
    private final SatchelInventory inventory;

    public SatchelInventorySlot(SatchelInventory inventory, int slot, int x, int y) {
        super(inventory, slot, x, y);
        this.baseX = x;
        this.baseY = y;
        this.satchelData = inventory.getParent();
        this.inventory = inventory;
    }

    public void updateX() {
        this.x = baseX + (satchelData.getHotbarOffset() * 18);
    }

    /**
     * Slides the slot's item icon vertically in lockstep with {@code ScreenWithSatchel}'s
     * inventory-row background tween, so it moves with the background instead of popping in/out
     * when the 11.1 toggle button (or equip/unequip) hides the row on the inventory screen.
     * Called every frame from {@code InventoryScreenMixin} with
     * {@code ScreenWithSatchel#getInventoryYOffset()} (0 = shown, {@code INVENTORY_HIDE_OFFSET}
     * = fully retracted) — the same value used to position the background sprite.
     * <p>
     * <b>fix3:</b> must be {@code baseY - offset}, not {@code baseY + offset}. The background
     * sprite in {@code ScreenWithSatchel#renderSatchelInventory} is drawn at
     * {@code top + height - offset - 1} — as {@code offset} grows the sprite moves <i>up</i>
     * (smaller Y) so it slides underneath the vanilla inventory panel, which is drawn
     * immediately afterward in the same {@code renderBg} call and paints over it. The item icon
     * has to travel the exact same direction and distance to stay glued to the sprite; the
     * previous {@code baseY + offset} sent it <i>down</i> instead, so the icon and the
     * background visibly split apart mid-tween (the "duplicated GUI" symptom). This method only
     * gets the icon moving in the right direction — see
     * {@code AbstractContainerScreenMixin#satchels$clipSatchelSlotStart/End} for why the icon
     * still needs a scissor clip to actually disappear behind the panel instead of floating on
     * top of it once it slides above the panel's bottom edge.
     */
    public void updateY(int offset) {
        this.y = baseY - offset;
    }

    /**
     * Live bounds check against the satchel's <i>current</i> tier, not a snapshot taken when
     * this slot was added to the menu. {@code MenuWithSatchel} now always creates
     * {@link SatchelTier#MAX_SLOT_COUNT} of these per menu (see that constant's javadoc), so a
     * slot's container index can legitimately sit past the currently-equipped tier's real slot
     * count — e.g. after downgrading Diamond (6) to Golden (3) in an already-open Accessories
     * screen, slots 3–5 are still present as {@code Slot} objects but should act as if they
     * don't exist. This is always safe to check freshly (no snapshot to go stale) since
     * satchel tier changes are only ever allowed while the satchel is empty (see
     * {@code AccessoriesCompat#canUnequipSatchel}) — there's never real content sitting past
     * the live boundary for this to hide.
     */
    private boolean isWithinCurrentTier() {
        return getContainerSlot() < this.satchelData.getSatchelInventory().getContainerSize();
    }

    /**
     * Beyond the live tier boundary this slot is inert — not drawn, not highlightable, not
     * hoverable ({@code AbstractContainerScreenMixin}'s {@code isActive()} wraps call through
     * to this override) — which is what makes the always-reserved
     * {@link SatchelTier#MAX_SLOT_COUNT} slots invisible past the equipped tier's real count
     * instead of showing as extra empty slots.
     */
    @Override
    public boolean isActive() {
        return isWithinCurrentTier();
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return this.satchelData.canAccess() && isWithinCurrentTier()
                && this.inventory.canPlaceItem(getContainerSlot(), stack);
    }

    public boolean allowModification(@NotNull Player player) {
        return super.allowModification(player) && this.satchelData.canAccess() && isWithinCurrentTier();
    }

    @Override
    public boolean isHighlightable() {
        return this.satchelData.canAccess() && isWithinCurrentTier();
    }

    @Override
    @NotNull
    public ItemStack getItem() {
        return super.getItem();
    }
}