package net.hotbar.satchels.client.satchel;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.hotbar.satchels.api.SatchelAccess;
import net.hotbar.satchels.client.ModSprites;
import net.hotbar.satchels.client.SatchelsClientConfig;
import net.hotbar.satchels.client.animation.LerpFunctions;
import net.hotbar.satchels.client.animation.LerpHelper;
import net.hotbar.satchels.compat.SatchelsCompat;
import net.hotbar.satchels.compat.raised.RaisedCompat;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelItem;
import net.hotbar.satchels.content.satchel.SatchelTier;

import java.awt.*;

/**
 * Renders the satchel hotbar overlay (the 3/6/9-slot row below the vanilla hotbar, sized to
 * the equipped tier — see {@link SatchelTier}), including open/close slide animation,
 * selection highlight, and Raised layer-offset support. Registered via
 * {@code HudRenderCallback.EVENT} in {@code SatchelsClient.registerOverlays}.
 */
@Environment(EnvType.CLIENT)
public class SatchelHotbarOverlay {
    public static final String ID = "satchel_hotbar";
    public static final SatchelHotbarOverlay INSTANCE = new SatchelHotbarOverlay();

    private long startTime = 0;
    private long endTime = 0;
    private boolean lastState = false;
    private int yOffset = 0;
    private int yOffsetOnChange = 0;

    private int lastColor = SatchelItem.DEFAULT_COLOR;

    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR)
            return;

        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - 22;
        if (SatchelsCompat.RAISED.isLoaded()) {
            x += RaisedCompat.getSatchelXOffset();
            y += RaisedCompat.getSatchelYOffset();
        }

        Player player = mc.player;
        if (player == null) return;

        int satchelTint = SatchelAccess.getSatchelTint(player);
        if (satchelTint != -1) lastColor = satchelTint;

        SatchelData satchelData = SatchelData.get(player);

        boolean enabled = satchelData.isActive();
        boolean stateChanged = this.lastState != enabled;

        int offsetGoal = 24;
        if (SatchelsCompat.RAISED.isLoaded()) offsetGoal -= RaisedCompat.getSatchelYOffset();
        long currentTime = Util.getMillis();

        if (stateChanged) {
            startTime = currentTime;
            endTime = currentTime + 300;
            yOffsetOnChange = this.yOffset;
            lastState = enabled;
        }

        float progress = LerpHelper.getProgress(currentTime, this.startTime, this.endTime);
        if (SatchelsClientConfig.shouldAnimateGUI()) this.yOffset = (int) LerpFunctions.EXPONENTIAL.lerp(progress, this.yOffsetOnChange, enabled ? 0 : offsetGoal);
        else this.yOffset = enabled ? 0 : offsetGoal;
        if (this.yOffset == offsetGoal) return;

        // Defensive: isActive() can only become true via toggleSatchel(), which itself
        // requires canAccess() (implying a satchel is equipped) — so currentTier should never
        // actually be null here. Mirrors the same guard in ScreenWithSatchel.
        SatchelTier tier = satchelData.getCurrentTier();
        if (tier == null) return;
        ModSprites.Sprite hotbarSprite = ModSprites.getHotbarSprite(tier);

        int red = ARGB.red(lastColor);
        int green = ARGB.green(lastColor);
        int blue = ARGB.blue(lastColor);
        int alpha = ARGB.alpha(lastColor);
        // 26.1: setColor() removed. Pass tint as ARGB int directly to blitSprite.
        int hotbarTint = ARGB.color(alpha, red, green, blue);

        // 26.1: pose() returns Matrix3x2fStack (2D): pushPose→pushMatrix, translate loses Z.
        graphics.pose().pushMatrix();
        graphics.pose().translate(0f, (float) this.yOffset);

        int xOffset = satchelData.getHotbarOffset() * 20;
        // Drawn flush with the vanilla hotbar's left edge — each HOTBAR_SPRITES texture carries
        // its own 1px left border column, see ModSprites for why.
        // 26.1: blitSprite requires RenderPipeline as first arg; tint as last int arg.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hotbarSprite.id(), x + xOffset, y, hotbarSprite.width(), hotbarSprite.height(), hotbarTint);

        int selected = player.getInventory().selected;
        boolean selectedInSatchel = satchelData.isSlotInSatchel(selected);
        Identifier selectionSprite = selectedInSatchel ? ModSprites.SATCHEL_HOTBAR_SELECTION : ModSprites.VANILLA_HOTBAR_SELECTION;

        float selectionYOffset = selectedInSatchel ? 0 : -this.yOffset;

        // 26.1: pushPose→pushMatrix, translate loses Z.
        graphics.pose().pushMatrix();
        graphics.pose().translate(0f, selectionYOffset);

        int selectionTint;
        if (selectedInSatchel) {
            float[] hsb = Color.RGBtoHSB(red, green, blue, null);
            hsb[0] = Math.max(hsb[0] - 0.01f, 0f);
            hsb[1] = Math.max(hsb[1] - 0.1f, 0f);
            hsb[2] = Math.min(hsb[2] + 0.1f, 1f);

            int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
            selectionTint = ARGB.color(alpha, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
        } else {
            selectionTint = ARGB.color(255, 255, 255, 255);
        }

        // 26.1: blitSprite requires RenderPipeline as first arg; tint as last int arg.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, selectionSprite, x - 1 + (selected * 20), y - 1, 24, selectedInSatchel ? 24 : 23, selectionTint);

        graphics.pose().popMatrix();

        // Items (icon + count) are drawn last, on top of the selection-frame sprite above: the
        // frame is a hollow border, but count text for two-digit stacks overflows the 16x16
        // icon footprint into the frame's border area, and these are 2D GUI blits with no depth
        // testing — draw order alone decides which one wins.
        // 26.1: setColor() removed — no tint needed for items (rendered at full white by default).
        for (int i = 0; i < satchelData.getSatchelInventory().getContainerSize(); i++) {
            ItemStack stack = satchelData.getSatchelInventory().getItem(i);
            SatchelRenderUtils.renderSlot(graphics, x + (i * 20) + 3 + xOffset, y + 3, deltaTracker, player, stack, i + 1);
        }

        graphics.pose().popMatrix();

        // 26.1: flush() removed — rendering is deferred automatically.
    }
}
