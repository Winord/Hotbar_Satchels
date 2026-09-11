package net.hotbar.satchels.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ModelLocationUtils;
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
 * See {@code assets/satchels/items/satchel_worn_*.json} and {@code SatchelLayer} for how these
 * are wired up (no explicit registration step needed in 26.1 — see
 * {@code SatchelTier#getWornModelId} for why).
 * <p>
 * Note (26.1 port): datagen model classes moved from {@code net.minecraft.data.models} to
 * {@code net.minecraft.client.data.models} as part of the 26.1 unobfuscation restructure.
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
            // 26.1: generateLayeredItem's texture params changed from Identifier to Material
            // (net.minecraft.client.resources.model.sprite.Material) — confirmed via javap.
            // Material is just a thin record wrapper around the sprite Identifier.
            itemModelGenerator.generateLayeredItem(
                    ModelLocationUtils.getModelLocation(satchel),
                    new net.minecraft.client.resources.model.sprite.Material(Satchels.at("item/satchel")),
                    new net.minecraft.client.resources.model.sprite.Material(satchel.getTier().getClipTexture())
            );
        }
    }

    // 26.1 port: FabricModelProvider now requires this third abstract method (per the current
    // Fabric docs' 26.1.2 model-generation example). If the compiler still can't find
    // FabricModelProvider itself in net.fabricmc.fabric.api.datagen.v1.provider at all (not
    // just complaining about a missing override), that's most likely gradle.properties pinning
    // too old a Fabric API build for 26.1 — bump `fabric_version` per decisions §5/§6 before
    // assuming the import path itself is wrong.
    @Override
    public @NotNull String getName() {
        return "Hotbar Satchels Models";
    }
}
