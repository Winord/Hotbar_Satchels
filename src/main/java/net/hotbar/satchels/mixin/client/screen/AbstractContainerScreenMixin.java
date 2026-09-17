package net.hotbar.satchels.mixin.client.screen;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.inventory.ContainerInput;
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
 * {@code InventoryMenu} and {@code AbstractMountInventoryScreen} (horse, nautilus, ...) — see
 * {@code satchels$renderSatchelInventory}'s javadoc for why the latter doesn't need (and
 * previously wrongly had) a separate mixin of its own.
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
    protected abstract Slot getHoveredSlot(double pMouseX, double pMouseY);
    
    @ModifyExpressionValue(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;hasClickedOutside(DDII)Z"))
    public boolean satchels$hasClickedOutsideOnClick(boolean original, MouseButtonEvent event, boolean bl) {
        return satchels$hasClickedOutside(original, event);
    }

    @ModifyExpressionValue(method = "mouseReleased", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;hasClickedOutside(DDII)Z"))
    public boolean satchels$hasClickedOutsideOnRelease(boolean original, MouseButtonEvent event) {
        return satchels$hasClickedOutside(original, event);
    }

    private boolean satchels$hasClickedOutside(boolean original, MouseButtonEvent event) {
        if (!original) return false;

        double x = event.x();
        double y = event.y();

        Slot hovered = getHoveredSlot(x, y);
        if (hovered instanceof SatchelEquipmentSlot satchelSlot && satchelSlot.isShown(Minecraft.getInstance().player, this.getMenu())) {
            return false;
        }
        if (hovered instanceof SatchelInventorySlot) {
            return false;
        }

        Identifier location = SatchelMenuLocation.resolve(menu);

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
     * Injected at {@code HEAD} of {@code extractContents}: fires after the panel background is
     * drawn (since {@code extractBackground} always runs first) and before slots/labels — same
     * timing as the old {@code renderBg}, generic across every {@code allowed_menus} screen.
     * <p>
     * Also covers {@code AbstractMountInventoryScreen} (horse, nautilus, ...): that class
     * overrides {@code extractBackground} (for its own panel texture/entity preview) and
     * {@code extractRenderState} (to stash {@code xMouse}/{@code yMouse}), but does <i>not</i>
     * override {@code extractContents} itself — {@code extractRenderState}'s {@code
     * super.extractRenderState(...)} call still resolves to {@code
     * AbstractContainerScreen#extractRenderState}, which calls {@code this.extractContents(...)}
     * and, via ordinary virtual dispatch, lands right back on this same injected method. An
     * earlier version of this hook bailed out for {@code AbstractMountInventoryScreen} on the
     * assumption that it wouldn't fire there, and gave mount screens their own separate
     * {@code extractBackground}-based renderer instead (formerly {@code
     * HorseInventoryScreenMixin}) — which put the satchel row in the wrong render phase (grouped
     * with the opaque panel/background draws rather than the content draws) and, being a second
     * independent hook, needed its own separate {@code ScreenWithSatchel} instance, which then
     * desynced from this class's interaction-gating logic (see the git history for that whole
     * saga). Letting this one hook run unconditionally for every {@code allowed_menus} screen,
     * mount screens included, avoids all of that.
     */
    @Inject(method = "extractContents", at = @At("HEAD"))
    public void satchels$renderSatchelInventory(GuiGraphicsExtractor guiGraphics, int p_283661_, int p_281248_, float p_281886_, CallbackInfo ci) {
        Identifier location = SatchelMenuLocation.resolve(menu);

        if (location == null) return;
        if (!SatchelsCommonConfig.isAllowed(location)) return;

        // This HEAD injection runs before extractContents' own pushMatrix/translate, so
        // leftPos/topPos are added manually. Scissored (rather than relying on draw order)
        // since the deferred render pipeline doesn't guarantee submission order keeps these
        // visually under the panel.
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        if (menu instanceof InventoryMenu) {
            guiGraphics.enableScissor(this.leftPos + this.imageWidth, 0, screenWidth, screenHeight);
            satchels$screenWithSatchel.renderSatchelSlot(guiGraphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
            guiGraphics.disableScissor();

            int slotXOffset = (int) satchels$screenWithSatchel.getSlotXOffset();
            for (Slot slot : this.menu.slots) {
                if (slot instanceof SatchelEquipmentSlot satchelEquipmentSlot) satchelEquipmentSlot.updateX(slotXOffset);
            }
        }

        Tuple<Integer, Integer> offset = SatchelsCommonConfig.getOverlayOffset(location);
        boolean forceHidden = SatchelsClientConfig.isSatchelHiddenInInventory();
        // Clip boundary must track the panel's actual bottom edge (topPos + offset.getB()),
        // not the un-offset imageHeight — otherwise a nonzero overlayYOffset (e.g. shulker_box's
        // default "0 -1") crops the row by that many pixels. The row's corner pixel (see
        // ModSprites/ScreenWithSatchel javadoc) needs a second, separate scissor pass below —
        // it sits 1px above this clip line and this boundary must not move to accommodate it.
        guiGraphics.enableScissor(0, this.topPos + this.imageHeight + offset.getB(), screenWidth, screenHeight);
        satchels$screenWithSatchel.renderSatchelInventory(guiGraphics, this.leftPos + offset.getA(), this.topPos + offset.getB(), this.imageHeight, forceHidden);
        guiGraphics.disableScissor();

        // Second, always-on scissor pass for the row's corner "tuck" pixel — see
        // ScreenWithSatchel#renderSatchelInventoryCorners's javadoc. Called after the main
        // scissor is disabled, not nested: GuiGraphics' scissor stack only intersects with
        // whatever is already active, so a nested rect could never expose a pixel above it.
        satchels$screenWithSatchel.renderSatchelInventoryCorners(guiGraphics, this.leftPos + offset.getA(), this.topPos + offset.getB(), this.imageHeight);

        int rowOffset = (int) satchels$screenWithSatchel.getInventoryYOffset();
        for (Slot slot : this.menu.slots) {
            if (slot instanceof SatchelInventorySlot satchelSlot) satchelSlot.updateY(rowOffset);
        }
    }

    @WrapOperation(method = "getHoveredSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;isActive()Z"))
    public boolean satchels$changeIsActive(Slot slot, Operation<Boolean> original) {
        if (slot instanceof SatchelEquipmentSlot satchelSlot) return satchelSlot.isShown(Minecraft.getInstance().player, this.getMenu());
        if (slot instanceof SatchelInventorySlot && satchels$isSatchelFullyRetractedHere()) return false;
        return original.call(slot);
    }

    @WrapOperation(method = "extractSlots", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;isActive()Z"))
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
        Identifier location = SatchelMenuLocation.resolve(menu);
        return location != null && SatchelsCommonConfig.isAllowed(location)
                && satchels$screenWithSatchel.getInventoryYOffset() >= ScreenWithSatchel.INVENTORY_HIDE_OFFSET;
    }

    /**
     * Suppresses slot hover (tooltip + highlight) during both the hide tween and the reveal
     * tween. Checks the animated offset rather than the raw hide flag so the tooltip can't
     * appear before the row has fully slid back into place, and vanishes the moment the hide
     * toggle flips (before the slide even starts).
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
        Identifier location = SatchelMenuLocation.resolve(menu);
        if (location == null || !SatchelsCommonConfig.isAllowed(location)) return false;

        if (SatchelsClientConfig.isSatchelHiddenInInventory()) return true;
        return satchels$screenWithSatchel.getInventoryYOffset() != 0;
    }

    /**
     * Blocks all three click paths (plain click, shift-click, hotbar-key swap via
     * {@link #satchels$swapWithSatchelSlot}) to a hidden {@code SatchelInventorySlot}.
     * <p>
     * The hotbar-key swap path calls {@code slotClicked} directly with {@code ContainerInput.SWAP},
     * bypassing {@code getHoveredSlot} entirely — the target satchel slot is encoded in
     * {@code pMouseButton}, so it's resolved separately here.
     */
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    public void satchels$blockHiddenSatchelSlotClicks(Slot pSlot, int pSlotId, int pMouseButton, ContainerInput pType, CallbackInfo ci) {
        if (!satchels$isSatchelInteractionBlockedHere()) return;

        if (pSlot instanceof SatchelInventorySlot) {
            ci.cancel();
            return;
        }

        if (pType == ContainerInput.SWAP && pMouseButton >= 0 && pMouseButton < menu.slots.size()
                && menu.slots.get(pMouseButton) instanceof SatchelInventorySlot) {
            ci.cancel();
        }
    }

    @WrapOperation(method = {"checkHotbarKeyPressed", "checkHotbarMouseClicked"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", ordinal = 1))
    public void satchels$swapWithSatchelSlot(AbstractContainerScreen<?> instance, Slot slot, int index, int i, ContainerInput type, Operation<Void> original) {
        Player player = Minecraft.getInstance().player;
        SatchelData data = SatchelData.get(player);
        // Without this guard, vanilla's SWAP branch removes the source slot before
        // SatchelInventory#canPlaceItem runs — the stack would be pulled out with nowhere to
        // go and silently deleted.
        if (
                SatchelsClientConfig.shouldSwapWithShiftKey() &&
                        data.canAccess() && data.isSlotInSatchel(i) && net.minecraft.client.Minecraft.getInstance().hasShiftDown() &&
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
     * bottom edge, making them slide visually under the panel during the retract tween
     * instead of floating on top of it. {@code SatchelEquipmentSlot} is scissored to the
     * region right of the panel's right edge, hiding the icon while it slides in during equip.
     * <p>
     * Inactive slots (beyond the equipped tier's real slot count) are skipped entirely.
     */
    @Unique
    private static boolean satchels$needsScissor(Slot slot) {
        return (slot instanceof SatchelInventorySlot || slot instanceof SatchelEquipmentSlot) && slot.isActive();
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    public void satchels$clipSatchelSlotStart(GuiGraphicsExtractor guiGraphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!satchels$needsScissor(slot)) return;
        Identifier location = SatchelMenuLocation.resolve(menu);
        if (location == null || !SatchelsCommonConfig.isAllowed(location)) return;

        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        // extractSlot runs inside extractContents' own pushMatrix()/translate(leftPos, topPos)
        // block, so that translation is already applied here — do not add leftPos/topPos again.
        if (slot instanceof SatchelEquipmentSlot) {
            int scissorRightEdge = this.imageWidth;
            guiGraphics.enableScissor(scissorRightEdge, 0, screenWidth, screenHeight);
        } else {
            // Must track overlayOffset the same way the background-bar clip above does, or a
            // nonzero overlayYOffset clips real pixels off the icon row's bottom edge.
            Tuple<Integer, Integer> overlayOffset = SatchelsCommonConfig.getOverlayOffset(location);
            int scissorBottomEdge = this.imageHeight + overlayOffset.getB();
            guiGraphics.enableScissor(0, scissorBottomEdge, screenWidth, screenHeight);
        }
    }

    @Inject(method = "extractSlot", at = @At("RETURN"))
    public void satchels$clipSatchelSlotEnd(GuiGraphicsExtractor guiGraphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!satchels$needsScissor(slot)) return;
        Identifier location = SatchelMenuLocation.resolve(menu);
        if (location == null || !SatchelsCommonConfig.isAllowed(location)) return;
        guiGraphics.disableScissor();
    }
}
