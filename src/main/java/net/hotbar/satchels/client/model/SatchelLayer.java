package net.hotbar.satchels.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.hotbar.satchels.api.SatchelAccess;
import net.hotbar.satchels.client.SatchelsClientConfig;
import net.hotbar.satchels.content.satchel.SatchelItem;
import org.jetbrains.annotations.NotNull;

/**
 * Renders the equipped satchel on the player's back.
 * <p>
 * <b>1.21.4 render state migration:</b> {@code RenderLayer} now uses
 * {@code EntityRenderState} as its type parameter instead of the entity directly.
 * For players, this is {@code PlayerRenderState}. The entity is not available in
 * {@code render()} — we resolve the live {@code Player} from the client world via
 * {@code PlayerRenderState.id}, which is an {@code int} (entity network ID, NOT UUID).
 * Use {@code Level.getEntity(int)} and cast to {@code Player}.
 * <p>
 * <b>ItemRenderer changes in 1.21.4:</b> the old
 * {@code render(ItemStack, ItemDisplayContext, boolean, PoseStack, MultiBufferSource, int, int, BakedModel)}
 * overload was removed. The replacement is {@code renderStatic(ItemStack, ItemDisplayContext,
 * int, int, PoseStack, MultiBufferSource, Level, int)}, which resolves the model internally.
 * The worn model is still used because it is registered as an extra model in
 * {@code SatchelsClient#registerExtraModels} and referenced in the item definition JSON
 * under {@code ItemDisplayContext.HEAD} — {@code renderStatic} with HEAD context picks it up.
 * The {@code FabricBakedModelManager} import is therefore no longer needed here.
 */
@Environment(EnvType.CLIENT)
public class SatchelLayer extends RenderLayer<PlayerRenderState, PlayerModel> {

    public SatchelLayer(RenderLayerParent<PlayerRenderState, PlayerModel> renderLayerParent) {
        super(renderLayerParent);
    }

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int light,
                       @NotNull PlayerRenderState renderState, float yaw, float pitch) {
        if (!SatchelsClientConfig.shouldRenderSatchel()) return;

        // PlayerRenderState.id is an int (entity network ID), not a UUID.
        // Use Level.getEntity(int) instead of Level.getPlayerByUUID(UUID).
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(renderState.id);
        if (!(entity instanceof Player player)) return;

        if (!SatchelAccess.satchelIsVisible(player)) return;

        PlayerModel model = getParentModel();

        ItemStack satchelStack = SatchelAccess.getSatchelVisualStack(player);
        if (!(satchelStack.getItem() instanceof SatchelItem)) return;

        poseStack.pushPose();

        model.body.translateAndRotate(poseStack);
        poseStack.translate(0, 4 / 16f, 0);
        poseStack.scale(-1, -1, 1);

        // render(ItemStack, ..., BakedModel) was removed in 1.21.4.
        // renderStatic resolves the model from the item definition's HEAD transform,
        // which maps to the satchel_worn_<tier> model registered via registerExtraModels().
        mc.getItemRenderer().renderStatic(
                satchelStack,
                ItemDisplayContext.HEAD,
                light,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                mc.level,
                0
        );

        poseStack.popPose();
    }
}
