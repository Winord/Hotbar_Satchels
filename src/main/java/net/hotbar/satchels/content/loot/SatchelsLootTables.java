package net.hotbar.satchels.content.loot;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.hotbar.satchels.ModItems;

/**
 * Adds the Golden Satchel as a natural, low-frequency drop in the shipwreck "treasure map"
 * chest ({@code minecraft:chests/shipwreck_map} — same chest as the buried-treasure map,
 * compass, and clock). Only Golden drops as loot — Diamond and Netherite stay strictly
 * craft/upgrade-only progression items.
 * <p>
 * Uses runtime {@link LootTableEvents#MODIFY} rather than a datagen loot-table override/replace:
 * a full-replacement json would need to duplicate vanilla's entire {@code shipwreck_map} table
 * (all 3 pools), and any future vanilla change or another mod's own patch to that file would
 * silently stop merging with ours. The event API adds an entry to whatever table ends up
 * loaded — vanilla or already patched by someone else — without duplicating its contents.
 */
public class SatchelsLootTables {
    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (key != BuiltInLootTables.SHIPWRECK_MAP || !source.isBuiltin()) return;

            // A dedicated pool with one guaranteed roll, weighted between the satchel and an
            // "empty" entry — an explicit weighted pick (1-in-8 for the satchel) rather than a
            // pool with just the satchel in it (which would make it drop every single time).
            // Same shape as vanilla's own third pool in this table (empty vs. armor-trim
            // template), just with our own weighting.
            tableBuilder.withPool(
                    LootPool.lootPool()
                            .setRolls(ConstantValue.exactly(1))
                            .add(
                                    LootItem.lootTableItem(ModItems.SATCHEL_GOLDEN)
                                            .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1)))
                                            .setWeight(1)
                            )
                            .add(EmptyLootItem.emptyItem().setWeight(7))
            );
        });
    }
}