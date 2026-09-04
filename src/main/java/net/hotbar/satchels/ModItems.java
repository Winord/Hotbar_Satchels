package net.hotbar.satchels;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
 */
public class ModItems {
    public static final SatchelItem SATCHEL_GOLDEN = register(SatchelTier.GOLDEN);
    public static final SatchelItem SATCHEL_DIAMOND = register(SatchelTier.DIAMOND);
    public static final SatchelItem SATCHEL_NETHERITE = register(SatchelTier.NETHERITE);

    /** All three satchel tiers, golden-to-netherite — for datagen/registration loops. */
    public static final List<SatchelItem> ALL_SATCHELS = List.of(SATCHEL_GOLDEN, SATCHEL_DIAMOND, SATCHEL_NETHERITE);

    private static SatchelItem register(SatchelTier tier) {
        ResourceLocation id = Satchels.at(tier.getItemPath());
        SatchelItem item = new SatchelItem(tier, new Item.Properties().stacksTo(1));
        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }

    /** Called from {@link Satchels#onInitialize()} to force this class to load, triggering
     *  the static field initializers above (that's where the actual registration happens). */
    public static void register() {
    }
}
