package net.hotbar.satchels;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.hotbar.satchels.content.satchel.SatchelItem;
import net.hotbar.satchels.content.satchel.SatchelTier;

import java.util.List;

/**
 * Item registration. Items are registered directly via {@link Registry#register}
 * (the Fabric-recommended approach for 1.21.1).
 * <p>
 * Three separate {@link SatchelItem} instances, one per {@link SatchelTier}. There is no single
 * generic "satchel" item — each tier is its own registered item.
 * <p>
 * 26.1: {@code Item}'s constructor now eagerly requires an id on its {@code Properties} —
 * confirmed via the crash trace itself: {@code Item.<init>} calls
 * {@code Properties#effectiveDescriptionId()}, which calls {@code Properties#itemIdOrThrow()},
 * which NPEs if {@code setId(ResourceKey<Item>)} was never called. Previously the id only
 * mattered at {@code Registry.register} time, so building the {@code Properties}/{@code Item}
 * first and registering after (the old order here) worked fine — now the key has to exist
 * before the {@code Item} is even constructed, so {@code setId(...)} moved onto the
 * {@code Properties} chain and the same {@link ResourceKey} is reused for registration itself.
 */
public class ModItems {
    public static final SatchelItem SATCHEL_GOLDEN = register(SatchelTier.GOLDEN);
    public static final SatchelItem SATCHEL_DIAMOND = register(SatchelTier.DIAMOND);
    public static final SatchelItem SATCHEL_NETHERITE = register(SatchelTier.NETHERITE);

    /** All three satchel tiers, golden-to-netherite — for datagen/registration loops. */
    public static final List<SatchelItem> ALL_SATCHELS = List.of(SATCHEL_GOLDEN, SATCHEL_DIAMOND, SATCHEL_NETHERITE);

    private static SatchelItem register(SatchelTier tier) {
        Identifier id = Satchels.at(tier.getItemPath());
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        SatchelItem item = new SatchelItem(tier, new Item.Properties().stacksTo(1).setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    /** Called from {@link Satchels#onInitialize()} to force this class to load, triggering
     *  the static field initializers above (that's where the actual registration happens). */
    public static void register() {
    }
}
