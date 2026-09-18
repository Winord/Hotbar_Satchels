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

    /**
     * {@code xPosition}/{@code yPosition} are just the vanilla {@link Slot} constructor's
     * required starting coordinates — harmless placeholders. The real, screen-relative
     * position is now recomputed every frame by {@link #updatePosition}, because unlike the
     * old survival-inventory-only wiring, this slot can now live in a menu of any size, so a
     * baked-in x/y from construction time can't be right for all of them.
     */
    public SatchelEquipmentSlot(Player player, int xPosition, int yPosition) {
        super(emptyInventory, 0, xPosition, yPosition);
        this.player = player;
    }

    /**
     * Recomputes this slot's screen-relative position from the CURRENT screen's own
     * {@code imageWidth}/{@code imageHeight}, so the real, clickable {@link Slot} lines up with
     * {@code ScreenWithSatchel#renderSatchelSlot}'s purely-visual sprite regardless of which
     * allowed menu's screen is open — that method already blits the background sprite at
     * {@code left + width + slotXOffset - 1, top + height - 30}; mirroring the same formula
     * here (screen-relative, i.e. without the {@code left}/{@code top} anchor vanilla adds back
     * on render) is what lets one generic slot follow any menu's own dimensions instead of the
     * single fixed spot the old survival-GUI-only {@code baseX} constant assumed.
     * <p>
     * The {@code +5}/{@code +6} matter: the 27x28 backdrop sprite and the actual item "hole"
     * inside it aren't the same rect — {@code renderSatchelSlot} draws its own empty-slot
     * placeholder icon at {@code x + 5, y + 6} within that backdrop, and this slot's position
     * needs to match that inner hole (where vanilla will actually draw a held item and
     * hit-test clicks), not the backdrop's own top-left corner. The old fixed constants
     * ({@code 170 + 10, 142}) baked this same {@code +5, +6} in already — it's just no longer
     * obvious once the position is computed instead of hardcoded.
     */
    public void updatePosition(int imageWidth, int imageHeight, int xOffset) {
        this.x = imageWidth + xOffset - 1 + 5;
        this.y = imageHeight - 30 + 6;
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
