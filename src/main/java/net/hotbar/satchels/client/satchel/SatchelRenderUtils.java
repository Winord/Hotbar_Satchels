package net.hotbar.satchels.client.satchel;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.RenderPipelines;
@Environment(EnvType.CLIENT)
public class SatchelRenderUtils {
    public static void renderSlot(GuiGraphicsExtractor guiGraphics, int x, int y, DeltaTracker deltaTracker, Player player, ItemStack itemStack, int k) {
        if (!itemStack.isEmpty()) {
            float f = itemStack.getPopTime() - deltaTracker.getGameTimeDeltaPartialTick(false);
            if (f > 0.0F) {
                // 26.1: pose() returns org.joml.Matrix3x2fStack (2D), not PoseStack.
                // pushPose/popPose → pushMatrix/popMatrix; translate/scale lose Z component.
                float g = 1.0F + f / 5.0F;
                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().translate((float) (x + 8), (float) (y + 12));
                guiGraphics.pose().scale(1.0F / g, (g + 1.0F) / 2.0F);
                guiGraphics.pose().translate((float) (-(x + 8)), (float) (-(y + 12)));
            }

            // 26.1: renderItem → item(ItemStack, x, y); renderItemDecorations → itemDecorations
            guiGraphics.item(itemStack, x, y);
            if (f > 0.0F) {
                guiGraphics.pose().popMatrix();
            }

            guiGraphics.itemDecorations(Minecraft.getInstance().font, itemStack, x, y);
        }
    }
}
