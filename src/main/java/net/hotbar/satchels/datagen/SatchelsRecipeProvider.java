package net.hotbar.satchels.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.SmithingTransformRecipeBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * Uses Fabric Convention Tags ({@code c:} namespace) for ingredients.
 * <p>
 * <b>1.21.4 API changes:</b>
 * <ul>
 *   <li>{@code FabricRecipeProvider} now requires overriding
 *       {@code createRecipeProvider(HolderLookup.Provider, RecipeOutput)} returning a
 *       {@code RecipeProvider} (Mojang mappings name; Yarn calls it {@code RecipeGenerator}).
 *       The inner method to override is {@code buildRecipes()} (no arguments).</li>
 *   <li>{@code registries.lookupOrThrow(Registries.ITEM).getOrThrow(tag)} returns
 *       {@code Named<Item>}, not {@code TagKey<Item>}. Pass {@code .key()} to get the
 *       {@code TagKey} needed by {@code define(char, TagKey)} and {@code has(TagKey)}.</li>
 *   <li>{@code SmithingTransformRecipeBuilder.save(RecipeOutput, ResourceLocation)} is
 *       gone — use {@code save(RecipeOutput, ResourceKey<Recipe<?>>)} instead.</li>
 *   <li>{@code getName()} is now abstract in {@code DataProvider} and must be overridden.</li>
 * </ul>
 */
public class SatchelsRecipeProvider extends FabricRecipeProvider {

    public SatchelsRecipeProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    public @NotNull RecipeProvider createRecipeProvider(@NotNull HolderLookup.Provider registries,
                                                        @NotNull RecipeOutput output) {
        return new RecipeProvider(registries, output) {
            @Override
            public void buildRecipes() {
                var items = registries.lookupOrThrow(Registries.ITEM);

                // getOrThrow(TagKey) returns Named<Item>; .key() gives back the TagKey
                // that define(char, TagKey) and has(TagKey) actually expect.
                ShapedRecipeBuilder.shaped(items, RecipeCategory.TOOLS, ModItems.SATCHEL_GOLDEN)
                        .pattern(" s ")
                        .pattern("lgl")
                        .pattern("sls")
                        .define('s', items.getOrThrow(ConventionalItemTags.STRINGS).key())
                        .define('l', items.getOrThrow(ConventionalItemTags.LEATHERS).key())
                        .define('g', items.getOrThrow(ConventionalItemTags.GOLD_INGOTS).key())
                        .unlockedBy("has_gold", has(items.getOrThrow(ConventionalItemTags.GOLD_INGOTS).key()))
                        .save(output);

                satchelUpgradeRecipe(
                        output, Satchels.at("satchel_diamond"), RecipeCategory.TOOLS, ModItems.SATCHEL_DIAMOND,
                        List.of(" d ", "dgd", " d "),
                        Map.of(
                                'd', Ingredient.of(items.getOrThrow(ConventionalItemTags.DIAMOND_GEMS)),
                                'g', Ingredient.of(ModItems.SATCHEL_GOLDEN)
                        ),
                        "has_diamond", has(items.getOrThrow(ConventionalItemTags.DIAMOND_GEMS).key())
                );

                ResourceKey<Recipe<?>> netheriteKey = ResourceKey.create(
                        Registries.RECIPE, Satchels.at("satchel_netherite_smithing"));

                SmithingTransformRecipeBuilder
                        .smithing(
                                Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                                Ingredient.of(ModItems.SATCHEL_DIAMOND),
                                Ingredient.of(Items.NETHERITE_INGOT),
                                RecipeCategory.TOOLS,
                                ModItems.SATCHEL_NETHERITE
                        )
                        .unlocks("has_netherite", has(Items.NETHERITE_INGOT))
                        .save(output, netheriteKey);

                // Satchel dyeing — replaces color instead of blending (see SatchelDyeRecipe)
                ResourceKey<Recipe<?>> dyeKey = ResourceKey.create(Registries.RECIPE, Satchels.at("satchel_dye"));
                output.accept(dyeKey,
                        new net.hotbar.satchels.content.satchel.SatchelDyeRecipe(CraftingBookCategory.EQUIPMENT),
                        null);
            }
        };
    }

    @Override
    public @NotNull String getName() {
        return "Satchels Recipes";
    }

    /**
     * Like {@link ShapedRecipeBuilder#save} but emits a {@link SatchelUpgradeRecipe} instead
     * of a plain {@code ShapedRecipe} so the output can carry the dyed ingredient's color.
     * In 1.21.4 recipe advancements use {@code ResourceKey<Recipe<?>>} instead of
     * {@code ResourceLocation} — derived via {@code ResourceKey.create(Registries.RECIPE, id)}.
     */
    private static void satchelUpgradeRecipe(
            RecipeOutput output, ResourceLocation id, RecipeCategory category,
            net.minecraft.world.level.ItemLike result,
            List<String> pattern, Map<Character, Ingredient> key,
            String criterionName, net.minecraft.advancements.Criterion<?> criterion
    ) {
        ResourceKey<Recipe<?>> recipeKey = ResourceKey.create(Registries.RECIPE, id);

        Advancement.Builder advancement = output.advancement()
                .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(recipeKey))
                .rewards(AdvancementRewards.Builder.recipe(recipeKey))
                .requirements(AdvancementRequirements.Strategy.OR)
                .addCriterion(criterionName, criterion);

        ShapedRecipePattern shapedPattern = ShapedRecipePattern.of(key, pattern);
        CraftingBookCategory bookCategory = RecipeBuilder.determineBookCategory(category);
        SatchelUpgradeRecipe recipe = new SatchelUpgradeRecipe(
                "", bookCategory, shapedPattern, new ItemStack(result), true
        );

        ResourceLocation advancementId = id.withPrefix("recipes/" + category.getFolderName() + "/");
        output.accept(recipeKey, recipe, advancement.build(advancementId));
    }
}