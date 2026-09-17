package net.hotbar.satchels.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.minecraft.client.color.item.Dye;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.resources.Identifier;
import net.hotbar.satchels.ModItems;
import net.hotbar.satchels.Satchels;
import net.hotbar.satchels.content.satchel.SatchelItem;
import org.jetbrains.annotations.NotNull;

/**
 * Generates one two-layer {@code minecraft:item/generated} model per satchel tier:
 * layer0 = shared dyeable body ({@code satchels:item/satchel}),
 * layer1 = tier clip ({@link net.hotbar.satchels.content.satchel.SatchelTier#getClipTexture()}).
 * <p>
 * Texture locations are built from {@code SatchelTier} directly rather than
 * {@code TextureMapping.getItemTexture(item, suffix)}: the actual asset names are
 * {@code satchel_clip_golden} (suffix first) and layer0 is shared rather than per-item.
 * <p>
 * {@code satchel_worn_<tier>.json} are left as static, hand-authored Blockbench geometry —
 * {@code ItemModelGenerators} only produces flat generated/handheld models. See
 * {@code assets/satchels/items/satchel_worn_*.json} and {@code SatchelLayer}.
 * <p>
 * <b>Important:</b> {@code generateLayeredItem} only writes the raw geometry model
 * ({@code assets/<ns>/models/item/<id>.json}); it does NOT register that model as the item's
 * icon. Without the explicit {@code itemModelOutput.accept(...)} call below, the satchel items
 * render as the pink/black missing-texture icon despite valid models/textures on disk.
 */
public class SatchelsModelProvider extends FabricModelProvider {
    public SatchelsModelProvider(FabricPackOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(@NotNull BlockModelGenerators blockStateModelGenerator) {
        // No registered blocks in this mod.
    }

    @Override
    public void generateItemModels(@NotNull ItemModelGenerators itemModelGenerator) {
        for (SatchelItem satchel : ModItems.ALL_SATCHELS) {
            Identifier modelId = itemModelGenerator.generateLayeredItem(
                    ModelLocationUtils.getModelLocation(satchel),
                    new net.minecraft.client.resources.model.sprite.Material(Satchels.at("item/satchel")),
                    new net.minecraft.client.resources.model.sprite.Material(satchel.getTier().getClipTexture())
            );
            // Registers the model as the item's actual icon — see the class javadoc.
            // The single Dye tint maps to layer0 (the dyeable body) only, by tint-list index;
            // layer1 (the tier clip) stays untinted.
            itemModelGenerator.itemModelOutput.accept(
                    satchel,
                    ItemModelUtils.tintedModel(modelId, new Dye(SatchelItem.DEFAULT_COLOR))
            );
        }
    }

    @Override
    public @NotNull String getName() {
        return "Hotbar Satchels Models";
    }
}
