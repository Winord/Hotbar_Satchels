package net.hotbar.satchels.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.RecipeUnlockAdvancementBuilder;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.hotbar.satchels.ModItems;
import net.hotbar.satchels.Satchels;
import net.hotbar.satchels.content.satchel.SatchelUpgradeRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Generates crafting recipes for all three satchel tiers.
 * Uses Fabric Convention Tags ({@code c:} namespace) for ingredients — same tags the
 * original NeoForge recipe already relied on.
 * <p>
 * Tier progression (confirmed with user, dev-brief §11.2.9):
 * <ul>
 *   <li><b>Golden</b> — shaped craft (strings, leathers, gold ingot).</li>
 *   <li><b>Diamond</b> — {@link SatchelUpgradeRecipe}: Golden Satchel center + 4 diamonds.
 *       Uses a custom recipe type so dye color is preserved from the input Golden Satchel.</li>
 *   <li><b>Netherite</b> — smithing-table upgrade from Diamond only (Diamond + Netherite
 *       Upgrade Smithing Template + Netherite Ingot). A non-empty Diamond Satchel can't be
 *       unequipped ({@code AccessoriesCompat.canUnequipSatchel}), so it can't be placed in
 *       the smithing ingredient slot either — no extra "must be empty" check needed here.</li>
 * </ul>
 * {@code .save(output)} / {@code output.accept(...)} auto-generates an unlock advancement
 * ({@code advancement/recipes/<category>/<id>.json}) for every recipe.
 * <p>
 * 26.1: {@code FabricRecipeProvider}'s abstract method changed shape entirely (confirmed against
 * the real Fabric API 26.1.2 branch source on GitHub) — it's no longer an override of vanilla's
 * {@code buildRecipes(RecipeOutput)}. The new hook is {@code createRecipeProvider(HolderLookup
 * .Provider, RecipeOutput)}, which must return a fresh vanilla {@code RecipeProvider} instance;
 * that instance's own no-arg {@code buildRecipes()} is what actually gets called. All the
 * per-recipe logic below moved into an anonymous subclass for that reason — it's also why
 * {@code has(...)} and {@code netheriteSmithing(...)} (both instance methods on vanilla's
 * {@code RecipeProvider}) now resolve: they didn't exist as visible symbols on the old style of
 * class, which is what triggered the original "cannot find symbol" errors for them too.
 */
public class SatchelsRecipeProvider extends FabricRecipeProvider {
    public SatchelsRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected @NotNull RecipeProvider createRecipeProvider(@NotNull HolderLookup.Provider registries, @NotNull RecipeOutput output) {
        return new RecipeProvider(registries, output) {
            @Override
            public void buildRecipes() {
                this.shaped(RecipeCategory.TOOLS, ModItems.SATCHEL_GOLDEN)
                        .pattern(" s ")
                        .pattern("lgl")
                        .pattern("sls")
                        .define('s', ConventionalItemTags.STRINGS)
                        .define('l', ConventionalItemTags.LEATHERS)
                        .define('g', ConventionalItemTags.GOLD_INGOTS)
                        .unlockedBy("has_gold", has(ConventionalItemTags.GOLD_INGOTS))
                        .save(output);

                // Dyeing: "minecraft:crafting_dye" is per-item, not tag-driven — confirmed via
                // the real vanilla data (leather_helmet_dyed.json, wolf_armor_dyed.json etc. are
                // each their own recipe file keyed to one specific "target" item, there's no
                // generic "#minecraft:dyeable" recipe that covers every tagged item for free).
                // Being in the dyeable tag only affects things like shulker-box-style dye
                // detection elsewhere; it does NOT give a crafting recipe by itself. dyedItem(...)
                // is the same vanilla RecipeProvider helper that generates those vanilla files.
                for (var satchel : ModItems.ALL_SATCHELS) {
                    this.dyedItem(satchel, "dyed_satchel");
                }

                satchelUpgradeRecipe(
                        output, Satchels.at("satchel_diamond"), RecipeCategory.TOOLS, ModItems.SATCHEL_DIAMOND,
                        List.of(" d ", "dgd", " d "),
                        Map.of(
                                'd', Ingredient.of(registries.lookupOrThrow(Registries.ITEM).getOrThrow(ConventionalItemTags.DIAMOND_GEMS)),
                                'g', Ingredient.of(ModItems.SATCHEL_GOLDEN)
                        ),
                        "has_diamond", has(ConventionalItemTags.DIAMOND_GEMS)
                );

                // 26.1: netheriteSmithing(Item, RecipeCategory, Item) — no RecipeOutput param
                // anymore (confirmed via decompile); it's an instance method that uses this
                // RecipeProvider's own internally-held output.
                netheriteSmithing(ModItems.SATCHEL_DIAMOND, RecipeCategory.TOOLS, ModItems.SATCHEL_NETHERITE);
            }

            /**
             * Like {@link ShapedRecipeBuilder#save} (including unlock-advancement wiring), but
             * emits a {@link SatchelUpgradeRecipe} instead of a plain {@code ShapedRecipe} so the
             * output can carry the dyed ingredient's color. {@link ShapedRecipeBuilder} always
             * produces a vanilla {@code ShapedRecipe} with no hook to substitute the type, so
             * this reimplements just enough of its {@code save()} to inject ours instead —
             * mirrored directly off the real (decompiled) {@code ShapedRecipeBuilder.save} and
             * {@code RecipeUnlockAdvancementBuilder.build}.
             */
            private void satchelUpgradeRecipe(
                    RecipeOutput output, Identifier id, RecipeCategory category, net.minecraft.world.level.ItemLike result,
                    List<String> pattern, Map<Character, Ingredient> key,
                    String criterionName, net.minecraft.advancements.Criterion<?> criterion
            ) {
                ShapedRecipePattern shapedPattern = ShapedRecipePattern.of(key, pattern);
                ResourceKey<Recipe<?>> recipeId = ResourceKey.create(Registries.RECIPE, id);

                RecipeUnlockAdvancementBuilder advancementBuilder = new RecipeUnlockAdvancementBuilder();
                advancementBuilder.unlockedBy(criterionName, criterion);

                CraftingBookCategory bookCategory = RecipeBuilder.determineCraftingBookCategory(category);
                SatchelUpgradeRecipe recipe = new SatchelUpgradeRecipe(
                        "", bookCategory, shapedPattern, new ItemStackTemplate(result.asItem(), 1), true
                );

                output.accept(recipeId, recipe, advancementBuilder.build(output, recipeId, category));
            }
        };
    }

    // DataProvider#getName() is still abstract in 26.1 (confirmed via javap) and FabricRecipeProvider
    // doesn't provide a default — every other datagen provider in this codebase overrides it too.
    @Override
    public @NotNull String getName() {
        return "Hotbar Satchels Recipes";
    }
}
