package net.hotbar.satchels.content.satchel;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.hotbar.satchels.ModTags;
import net.hotbar.satchels.compat.SatchelsCompat;
import org.jetbrains.annotations.NotNull;

/**
 * A vanilla-menu {@link Slot} that reads and writes directly through
 * {@link SatchelData#getSatchelSlotStack()}/{@code setSatchelSlotStack} rather than a backing
 * {@link Container} — {@link SatchelData} is the single source of truth for the equipped
 * satchel item, so there's no separate cache to keep in sync.
 */
public class SatchelEquipmentSlot extends Slot {
    private static final Container emptyInventory = new SimpleContainer(0);
    private final Player player;
    private int baseX;

    public SatchelEquipmentSlot(Player player, int xPosition, int yPosition) {
        super(emptyInventory, 0, xPosition, yPosition);
        this.player = player;
        this.baseX = xPosition;
    }

    /**
     * Slides the slot's item icon horizontally in lockstep with {@code ScreenWithSatchel
     * #renderSatchelSlot}'s background-sprite tween ({@code slotXOffset}), so the item stays
     * glued to the sprite instead of sitting frozen at its construction-time {@code x} while
     * the background slides in/out on equip/unequip. Mirrors {@code SatchelInventorySlot
     * #updateX/#updateY} for the same reason: vanilla's per-slot item render pass reads
     * {@code Slot#x} directly and has no idea the background it's sitting on is animated.
     * <p>
     * Unlike {@code SatchelInventorySlot#updateY} (which has to negate the offset — see its
     * javadoc), this one adds the offset directly: the background sprite in
     * {@code renderSatchelSlot} is blitted at {@code left + width + slotXOffset - 1}, i.e.
     * already {@code baseX}-relative in the same direction as {@code slotXOffset} grows, so
     * {@code baseX + offset} matches it without a sign flip.
     */
    public void updateX(int offset) {
        this.x = baseX + offset;
    }

    /**
     * Re-anchors the slot before {@link #updateX} applies the slide-in/out tween offset, so the
     * indicator lands in the right spot on whatever {@code allowed_menus} screen is currently
     * open — each has its own {@code imageWidth}/{@code imageHeight}, unlike the fixed
     * {@code InventoryScreen} the slot's construction-time {@code (x, y)} used to assume.
     * <p>
     * {@code baseX}/{@code y} aren't known at menu-construction time (the server-side
     * {@code AbstractContainerMenuMixin} hook that adds this slot to non-{@code InventoryMenu}
     * menus has no idea what texture a client screen will use), so {@code
     * AbstractContainerScreenMixin} calls this once per frame with the current screen's own
     * geometry before calling {@link #updateX}. {@code InventoryScreen} is 176 wide, 166 tall,
     * and {@code InventoryMenuMixin}'s original hand-placed slot sits at {@code (180, 142)},
     * i.e. exactly {@code (imageWidth + 4, imageHeight - 24)}; that same relationship is reused
     * here for every menu so behavior on {@code InventoryScreen} itself is unchanged.
     */
    public void updateBase(int baseX, int y) {
        this.baseX = baseX;
        this.y = y;
    }

    @Override
    public void setByPlayer(@NotNull ItemStack to, @NotNull ItemStack from) {
        super.setByPlayer(to, from);

        if (!ItemStack.isSameItemSameComponents(to, from) && to.is(ModTags.SATCHEL)) SatchelItem.playEquipSound(player);
    }

    public boolean isShown(Player player, AbstractContainerMenu menu) {
        if (!SatchelsCompat.VANILLA.isLoaded()) return false;
        SatchelData data = SatchelData.get(player);
        return menu.getCarried().is(ModTags.SATCHEL) || (
                data.getSatchelInventory().isEmpty() &&
                        this.getItem().is(ModTags.SATCHEL)
        );
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        if (stack.isEmpty()) return false;
        return SatchelData.get(player).isSatchelSlotItemValid(stack);
    }

    @Override
    @NotNull
    public ItemStack getItem() {
        return SatchelData.get(player).getSatchelSlotStack();
    }

    @Override
    public void set(@NotNull ItemStack stack) {
        SatchelData.get(player).setSatchelSlotStack(stack);
        this.setChanged();
    }

    @Override
    public void onQuickCraft(@NotNull ItemStack oldStackIn, @NotNull ItemStack newStackIn) {
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public int getMaxStackSize(@NotNull ItemStack stack) {
        return 1;
    }

    @Override
    public boolean mayPickup(@NotNull Player player) {
        if (!SatchelData.get(player).getSatchelInventory().isEmpty()) return false;
        return !SatchelData.get(player).getSatchelSlotStack().isEmpty();
    }

    @Override
    @NotNull
    public ItemStack remove(int amount) {
        SatchelData data = SatchelData.get(player);
        ItemStack current = data.getSatchelSlotStack();
        ItemStack removed = current.split(amount);
        data.setSatchelSlotStack(current);

        if (!removed.isEmpty() && data.isActive()) data.setActive(false, true);
        setChanged();
        return removed;
    }

    @Override
    public void setChanged() {
        SatchelData.get(player).syncSatchelSlotToObservers();
    }
}