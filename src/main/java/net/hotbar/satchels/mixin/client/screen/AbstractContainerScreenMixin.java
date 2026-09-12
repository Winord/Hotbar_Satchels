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

    // 26.1: renamed from findSlot(double,double) to getHoveredSlot(double,double) — confirmed via
    // javap on the real merged jar (identical descriptor (DD)Lnet/minecraft/world/inventory/Slot;
    // and identical body: iterate menu.slots, check isActive() then isHovering(), return first
    // match). Also narrowed from protected abstract to private on the real class; @Shadow doesn't
    // need to match that exactly to locate the method.
    @Shadow
    protected abstract Slot getHoveredSlot(double pMouseX, double pMouseY);

    /**
     * Prevents throwing an item when clicking on a visible {@code SatchelEquipmentSlot}:
     * that slot is outside the pixel bounds {@code ScreenWithSatchel.hasClickedOutside}
     * treats as "inside the window" (it only widens that zone for the satchel inventory row).
     */
    // 26.1: hasClickedOutside dropped its imageWidth/imageHeight params — it now reads
    // this.imageWidth/this.imageHeight directly (confirmed via javap -c: the descriptor is
    // (DDII)Z, and mouseClicked/mouseReleased now call it with just (mouseX, mouseY, leftPos,
    // topPos)). Descriptor-only fix, method's own semantics unchanged.
    //
    // 26.1: mouseClicked/mouseReleased no longer take raw (double,double,int) mouse params —
    // they take a MouseButtonEvent record (with x()/y()/button() accessors) instead. Confirmed
    // via javap that the two target methods now have genuinely different arities:
    // mouseClicked(MouseButtonEvent, boolean) vs mouseReleased(MouseButtonEvent) — no second
    // boolean. A single @ModifyExpressionValue handler can't cover both anymore (Mixin infers
    // the expected handler signature from each target's own captured locals), so this is split
    // into one handler per target, both delegating to the same private helper.
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
     * 26.1: {@code renderBackground}/{@code renderBg}/{@code render} don't exist anymore — the
     * whole render pipeline was split into an "extract render state" pass (confirmed by tracing
     * the real bytecode end to end). The new top-level order per frame is
     * {@code Screen#extractRenderStateWithTooltipAndSubtitles} → {@code extractBackground(...)}
     * (overridden per screen subclass — e.g. {@code ContainerScreen}/{@code CraftingScreen} blit
     * their own GUI panel texture there, confirmed via javap -c on both) → then
     * {@code extractRenderState(...)} → (for container screens) {@code extractContents(...)},
     * which is declared once in {@code AbstractContainerScreen} and NOT overridden per screen —
     * confirmed by checking every subclass in the hierarchy. So injecting at {@code HEAD} of
     * {@code extractContents} fires after the panel background is already drawn (since
     * {@code extractBackground} always runs first) and before slots/labels are drawn — the exact
     * same timing this mixin had before, just via a different hook, and still generic across
     * every {@code allowed_menus} screen.
     */
    @Inject(method = "extractContents", at = @At("HEAD"))
    public void satchels$renderSatchelInventory(GuiGraphicsExtractor guiGraphics, int p_283661_, int p_281248_, float p_281886_, CallbackInfo ci) {
        Identifier location = SatchelMenuLocation.resolve(menu);

        if (location == null) return;
        if (!SatchelsCommonConfig.isAllowed(location)) return;

        // 26.1: unlike satchels$clipSatchelSlotStart (which clips extractSlot's item icons and
        // runs *inside* extractContents' own pushMatrix/translate), this HEAD injection runs
        // *before* that translate — so leftPos/topPos still need to be added manually here, the
        // pose is genuinely untransformed at this point. These two background-sprite draws had
        // no scissor clip at all before: in 1.21.1 they relied on draw order/z-layering alone to
        // end up hidden behind the main panel, but 26.1's deferred "extract now, batch-render
        // later" pipeline (confirmed via decompile — every draw call just queues a render-state
        // object, it doesn't paint immediately) doesn't guarantee that submission order alone
        // keeps them visually under the panel. Clipping them the same way the item icons are
        // clipped removes that reliance on draw order entirely.
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
        guiGraphics.enableScissor(0, this.topPos + this.imageHeight, screenWidth, screenHeight);
        satchels$screenWithSatchel.renderSatchelInventory(guiGraphics, this.leftPos + offset.getA(), this.topPos + offset.getB(), this.imageHeight, forceHidden);
        guiGraphics.disableScissor();

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

    // 26.1: no more `render` method on this class. The icon-rendering isActive() gate this used
    // to wrap now lives in `extractSlots` (confirmed via javap -c: extractSlots iterates
    // menu.slots and gates each extractSlot(...) call behind the exact same Slot.isActive()
    // check that `render` used to gate renderSlot(...) with).
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
     * <p>
     * Client-only, unsynced — consistent with the hide toggle never touching
     * {@code SatchelData#isActive()}.
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
        // Without this guard, vanilla's ContainerInput.SWAP branch calls Slot#remove on the source
        // before SatchelInventory#canPlaceItem ever runs — the stack is pulled out with nowhere
        // to go and silently deleted. This covers both a worn satchel being swapped into its
        // own storage and a different satchel from the inventory being swapped into the
        // equipped one's storage.
        if (
                SatchelsClientConfig.shouldSwapWithShiftKey() &&
                        // 26.1: Screen.hasShiftDown() (the old static live-state poll) is gone —
                        // confirmed via bytecode search across the whole jar: the only remaining
                        // hasShiftDown() is an instance default method on KeyEvent (via
                        // InputWithModifiers), tied to a specific key event, not a live query.
                        // Minecraft itself now exposes the live-state instance method that this
                        // code actually wants (decompiled: polls InputConstants.isKeyDown for both
                        // shift keys, same semantics as the old static helper).
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
     * The RETURN inject (not TAIL) ensures every exit path through {@code extractSlot} gets a
     * matching disable — vanilla has an early guard near the top in addition to the natural
     * return, so TAIL would leave the scissor enabled when that guard fires.
     */
    @Unique
    private static boolean satchels$needsScissor(Slot slot) {
        return (slot instanceof SatchelInventorySlot || slot instanceof SatchelEquipmentSlot) && slot.isActive();
    }

    // 26.1: renderSlot(GuiGraphics, Slot) was renamed and gained two params — it's now
    // extractSlot(GuiGraphicsExtractor, Slot, int mouseX, int mouseY) (confirmed via javap -c;
    // the two extra ints are just threaded through from extractSlots, unused by this mixin).
    // Still has an early `return` guard partway through in addition to the natural end
    // (confirmed via javap -c), so @At("RETURN") (which injects at every return point) is still
    // the right choice over TAIL — same reasoning as before, just re-verified against the new body.
    @Inject(method = "extractSlot", at = @At("HEAD"))
    public void satchels$clipSatchelSlotStart(GuiGraphicsExtractor guiGraphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!satchels$needsScissor(slot)) return;
        Identifier location = SatchelMenuLocation.resolve(menu);
        if (location == null || !SatchelsCommonConfig.isAllowed(location)) return;

        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        // 26.1: enableScissor(x0,y0,x1,y1) now runs the rect through
        // ScreenRectangle#transformAxisAligned(this.pose) before pushing it (confirmed via
        // decompile) — i.e. it's transformed by whatever pose translation is currently active,
        // not raw absolute screen pixels like before. extractSlot runs *inside*
        // extractContents' own pushMatrix()/translate(leftPos, topPos) block (confirmed via
        // decompile), so that translation is already applied here — adding leftPos/topPos
        // again double-translates the rect, landing the clip region nowhere near the actual
        // panel edge. That's what broke both the equipment-slot icon (clipped away entirely)
        // and the retract animation (nothing left to clip it against the real panel edge).
        if (slot instanceof SatchelEquipmentSlot) {
            int scissorRightEdge = this.imageWidth;
            guiGraphics.enableScissor(scissorRightEdge, 0, screenWidth, screenHeight);
        } else {
            int scissorBottomEdge = this.imageHeight;
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