package net.hotbar.satchels.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.hotbar.satchels.ModItems;
import net.hotbar.satchels.ModTags;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Generates item tags for all three satchel tiers.
 * Replaces the NeoForge {@code ItemTagGen}; {@code curios:satchel} replaced by
 * {@code accessories:satchel} on the 1.21.1 branch, and by {@code trinkets:chest/satchel}
 * on 26.x (Trinkets Updated — see {@code satchels-port-decisions-26_1.md} §3, dev-brief §6).
 *
 * Cross-mod tags in another namespace ({@code trinkets:chest/satchel}) are fully
 * supported by {@link FabricTagsProvider} — standard cross-compat pattern. This is the
 * "slot tag" Trinkets reads from {@code data/trinkets/tags/items/chest/satchel.json}
 * (see https://github.com/emilyploszaj/trinkets/wiki/Trinkets-Data-Formats) to decide which
 * items are valid in the {@code chest/satchel} slot registered by
 * {@code data/trinkets/entities/player.json} / {@code data/trinkets/slots/chest/satchel.json}.
 */
public class SatchelsItemTagProvider extends FabricTagsProvider.ItemTagsProvider {
    private static final TagKey<Item> TRINKETS_CHEST_SATCHEL = TagKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath("trinkets", "chest/satchel")
    );

    public SatchelsItemTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(@NotNull HolderLookup.Provider provider) {
        // 26.1: ItemTags.DYEABLE doesn't exist in vanilla anymore (confirmed via javap) —
        // closest vanilla tag is CAULDRON_CAN_REMOVE_DYE. FabricTagsProvider's tag(...) helper
        // was also renamed to builder(...) (confirmed against the real Fabric API 26.1.2 branch
        // source) — same TagAppender<ResourceKey<Item>, Item> shape, just a different method name.
        var dyeable = builder(ItemTags.CAULDRON_CAN_REMOVE_DYE);
        var satchels = builder(ModTags.SATCHEL);
        for (var satchel : ModItems.ALL_SATCHELS) {
            dyeable.add(satchel.builtInRegistryHolder().key());
            satchels.add(satchel.builtInRegistryHolder().key());
        }

        builder(TRINKETS_CHEST_SATCHEL)
                .addTag(ModTags.SATCHEL);
    }
}