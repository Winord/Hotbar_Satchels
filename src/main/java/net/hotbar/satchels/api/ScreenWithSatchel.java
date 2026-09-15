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
     *                     button, so it can hide the row on the inventory screen specifically
     *                     without touching the player's actual equipped/active satchel state.
     *                     Reuses the exact same 300ms tween as an equip/unequip state change,
     *                     since both are just changes to the same {@code enabled} boolean below.
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

        // 26.1: setColor() removed — pass tint as ARGB int to blitSprite directly.
        int invTint = ARGB.color(ARGB.alpha(lastColor), ARGB.red(lastColor), ARGB.green(lastColor), ARGB.blue(lastColor));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite.id(), left + 2 + satchelXOffset, top + height - (int) satchelYOffset - 1, sprite.width(), sprite.height(), invTint);
    }

    /**
     * Second clip pass for the satchel row's top-left corner pixel — the "tuck" pixel that
     * completes the main panel's bottom-left border corner.
     * <p>
     * Row 0 of every {@code satchel_inventory_<tier>.png} is fully transparent except for one
     * opaque pixel in each top corner (verified in the PNGs themselves: golden is transparent
     * across row 0 except x=0 and x=63, same shape at 118/172 px wide for diamond/netherite).
     * That row sits exactly 1px above the main pass's clip line in {@link #renderSatchelInventory},
     * so the main pass structurally cannot put it on screen — and the clip line cannot simply be
     * raised by 1px either, because that shifts the visibility threshold for *every* row of the
     * sprite (row r becomes visible at r >= satchelYOffset - 1) and leaks a full extra row of the
     * row's real body above the panel on every frame of the tween.
     * <p>
     * Hence two scissors instead of one, applied one after the other, both running on every frame
     * the row renders:
     * <ol>
     *   <li>the main horizontal clip — everything at or below the panel's bottom edge, full screen
     *       width — draws the row body;</li>
     *   <li>this one — a 1px-wide vertical clip at the row's own left edge whose top edge is
     *       exactly 1px higher, i.e. the single cell at {@code (x, top + height - 1)} — draws
     *       nothing but the corner.</li>
     * </ol>
     * The two regions are deliberately disjoint (this one is exactly 1px tall and does not extend
     * down into the main pass's region), so no pixel is ever blitted twice and a non-opaque
     * satchel tint cannot double-blend along the seam.
     * <p>
     * This pass is unconditional by design: it is NOT gated on the row being at rest, and it blits
     * the sprite at the same animated {@code satchelYOffset} position the main pass uses. That is
     * what makes it flash-free — there is no visibility state to switch, the corner cell simply
     * shows whatever the sliding sprite happens to have at that spot. Mid-tween that is the
     * sprite's left border column, which is the same colour as the corner pixel itself (66,66,66
     * before tint, verified in all three PNGs), so the corner stays visually attached to the panel
     * while the row slides; it then disappears on its own near the end of the retract because the
     * sprite's last rows are transparent at column 0 (rows 25-26). The previous gated version —
     * fire only at {@code satchelYOffset == 0}, with a short alpha fade to soften the pop — is
     * exactly what produced the visible flash on open/toggle, and has been dropped along with its
     * fade window.
     * <p>
     * Only the left corner is drawn, on purpose: the mirrored pixel in the sprite's top-right
     * corner has no matching notch on the panels this mod targets, so drawing it would add a stray
     * pixel instead of completing anything. It is being removed from the textures themselves; until
     * then the 1px-wide scissor here keeps it unreachable regardless.
     * <p>
     * <b>Only correct at the hotbar's own left edge.</b> Everything above assumes the corner
     * pixel is landing on the actual bottom-left notch of the main panel's border — true when
     * {@code hotbarOffset == 0}. The per-tier hotbar slot-start setting ({@code
     * SatchelsClientConfig#getSlotStart}) lets the row start anywhere from slot 1 up to {@code
     * 9 - slotCount + 1}, and {@code x} below slides right by 18px per step right along with it.
     * Past a certain shift there is no notch under that pixel anymore — it lands on a plain
     * stretch of the Survival GUI's own hotbar border, where the sprite's corner colour doesn't
     * belong and reads as a stray pixel bleeding onto vanilla UI instead of completing anything.
     * {@link #satchels$isCornerSafeAtPosition} gates the whole pass on the 1-based slot-start
     * position staying inside the range each tier's panel art was actually drawn to tuck into —
     * Golden 1-3, Diamond 1-6, Netherite always (it has no room to shift: {@code slotCount == 9}
     * pins its only valid position to 1). Outside that range this pass is skipped entirely and
     * the row renders exactly as the main pass alone leaves it — corner cropped, same as before
     * this method existed — rather than drawing something in the wrong place.
     */
    @ApiStatus.Internal
    public void renderSatchelInventoryCorners(GuiGraphicsExtractor graphics, int left, int top, int height) {
        // Neither of these is a visibility toggle — both are states in which this pass would
        // provably draw nothing, so skipping the scissor/blit churn changes no pixel:
        //   satchelYOffset == -1  -> renderSatchelInventory has never run for this screen yet, so
        //                            there is no row on screen whose corner needs completing;
        //   >= INVENTORY_HIDE_OFFSET -> fully-retracted rest state, where the main pass itself
        //                            early-returns and the sprite no longer covers this cell.
        if (satchelYOffset < 0 || satchelYOffset >= INVENTORY_HIDE_OFFSET) return;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        SatchelData satchelData = SatchelData.get(player);
        SatchelTier tier = satchelData.getCurrentTier();
        if (tier == null) return;

        // 1-based, matching SatchelsClientConfig#getSlotStart's own convention — offset 0 is
        // "starts at slot 1".
        int position = satchelData.getHotbarOffset() + 1;
        if (!satchels$isCornerSafeAtPosition(tier, position)) return;

        ModSprites.Sprite sprite = ModSprites.getInventorySprite(tier);

        int satchelXOffset = satchelData.getHotbarOffset() * 18;
        int x = left + 2 + satchelXOffset;

        // Clip cell — pinned to the panel, never moves with the tween. `top + height` is the main
        // pass's own clip line (the caller has already folded overlayOffset into `top`, so this
        // stays flush with it for every allowed_menus entry, offset or not), and -1 is the single
        // row above it that the main pass can never reach.
        int clipTop = top + height - 1;

        // Blit position — deliberately the same expression renderSatchelInventory uses, so both
        // passes sample the same sprite at the same place every frame; the only thing that differs
        // between the two passes is which rect is clipped.
        int spriteY = top + height - (int) satchelYOffset - 1;

        // lastColor is already fresh for this frame — renderSatchelInventory (called earlier in
        // the same frame, before this) always updates it first.
        int invTint = ARGB.color(ARGB.alpha(lastColor), ARGB.red(lastColor), ARGB.green(lastColor), ARGB.blue(lastColor));

        graphics.enableScissor(x, clipTop, x + 1, clipTop + 1);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite.id(), x, spriteY, sprite.width(), sprite.height(), invTint);
        graphics.disableScissor();
    }

    /**
     * Whether {@code tier}'s satchel row, at the given 1-based hotbar slot-start {@code
     * position} ({@code SatchelsClientConfig#getSlotStart} convention — 1 is the default,
     * left-most start), has the corner pixel landing back on the panel's own notch rather than
     * bleeding onto a plain stretch of the Survival GUI's hotbar border. See the caller's
     * javadoc for why the pixel stops being correct past a certain shift.
     * <p>
     * Netherite always returns {@code true}: {@code slotCount == 9} means {@code
     * SatchelsClientConfig#getMaxSlotStart} is 1 — there is no other position to be at, so the
     * corner is always exactly at the real left edge, same as before offsets existed at all.
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
     * fully shown, {@link #INVENTORY_HIDE_OFFSET} = fully retracted). Updated every call to
     * {@link #renderSatchelInventory(GuiGraphicsExtractor, int, int, int, boolean)}. {@code
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
        // 26.1: blitSprite requires RenderPipeline as first arg.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ModSprites.SATCHEL_SLOT_INVENTORY, x, y, 27, 28);
        if (slotHeld.isEmpty()) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ModSprites.SATCHEL_SLOT_ICON, x + 5, y + 6, 16, 16);
        // 26.1: setColor() removed — not needed after blitSprite (no persistent state).
    }
}