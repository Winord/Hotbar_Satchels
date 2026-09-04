package net.hotbar.satchels.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
 * {@code accessories:satchel} (no Fabric Curios port — see dev-brief §6).
 *
 * Cross-mod tags in another namespace ({@code accessories:satchel}) are fully
 * supported by {@link FabricTagProvider} — standard cross-compat pattern.
 */
public class SatchelsItemTagProvider extends FabricTagProvider.ItemTagProvider {
    private static final TagKey<Item> ACCESSORIES_SATCHEL = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("accessories", "satchel")
    );

    public SatchelsItemTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(@NotNull HolderLookup.Provider provider) {
        // Use .add(ResourceKey<Item>) rather than .add(T): on some API versions the generic
        // overload resolves to TagAppender.add(ResourceKey<T>), so we pass the key directly
        // to stay unambiguous regardless of resolution.
        var dyeable = tag(ItemTags.DYEABLE);
        var satchels = tag(ModTags.SATCHEL);
        for (var satchel : ModItems.ALL_SATCHELS) {
            dyeable.add(satchel.builtInRegistryHolder().key());
            satchels.add(satchel.builtInRegistryHolder().key());
        }

        tag(ACCESSORIES_SATCHEL)
                .addTag(ModTags.SATCHEL);
    }
}
