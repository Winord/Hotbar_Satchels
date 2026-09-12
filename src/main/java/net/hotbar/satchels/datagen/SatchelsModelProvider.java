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
 * <p>
 * 26.1 port bug (missing-texture item icon): confirmed via {@code javap -c} that
 * <b>neither</b> {@code generateLayeredItem} overload touches {@code itemModelOutput} — both
 * only ever write the raw geometry model ({@code assets/<ns>/models/item/<id>.json}) through
 * the low-level {@code modelOutput} consumer. In 26.1 that raw model is no longer
 * auto-associated with the registered {@code Item} as its icon; something has to explicitly
 * call {@code itemModelOutput.accept(item, unbakedModel)} to write the actual client-item
 * wrapper ({@code assets/<ns>/items/<id>.json}) that {@code ClientItemInfoLoader} looks up —
 * exactly the same wrapper shape already hand-authored for the worn models (see
 * {@code assets/satchels/items/satchel_worn_golden.json}). {@code generateFlatItem}'s own
 * disassembly is what shows the correct pattern
 * ({@code itemModelOutput.accept(item, ItemModelUtils.plainModel(modelId))}); it's just not
 * something {@code generateLayeredItem} does for you, so it has to be added explicitly here.
 * Without this, the three satchel items had a perfectly correct model+textures on disk that
 * nothing ever pointed the registered {@code Item} at — hence the pink/black missing-texture
 * icon in inventory/hotbar despite no crash and no missing-file warning.
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
            Identifier modelId = itemModelGenerator.generateLayeredItem(
                    ModelLocationUtils.getModelLocation(satchel),
                    new net.minecraft.client.resources.model.sprite.Material(Satchels.at("item/satchel")),
                    new net.minecraft.client.resources.model.sprite.Material(satchel.getTier().getClipTexture())
            );
            // The call above only ever writes the raw geometry model. Without this, nothing
            // ever tells the registered Item to actually use it — see the class javadoc.
            //
            // Bug fix: default color was rendering white, not brown. 26.1 removed the old
            // Java-side ColorProviderRegistry.ITEM entirely — item tinting is now declared on
            // the model itself via a tint_source (confirmed against the real vanilla
            // leather_helmet item wrapper: {"type": "minecraft:model", "model": ...,
            // "tints": [{"type": "minecraft:dye", "default": <argb>}]}). tintedModel's tints
            // list maps to texture layers by index — passing exactly one Dye tint here applies
            // it to layer0 only (the shared dyeable body), leaving layer1 (the tier clip)
            // untinted, matching the two-layer split this model already uses.
            itemModelGenerator.itemModelOutput.accept(
                    satchel,
                    ItemModelUtils.tintedModel(modelId, new Dye(SatchelItem.DEFAULT_COLOR))
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
