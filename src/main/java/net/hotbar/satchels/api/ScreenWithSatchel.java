package net.hotbar.satchels.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.FastColor;
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
     * Fully-retracted Y offset for the inventory-screen satchel row (background sprite AND,
     * as of the toggle-button follow-up fix, the {@code SatchelInventorySlot} items sliding
     * with it). Shared as a constant so {@code AbstractContainerScreenMixin} can check "is the
     * row all the way hidden" without duplicating the magic number.
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
    public void renderSatchelInventory(GuiGraphics graphics, int left, int top, int height) {
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
     *                     button, so it can hide the row on the inventory screen specifically
     *                     without touching the player's actual equipped/active satchel state.
     *                     Reuses the exact same 300ms tween as an equip/unequip state change,
     *                     since both are just changes to the same {@code enabled} boolean below.
     */
    public void renderSatchelInventory(GuiGraphics graphics, int left, int top, int height, boolean forceHidden) {
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

        // Defensive: canAccess() (used to compute `enabled` above) implies a satchel is
        // currently equipped, so currentTier should never actually be null here — but bail
        // rather than NPE if some future caller manages to reach this with forceHidden=false
        // and no satchel ever having been equipped this session.
        SatchelTier tier = satchelData.getCurrentTier();
        if (tier == null) return;
        ModSprites.Sprite sprite = ModSprites.getInventorySprite(tier);

        int satchelXOffset = satchelData.getHotbarOffset() * 18;

        int satchelTint = SatchelAccess.getSatchelTint(player);
        if (satchelTint != -1) lastColor = satchelTint;

        graphics.setColor(
                FastColor.ARGB32.red(lastColor) / 255f,
                FastColor.ARGB32.green(lastColor) / 255f,
                FastColor.ARGB32.blue(lastColor) / 255f,
                FastColor.ARGB32.alpha(lastColor) / 255f
        );
        graphics.blitSprite(sprite.id(), left + 2 + satchelXOffset, top + height - (int) satchelYOffset - 1, sprite.width(), sprite.height());
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * The current, frame-by-frame animated Y offset of the inventory-screen satchel row (0 =
     * fully shown, {@link #INVENTORY_HIDE_OFFSET} = fully retracted). Updated every call to
     * {@link #renderSatchelInventory(GuiGraphics, int, int, int, boolean)}. {@code
     * InventoryScreenMixin} reads this each frame to slide {@code SatchelInventorySlot} item
     * icons in lockstep with the background sprite, instead of them popping in/out — the same
     * way {@code SatchelHotbarOverlay} slides its background and items together inside one
     * {@code pushPose()}/{@code translate()} block.
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

        // Defensive, mirrors renderSatchelInventory: canAccess() implies a tier is set.
        SatchelTier tier = satchelData.getCurrentTier();
        if (tier == null) return true;

        int offset = satchelData.getHotbarOffset();
        int spriteWidth = ModSprites.getInventorySprite(tier).width();

        int finalLeft = left + (offset * 18);
        boolean clickedLeft = x < finalLeft;
        // "+ 2" mirrors the same inset renderSatchelInventory blits the sprite at (left + 2 + xOffset).
        boolean clickedRight = x >= finalLeft + 2 + spriteWidth;
        // Height (27px) is the same across all three tiers, so this boundary doesn't need to vary by tier.
        boolean clickedBelow = y >= top + height + 26;
        return clickedLeft || clickedRight || clickedBelow;
    }

    /**
     * The current, frame-by-frame animated X offset of the vanilla-inventory
     * {@code SatchelEquipmentSlot} indicator (0 = fully shown, {@code -27} = fully retracted).
     * Updated every call to {@link #renderSatchelSlot}. {@code AbstractContainerScreenMixin}
     * reads this each frame to slide the {@code SatchelEquipmentSlot} item icon in lockstep
     * with the background sprite — same purpose as {@link #getInventoryYOffset()}, for the
     * equipment-slot indicator instead of the satchel's own inventory row.
     */
    public float getSlotXOffset() {
        return slotXOffset;
    }

    /**
     * For use in the vanilla slot handler only.
     */
    @ApiStatus.Internal
    public void renderSatchelSlot(GuiGraphics graphics, int left, int top, int width, int height) {
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
        graphics.blitSprite(ModSprites.SATCHEL_SLOT_INVENTORY, x, y, 27, 28);
        if (slotHeld.isEmpty()) graphics.blitSprite(ModSprites.SATCHEL_SLOT_ICON, x + 5, y + 6, 16, 16);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
