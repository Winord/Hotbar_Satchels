package net.hotbar.satchels.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
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
 * {@code RenderLayer} operates on {@link AvatarRenderState} snapshots rather than a live
 * {@code Entity}, via {@code submit()}. The satchel stack is looked up from the live player
 * entity by the render state's numeric id instead, since {@code AvatarRenderState} doesn't
 * carry a satchel field of its own — safe since this only runs client-side.
 */
@Environment(EnvType.CLIENT)
public class SatchelLayer<S extends AvatarRenderState, M extends EntityModel<S>>
        extends RenderLayer<S, M> {

    private final ItemModelResolver itemModelResolver;

    public SatchelLayer(RenderLayerParent<S, M> renderLayerParent, ItemModelResolver itemModelResolver) {
        super(renderLayerParent);
        this.itemModelResolver = itemModelResolver;
    }

    @Override
    public void submit(@NotNull PoseStack poseStack,
                       @NotNull SubmitNodeCollector submitNodeCollector,
                       int lightCoords,
                       @NotNull S state,
                       float yRot,
                       float xRot) {
        if (!SatchelsClientConfig.shouldRenderSatchel()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(state.id);
        if (!(entity instanceof Player player)) return;
        if (!SatchelAccess.satchelIsVisible(player)) return;

        M entityModel = getParentModel();
        if (!(entityModel instanceof HumanoidModel<?> model)) return;

        ItemStack satchelStack = SatchelAccess.getSatchelVisualStack(player);
        if (!(satchelStack.getItem() instanceof SatchelItem satchelItem)) return;

        Identifier wornModelId = satchelItem.getTier().getWornModelId();
        ItemModel wornModel = mc.getModelManager().getItemModel(wornModelId);
        if (wornModel == null) return;

        poseStack.pushPose();

        // Position on the player's back (body-relative transform).
        model.body.translateAndRotate(poseStack);
        poseStack.translate(0, 4 / 16f, 0);
        poseStack.scale(-1, -1, 1);

        // Bakes the worn-model geometry via ItemModel#update directly, not
        // ItemModelResolver#updateForTopItem — that would resolve satchelStack's own
        // inventory-icon model instead of the worn geometry.
        ItemStackRenderState renderState = new ItemStackRenderState();
        wornModel.update(
                renderState,
                satchelStack,
                itemModelResolver,
                ItemDisplayContext.HEAD,
                mc.level,
                player,
                player.getId()
        );

        renderState.submit(poseStack, submitNodeCollector, lightCoords,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, state.outlineColor);

        poseStack.popPose();
    }
}
