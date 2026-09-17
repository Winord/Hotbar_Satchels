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
 * Generates item tags for all three satchel tiers, including the {@code trinkets:chest/satchel}
 * cross-mod "slot tag" that Trinkets Updated reads (via {@code data/trinkets/tags/items/chest/
 * satchel.json}) to decide which items are valid in its {@code chest/satchel} slot. Kept
 * populated even while Trinkets is archived (see {@code satchels-port-decisions-26_1.md}) so
 * re-enabling it needs no datagen changes. {@link FabricTagsProvider} supports tags in another
 * mod's namespace directly — standard cross-compat pattern, no extra setup needed.
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
        // ItemTags.DYEABLE doesn't exist; closest vanilla tag is CAULDRON_CAN_REMOVE_DYE.
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