package net.hotbar.satchels.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.hotbar.satchels.ModTags;
import net.hotbar.satchels.client.ModSprites;
import net.hotbar.satchels.client.SatchelsClientConfig;
import net.hotbar.satchels.client.animation.LerpFunctions;
import net.hotbar.satchels.client.animation.LerpHelper;
import net.hotbar.satchels.compat.SatchelsCompat;
import net.hotbar.satchels.content.satchel.SatchelEquipmentSlot;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelItem;
import net.hotbar.satchels.content.satchel.SatchelTier;
import net.minecraft.client.renderer.RenderPipelines;
import org.jetbrains.annotations.ApiStatus;

/**
 * Renders the satchel background overlay, the equipment-slot indicator, and click-outside
 * bounds checking for container screens. Each screen mixin holds its own {@code @Unique}
 * instance of this class (rather than a shared singleton) since it stores per-screen
 * animation state (tween timers, last known offsets).
 */
@Environment(EnvType.CLIENT)
public class ScreenWithSatchel {
    /**
     * Fully-retracted Y offset for the inventory-screen satchel row (background sprite and
     * the {@code SatchelInventorySlot} items sliding with it). Shared as a constant so
     * {@code AbstractContainerScreenMixin} can check "is the row all the way hidden" without
     * duplicating the magic number.
     */
    public static final int INVENTORY_HIDE_OFFSET = 27;

    private float satchelYOffset = -1;
    private float yOffsetOnChange = 0;
    private long inventoryTweenStartTime = 0;
    private long inventoryTweenEndTime = 0;
    private boolean lastInventoryState = false;

    private float slotXOffset = -1;
    private long slotTweenStartTime = 0;
    private long slotTweenEndTime = 0;
    private float xOffsetOnChange = 0;
    private boolean lastSlotState = false;

    private int lastColor = SatchelItem.DEFAULT_COLOR;

    /**
     * Render the satchel background.
     * @param graphics The <code>GuiGraphics</code> passed to the render screen. Easily obtainable from <code>Screen#renderBg</code>.
     * @param left The left edge of the background (when {@link SatchelData#getHotbarOffset()} is 0).
     * @param top The position of the top edge of your screen.
     * @param height The height of your screen.
     */
    public void renderSatchelInventory(GuiGraphicsExtractor graphics, int left, int top, int height) {
        renderSatchelInventory(graphics, left, top, height, false);
    }

    /**
     * Render the satchel background.
     * @param graphics The <code>GuiGraphics</code> passed to the render screen. Easily obtainable from <code>Screen#renderBg</code>.
     * @param left The left edge of the background (when {@link SatchelData#getHotbarOffset()} is 0).
     * @param top The position of the top edge of your screen.
     * @param height The height of your screen.
     * @param forceHidden When {@code true}, the satchel row is treated as not accessible for
     *                     this render call regardless of {@link SatchelData#canAccess()} — used
     *                     by {@code InventoryScreenMixin} for the satchel-visibility toggle
     *                     button, without touching the player's actual equipped/active state.
     */
    public void renderSatchelInventory(GuiGraphicsExtractor graphics, int left, int top, int height, boolean forceHidden) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        SatchelData satchelData = SatchelData.get(player);

        if (satchelYOffset == -1) {
            satchelYOffset = (satchelData.canAccess() && !forceHidden) ? 0 : INVENTORY_HIDE_OFFSET;
        }

        int offsetGoal = INVENTORY_HIDE_OFFSET;
        boolean enabled = satchelData.canAccess() && !forceHidden;
        boolean stateChanged = lastInventoryState != enabled;
        long currentTime = Util.getMillis();

        if (stateChanged) {
            inventoryTweenStartTime = currentTime;
            inventoryTweenEndTime = currentTime + 300;
            yOffsetOnChange = satchelYOffset;
            lastInventoryState = enabled;
        }

        float progress = LerpHelper.getProgress(currentTime, inventoryTweenStartTime, inventoryTweenEndTime);
        if (SatchelsClientConfig.shouldAnimateGUI()) satchelYOffset = (int) LerpFunctions.EXPONENTIAL.lerp(progress, yOffsetOnChange, enabled ? 0 : offsetGoal);
        else satchelYOffset = enabled ? 0 : offsetGoal;
        if (satchelYOffset == offsetGoal) return;

        // Defensive: canAccess() implies a tier is set, but bail rather than NPE if a future
        // caller reaches this with forceHidden=false and no satchel ever equipped this session.
        SatchelTier tier = satchelData.getCurrentTier();
        if (tier == null) return;
        ModSprites.Sprite sprite = ModSprites.getInventorySprite(tier);

        int satchelXOffset = satchelData.getHotbarOffset() * 18;

        int satchelTint = SatchelAccess.getSatchelTint(player);
        if (satchelTint != -1) lastColor = satchelTint;

        int invTint = ARGB.color(ARGB.alpha(lastColor), ARGB.red(lastColor), ARGB.green(lastColor), ARGB.blue(lastColor));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite.id(), left + 2 + satchelXOffset, top + height - (int) satchelYOffset - 1, sprite.width(), sprite.height(), invTint);
    }

    /**
     * Second clip pass for the satchel row's top-left corner "tuck" pixel that completes the
     * main panel's bottom-left border corner. Row 0 of every {@code satchel_inventory_<tier>.png}
     * is fully transparent except that one pixel; it sits exactly 1px above the main clip line
     * in {@link #renderSatchelInventory}, so a second, narrower scissor is needed to expose it
     * without shifting the main tween's own visibility threshold by a pixel.
     * <p>
     * Runs unconditionally (not gated on the row being at rest) so there's no visibility state
     * to switch and thus nothing to flash on toggle — the corner cell just shows whatever the
     * sliding sprite has at that spot each frame.
     * <p>
     * Only the left corner is drawn — the mirrored top-right pixel has no matching notch on the
     * panels this mod targets.
     * <p>
     * <b>Only correct at the hotbar's own left edge</b> (hotbarOffset == 0). Past a shifted
     * slot-start the pixel no longer lands on a real notch and would bleed onto the Survival
     * GUI's plain hotbar border instead — {@link #satchels$isCornerSafeAtPosition} gates the
     * whole pass to the range each tier's panel art actually supports (Golden 1-3, Diamond 1-6,
     * Netherite always); outside that range this pass is skipped and the row renders with its
     * corner cropped, same as if this method didn't exist.
     */
    @ApiStatus.Internal
    public void renderSatchelInventoryCorners(GuiGraphicsExtractor graphics, int left, int top, int height) {
        // satchelYOffset == -1: nothing has rendered yet this screen. >= INVENTORY_HIDE_OFFSET:
        // fully retracted, main pass already early-returns. Either way there's nothing to draw.
        if (satchelYOffset < 0 || satchelYOffset >= INVENTORY_HIDE_OFFSET) return;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        SatchelData satchelData = SatchelData.get(player);
        SatchelTier tier = satchelData.getCurrentTier();
        if (tier == null) return;

        // 1-based, matching SatchelsClientConfig#getSlotStart's convention.
        int position = satchelData.getHotbarOffset() + 1;
        if (!satchels$isCornerSafeAtPosition(tier, position)) return;

        ModSprites.Sprite sprite = ModSprites.getInventorySprite(tier);

        int satchelXOffset = satchelData.getHotbarOffset() * 18;
        int x = left + 2 + satchelXOffset;

        // Pinned to the panel's own clip line (top + height), never moves with the tween.
        int clipTop = top + height - 1;

        // Same expression renderSatchelInventory uses, so both passes sample the sprite
        // identically — only the clipped rect differs between the two passes.
        int spriteY = top + height - (int) satchelYOffset - 1;

        int invTint = ARGB.color(ARGB.alpha(lastColor), ARGB.red(lastColor), ARGB.green(lastColor), ARGB.blue(lastColor));

        graphics.enableScissor(x, clipTop, x + 1, clipTop + 1);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite.id(), x, spriteY, sprite.width(), sprite.height(), invTint);
        graphics.disableScissor();
    }

    /**
     * Whether {@code tier}'s satchel row, at the given 1-based hotbar slot-start {@code
     * position}, has the corner pixel landing back on the panel's own notch rather than
     * bleeding onto the Survival GUI's hotbar border. Netherite always returns {@code true}:
     * its only valid slot-start is 1, so the corner is always at the real left edge.
     */
    private static boolean satchels$isCornerSafeAtPosition(SatchelTier tier, int position) {
        return switch (tier) {
            case GOLDEN -> position <= 1;
            case DIAMOND -> position <= 1;
            case NETHERITE -> true;
        };
    }

    /**
     * The current, frame-by-frame animated Y offset of the inventory-screen satchel row (0 =
     * fully shown, {@link #INVENTORY_HIDE_OFFSET} = fully retracted). {@code
     * InventoryScreenMixin} reads this each frame to slide {@code SatchelInventorySlot} item
     * icons in lockstep with the background sprite instead of them popping in/out.
     */
    public float getInventoryYOffset() {
        return satchelYOffset;
    }

    /**
     * Use to determine if a click is within the satchel.
     * @param x The x-position of the mouse.
     * @param y the y-position of the mouse.
     * @param left The left edge of the bounds (when {@link SatchelData#getHotbarOffset()} is 0).
     * @param top The position of the top edge of your screen.
     * @param height The height of your screen.
     */
    public static boolean hasClickedOutside(double x, double y, int left, int top, int height) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return true;

        SatchelData satchelData = SatchelData.get(player);
        if (!satchelData.canAccess()) return true;

        SatchelTier tier = satchelData.getCurrentTier();
        if (tier == null) return true;

        int offset = satchelData.getHotbarOffset();
        int spriteWidth = ModSprites.getInventorySprite(tier).width();

        int finalLeft = left + (offset * 18);
        boolean clickedLeft = x < finalLeft;
        boolean clickedRight = x >= finalLeft + 2 + spriteWidth;
        boolean clickedBelow = y >= top + height + 26;
        return clickedLeft || clickedRight || clickedBelow;
    }

    /**
     * The current, frame-by-frame animated X offset of the vanilla-inventory
     * {@code SatchelEquipmentSlot} indicator (0 = fully shown, {@code -27} = fully retracted).
     * {@code AbstractContainerScreenMixin} reads this each frame to slide the icon in lockstep
     * with the background sprite — same purpose as {@link #getInventoryYOffset()}.
     */
    public float getSlotXOffset() {
        return slotXOffset;
    }

    /** For use in the vanilla slot handler only. */
    @ApiStatus.Internal
    public void renderSatchelSlot(GuiGraphicsExtractor graphics, int left, int top, int width, int height) {
        if (!SatchelsCompat.VANILLA.isLoaded()) return;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        SatchelData data = SatchelData.get(player);
        ItemStack carried = player.containerMenu.getCarried();
        SatchelEquipmentSlot slot = (SatchelEquipmentSlot) player.containerMenu.slots.stream()
                .filter(s -> s instanceof SatchelEquipmentSlot)
                .findFirst()
                .orElse(null);

        if (slot == null) return;

        ItemStack slotHeld = slot.getItem();
        boolean shown = carried.is(ModTags.SATCHEL) || (
                data.getSatchelInventory().isEmpty() &&
                        slotHeld.is(ModTags.SATCHEL)
        );

        int offsetGoal = -27;

        if (slotXOffset == -1) {
            slotXOffset = shown ? 0 : offsetGoal;
        }

        boolean stateChanged = lastSlotState != shown;
        long currentTime = Util.getMillis();

        if (stateChanged) {
            slotTweenStartTime = currentTime;
            slotTweenEndTime = currentTime + 300;
            xOffsetOnChange = slotXOffset;
            lastSlotState = shown;
        }

        float progress = LerpHelper.getProgress(currentTime, slotTweenStartTime, slotTweenEndTime);
        if (SatchelsClientConfig.shouldAnimateGUI()) slotXOffset = (int) LerpFunctions.EXPONENTIAL.lerp(progress, xOffsetOnChange, shown ? 0 : offsetGoal);
        else slotXOffset = shown ? 0 : offsetGoal;
        if (slotXOffset == offsetGoal) return;


        int x = left + width + (int) slotXOffset - 1;
        int y = top + height - 30;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ModSprites.SATCHEL_SLOT_INVENTORY, x, y, 27, 28);
        if (slotHeld.isEmpty()) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ModSprites.SATCHEL_SLOT_ICON, x + 5, y + 6, 16, 16);
    }
}
