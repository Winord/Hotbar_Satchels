package net.hotbar.satchels.mixin.client.screen;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.hotbar.satchels.ModTags;
import net.hotbar.satchels.SatchelsCommonConfig;
import net.hotbar.satchels.api.ScreenWithSatchel;
import net.hotbar.satchels.client.SatchelMenuLocation;
import net.hotbar.satchels.client.SatchelsClientConfig;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelEquipmentSlot;
import net.hotbar.satchels.content.satchel.SatchelInventorySlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side container screen hooks: satchel slot rendering, click-outside detection,
 * hotbar-swap-on-shift-click, and satchel-equipment-slot visibility handling.
 * <p>
 * This single generic mixin covers every {@code allowed_menus} screen, including
 * {@code InventoryMenu} — the old separate {@code InventoryScreenMixin} was removed when
 * satchel rendering was generalized beyond the survival inventory (§11.1).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> extends Screen {
    @Unique
    private final ScreenWithSatchel satchels$screenWithSatchel = new ScreenWithSatchel();

    @Shadow
    protected int imageHeight;

    @Shadow
    protected int imageWidth;

    @Shadow
    @Final
    protected T menu;

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    protected AbstractContainerScreenMixin(Component p_96550_) {
        super(p_96550_);
    }

    @Shadow
    public abstract T getMenu();

    @Shadow
    protected abstract Slot findSlot(double pMouseX, double pMouseY);

    /**
     * Prevents throwing an item when clicking on a visible {@code SatchelEquipmentSlot}:
     * that slot is outside the pixel bounds {@code ScreenWithSatchel.hasClickedOutside}
     * treats as "inside the window" (it only widens that zone for the satchel inventory row).
     */
    @ModifyExpressionValue(method = {"mouseClicked", "mouseReleased"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;hasClickedOutside(DDIII)Z"))
    public boolean satchels$hasClickedOutside(boolean original, double x, double y, int click) {
        if (!original) return false;

        Slot hovered = findSlot(x, y);
        if (hovered instanceof SatchelEquipmentSlot satchelSlot && satchelSlot.isShown(Minecraft.getInstance().player, this.getMenu())) {
            return false;
        }

        ResourceLocation location = SatchelMenuLocation.resolve(menu);

        if (location == null) return true;
        if (!SatchelsCommonConfig.isAllowed(location)) return true;
        Tuple<Integer, Integer> offset = SatchelsCommonConfig.getOffset(location);
        return ScreenWithSatchel.hasClickedOutside(x, y, leftPos + offset.getA(), topPos + offset.getB(), this.imageHeight);
    }

    /**
     * Renders the satchel inventory background and slides {@code SatchelInventorySlot} items
     * with it. For {@code InventoryMenu} also renders the equipment-slot indicator and slides
     * the {@code SatchelEquipmentSlot} icon with the sprite.
     * <p>
     * Injected at the {@code renderBg} call inside {@code renderBackground} so it fires after
     * the vanilla background is already drawn but before item icons.
     */
    @Inject(method = "renderBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"))
    public void satchels$renderSatchelInventory(GuiGraphics guiGraphics, int p_283661_, int p_281248_, float p_281886_, CallbackInfo ci) {
        ResourceLocation location = SatchelMenuLocation.resolve(menu);

        if (location == null) return;
        if (!SatchelsCommonConfig.isAllowed(location)) return;

        if (menu instanceof InventoryMenu) {
            satchels$screenWithSatchel.renderSatchelSlot(guiGraphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);

            int slotXOffset = (int) satchels$screenWithSatchel.getSlotXOffset();
            for (Slot slot : this.menu.slots) {
                if (slot instanceof SatchelEquipmentSlot satchelEquipmentSlot) satchelEquipmentSlot.updateX(slotXOffset);
            }
        }

        Tuple<Integer, Integer> offset = SatchelsCommonConfig.getOverlayOffset(location);
        boolean forceHidden = SatchelsClientConfig.isSatchelHiddenInInventory();
        satchels$screenWithSatchel.renderSatchelInventory(guiGraphics, this.leftPos + offset.getA(), this.topPos + offset.getB(), this.imageHeight, forceHidden);

        int rowOffset = (int) satchels$screenWithSatchel.getInventoryYOffset();
        for (Slot slot : this.menu.slots) {
            if (slot instanceof SatchelInventorySlot satchelSlot) satchelSlot.updateY(rowOffset);
        }
    }

    @WrapOperation(method = "findSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;isActive()Z"))
    public boolean satchels$changeIsActive(Slot slot, Operation<Boolean> original) {
        if (slot instanceof SatchelEquipmentSlot satchelSlot) return satchelSlot.isShown(Minecraft.getInstance().player, this.getMenu());
        if (slot instanceof SatchelInventorySlot && satchels$isSatchelFullyRetractedHere()) return false;
        return original.call(slot);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;isActive()Z"))
    public boolean satchels$changeIsActiveInRender(Slot slot, Operation<Boolean> original) {
        if (slot instanceof SatchelEquipmentSlot satchelSlot) return satchelSlot.isShown(Minecraft.getInstance().player, this.getMenu());
        if (slot instanceof SatchelInventorySlot && satchels$isSatchelFullyRetractedHere()) return false;
        return original.call(slot);
    }

    /**
     * Returns true once the satchel row has fully retracted (offset >= {@code INVENTORY_HIDE_OFFSET}).
     * Used to gate icon rendering — icons continue rendering during the ~300 ms slide-out tween
     * (so they visually slide away) and only stop once fully off-screen.
     */
    @Unique
    private boolean satchels$isSatchelFullyRetractedHere() {
        ResourceLocation location = SatchelMenuLocation.resolve(menu);
        return location != null && SatchelsCommonConfig.isAllowed(location)
                && satchels$screenWithSatchel.getInventoryYOffset() >= ScreenWithSatchel.INVENTORY_HIDE_OFFSET;
    }

    /**
     * Suppresses slot hover (tooltip + highlight) during both the hide tween and the reveal
     * tween. Checks the animated offset rather than the raw hide flag so the tooltip can't
     * appear before the row has fully slid back into place, and vanishes the moment the hide
     * toggle flips (before the slide even starts).
     * <p>
     * Icon rendering ({@code isActive()} above) is intentionally left on the animated check —
     * only hover/tooltip need to vanish early; the icon should visibly slide out from under
     * the mouse rather than popping away.
     */
    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("RETURN"), cancellable = true)
    public void satchels$suppressHoverWhenHidden(Slot slot, double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (slot instanceof SatchelInventorySlot && satchels$isSatchelInteractionBlockedHere()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Returns true if satchel slot interaction should be blocked — either because the hide
     * flag is set (blocks instantly on keypress, before the slide starts) or because the
     * animated offset hasn't settled at 0 yet (blocks during the reveal tween until the row
     * is fully visible).
     */
    @Unique
    private boolean satchels$isSatchelInteractionBlockedHere() {
        ResourceLocation location = SatchelMenuLocation.resolve(menu);
        if (location == null || !SatchelsCommonConfig.isAllowed(location)) return false;

        if (SatchelsClientConfig.isSatchelHiddenInInventory()) return true;
        return satchels$screenWithSatchel.getInventoryYOffset() != 0;
    }

    /**
     * Blocks all three click paths (plain click, shift-click, hotbar-key swap via
     * {@link #satchels$swapWithSatchelSlot}) to a hidden {@code SatchelInventorySlot}.
     * <p>
     * The hotbar-key swap path calls {@code slotClicked} directly with {@code ClickType.SWAP},
     * bypassing {@code findSlot} entirely — the target satchel slot is encoded in
     * {@code pMouseButton}, so it's resolved separately here.
     * <p>
     * Client-only, unsynced — consistent with the hide toggle never touching
     * {@code SatchelData#isActive()}.
     */
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    public void satchels$blockHiddenSatchelSlotClicks(Slot pSlot, int pSlotId, int pMouseButton, ClickType pType, CallbackInfo ci) {
        if (!satchels$isSatchelInteractionBlockedHere()) return;

        if (pSlot instanceof SatchelInventorySlot) {
            ci.cancel();
            return;
        }

        if (pType == ClickType.SWAP && pMouseButton >= 0 && pMouseButton < menu.slots.size()
                && menu.slots.get(pMouseButton) instanceof SatchelInventorySlot) {
            ci.cancel();
        }
    }

    @WrapOperation(method = {"checkHotbarKeyPressed", "checkHotbarMouseClicked"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V", ordinal = 1))
    public void satchels$swapWithSatchelSlot(AbstractContainerScreen<?> instance, Slot slot, int index, int i, ClickType type, Operation<Void> original) {
        Player player = Minecraft.getInstance().player;
        SatchelData data = SatchelData.get(player);
        // Without this guard, vanilla's ClickType.SWAP branch calls Slot#remove on the source
        // before SatchelInventory#canPlaceItem ever runs — the stack is pulled out with nowhere
        // to go and silently deleted. This covers both a worn satchel being swapped into its
        // own storage and a different satchel from the inventory being swapped into the
        // equipped one's storage.
        if (
                SatchelsClientConfig.shouldSwapWithShiftKey() &&
                        data.canAccess() && data.isSlotInSatchel(i) && hasShiftDown() &&
                        !slot.getItem().is(ModTags.SATCHEL)
        ) {
            int satchelIndex = i - data.getHotbarOffset();

            int satchelSlot = -1;
            for (int j = 0; j < instance.getMenu().slots.size(); j++) {
                Slot possible = instance.getMenu().slots.get(j);
                if (possible instanceof SatchelInventorySlot && possible.getContainerSlot() == satchelIndex) satchelSlot = j;
            }

            if (satchelSlot == -1) {
                original.call(instance, slot, index, i, type);
                return;
            }

            original.call(instance, slot, index, satchelSlot, type);
            return;
        }
        original.call(instance, slot, index, i, type);
    }

    /**
     * Scissors {@code SatchelInventorySlot} item icons to the region below the panel's
     * bottom edge ({@code topPos + imageHeight}), making them slide visually under the panel
     * during the retract tween instead of floating on top of it.
     * <p>
     * {@code SatchelEquipmentSlot} is scissored to the region right of the panel's right
     * edge ({@code leftPos + imageWidth}), hiding the icon while it slides in during equip.
     * <p>
     * The guard skips inactive slots (e.g. slots beyond the equipped tier's real slot count,
     * which always exist up to {@code SatchelTier#MAX_SLOT_COUNT}) — nothing gets drawn for
     * an inactive slot, so applying the scissor pair would be wasted GPU state churn.
     * <p>
     * The RETURN inject (not TAIL) ensures every exit path through {@code renderSlot} gets a
     * matching disable — vanilla has an early guard near the top in addition to the natural
     * return, so TAIL would leave the scissor enabled when that guard fires.
     */
    @Unique
    private static boolean satchels$needsScissor(Slot slot) {
        return (slot instanceof SatchelInventorySlot || slot instanceof SatchelEquipmentSlot) && slot.isActive();
    }

    @Inject(method = "renderSlot", at = @At("HEAD"))
    public void satchels$clipSatchelSlotStart(GuiGraphics guiGraphics, Slot slot, CallbackInfo ci) {
        if (!satchels$needsScissor(slot)) return;
        ResourceLocation location = SatchelMenuLocation.resolve(menu);
        if (location == null || !SatchelsCommonConfig.isAllowed(location)) return;

        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        if (slot instanceof SatchelEquipmentSlot) {
            int scissorRightEdge = this.leftPos + this.imageWidth;
            guiGraphics.enableScissor(scissorRightEdge, 0, screenWidth, screenHeight);
        } else {
            int scissorBottomEdge = this.topPos + this.imageHeight;
            guiGraphics.enableScissor(0, scissorBottomEdge, screenWidth, screenHeight);
        }
    }

    @Inject(method = "renderSlot", at = @At("RETURN"))
    public void satchels$clipSatchelSlotEnd(GuiGraphics guiGraphics, Slot slot, CallbackInfo ci) {
        if (!satchels$needsScissor(slot)) return;
        ResourceLocation location = SatchelMenuLocation.resolve(menu);
        if (location == null || !SatchelsCommonConfig.isAllowed(location)) return;
        guiGraphics.disableScissor();
    }
}
