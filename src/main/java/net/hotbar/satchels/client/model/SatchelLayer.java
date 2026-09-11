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
 *
 * <p><b>26.1 render architecture:</b> RenderLayer now operates on {@link EntityRenderState}
 * snapshots rather than live Entity references. The abstract method is {@code submit()} instead
 * of {@code render()}. For players this is {@link AvatarRenderState}.</p>
 *
 * <p>The satchel stack is looked up from the live player entity via UUID from the render state,
 * since AvatarRenderState does not carry an arbitrary satchel field (we do not mixin into it).
 * This is safe because SatchelLayer only renders on the client and the local level is always
 * available.</p>
 */
@Environment(EnvType.CLIENT)
public class SatchelLayer<S extends AvatarRenderState, M extends EntityModel<S>>
        extends RenderLayer<S, M> {

    private final ItemModelResolver itemModelResolver;

    public SatchelLayer(RenderLayerParent<S, M> renderLayerParent, ItemModelResolver itemModelResolver) {
        super(renderLayerParent);
        this.itemModelResolver = itemModelResolver;
    }

    // 26.1: RenderLayer's abstract method is submit() — PoseStack + SubmitNodeCollector + light + state + yRot + xRot.
    @Override
    public void submit(@NotNull PoseStack poseStack,
                       @NotNull SubmitNodeCollector submitNodeCollector,
                       int lightCoords,
                       @NotNull S state,
                       float yRot,
                       float xRot) {
        if (!SatchelsClientConfig.shouldRenderSatchel()) return;

        // Retrieve the live player entity from the render state UUID.
        // AvatarRenderState.id is the entity's numeric runtime ID.
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

        // Position the satchel on the player's back (body-relative transform).
        model.body.translateAndRotate(poseStack);
        poseStack.translate(0, 4 / 16f, 0);
        poseStack.scale(-1, -1, 1);

        // 26.1: bake the worn-model geometry (not the satchel's own inventory-icon model) into
        // the render state via ItemModel#update directly — the ItemModelResolver's
        // updateForTopItem() would resolve satchelStack's *own* model, which is the wrong
        // geometry here. update(state, stack, resolver, displayContext, level, itemOwner, seed)
        // — Player implements ItemOwner directly (confirmed via javap: Entity implements ItemOwner).
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

        // 26.1: ItemStackRenderState.render(...) renamed to submit(...) (confirmed via javap —
        // no render() method exists on this class anymore).
        renderState.submit(poseStack, submitNodeCollector, lightCoords,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

        poseStack.popPose();
    }
}
