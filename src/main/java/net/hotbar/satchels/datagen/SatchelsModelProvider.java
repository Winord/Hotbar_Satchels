package net.hotbar.satchels.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.hotbar.satchels.ModItems;
import net.hotbar.satchels.Satchels;
import net.hotbar.satchels.content.satchel.SatchelItem;
import org.jetbrains.annotations.NotNull;

/**
 * Generates one two-layer {@code minecraft:item/generated} model per satchel tier:
 * layer0 = shared dyeable body ({@code satchels:item/satchel}),
 * layer1 = tier clip ({@link net.hotbar.satchels.content.satchel.SatchelTier#getClipTexture()}).
 * <p>
 * Texture locations are built from {@code SatchelTier} directly rather than using
 * {@code TextureMapping.getItemTexture(item, suffix)} — that helper would look for
 * {@code satchel_golden_clip}, but the actual asset names are {@code satchel_clip_golden}
 * (reversed) and layer0 is shared rather than per-item.
 * <p>
 * {@code satchel_worn_<tier>.json} are left static — they are custom Blockbench geometry
 * (arbitrary elements/rotations/UVs), not flat templated models. {@code ItemModelGenerators}
 * can only produce flat generated/handheld models; regenerating the worn models in code
 * would mean manually transcribing the same element data already in the json with no benefit.
 * See {@code SatchelsClient#registerExtraModels} and {@code SatchelLayer} for how these are used.
 */
public class SatchelsModelProvider extends FabricModelProvider {
    public SatchelsModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(@NotNull BlockModelGenerators blockStateModelGenerator) {
        // No registered blocks in this mod.
    }

    @Override
    public void generateItemModels(@NotNull ItemModelGenerators itemModelGenerator) {
        for (SatchelItem satchel : ModItems.ALL_SATCHELS) {
            itemModelGenerator.generateLayeredItem(
                    ModelLocationUtils.getModelLocation(satchel),
                    Satchels.at("item/satchel"),
                    satchel.getTier().getClipTexture()
            );
        }
    }
}
