package net.hotbar.satchels.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
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
 * <b>Fabric vs. NeoForge model resolution:</b> the original (NeoForge) mod resolved the worn
 * model via {@code itemRenderer.renderStatic(player, stack, ItemDisplayContext.HEAD, ...)},
 * relying on a NeoForge-only {@code neoforge:separate_transforms} model loader to redirect the
 * HEAD perspective to a separate worn-model geometry. Fabric has no equivalent loader, so that
 * approach here would just render the flat 2D item icon with the default HEAD transform (like a
 * pumpkin on the head).
 * <p>
 * Fix: each tier's {@code satchel_worn_<tier>.json} model is registered as an extra model via
 * Fabric's Model Loading API ({@code SatchelsClient} registers a {@code ModelLoadingPlugin}
 * calling {@code context.addModels(...)} for all three tier ids — without this they'd never be
 * baked at all, since nothing else references them). The tier matching the currently-equipped
 * satchel is fetched here as an already-baked {@code BakedModel} via
 * {@code FabricBakedModelManager} and rendered directly through the
 * {@code itemRenderer.render(..., bakedModel)} overload, which skips normal stack-to-model
 * resolution entirely. If porting to NeoForge or a future Fabric API with different model-loading
 * hooks, this whole bypass may no longer be necessary.
 */
@Environment(EnvType.CLIENT)
public class SatchelLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final ItemRenderer itemRenderer;

    public SatchelLayer(RenderLayerParent<T, M> renderLayerParent, ItemRenderer itemRenderer) {
        super(renderLayerParent);
        this.itemRenderer = itemRenderer;
    }

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int light, @NotNull T entity, float yaw, float pitch, float partialTicks, float j, float k, float l) {
        if (!(entity instanceof Player player)) return;
        if (!SatchelsClientConfig.shouldRenderSatchel()) return;
        if (!SatchelAccess.satchelIsVisible(player)) return;

        M entityModel = getParentModel();
        if (!(entityModel instanceof HumanoidModel<?> model))
            return;

        ItemStack satchelStack = SatchelAccess.getSatchelVisualStack(player);
        if (!(satchelStack.getItem() instanceof SatchelItem satchelItem)) return;

        ResourceLocation wornModelId = satchelItem.getTier().getWornModelId();
        BakedModel wornModel = ((FabricBakedModelManager) Minecraft.getInstance().getModelManager()).getModel(wornModelId);
        if (wornModel == null) return;

        poseStack.pushPose();

        model.body.translateAndRotate(poseStack);
        poseStack.translate(0, 4 / 16f, 0);
        poseStack.scale(-1, -1, 1);

        itemRenderer.render(
                satchelStack,
                ItemDisplayContext.HEAD,
                false,
                poseStack,
                buffer,
                light,
                OverlayTexture.NO_OVERLAY,
                wornModel
        );

        poseStack.popPose();
    }
}
