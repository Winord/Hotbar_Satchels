package net.hotbar.satchels.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
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
 */
public class SatchelsRecipeProvider extends FabricRecipeProvider {
    public SatchelsRecipeProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    public void buildRecipes(@NotNull RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.SATCHEL_GOLDEN)
                .pattern(" s ")
                .pattern("lgl")
                .pattern("sls")
                .define('s', ConventionalItemTags.STRINGS)
                .define('l', ConventionalItemTags.LEATHERS)
                .define('g', ConventionalItemTags.GOLD_INGOTS)
                .unlockedBy("has_gold", has(ConventionalItemTags.GOLD_INGOTS))
                .save(output);

        satchelUpgradeRecipe(
                output, Satchels.at("satchel_diamond"), RecipeCategory.TOOLS, ModItems.SATCHEL_DIAMOND,
                List.of(" d ", "dgd", " d "),
                Map.of('d', Ingredient.of(ConventionalItemTags.DIAMOND_GEMS), 'g', Ingredient.of(ModItems.SATCHEL_GOLDEN)),
                "has_diamond", has(ConventionalItemTags.DIAMOND_GEMS)
        );

        netheriteSmithing(output, ModItems.SATCHEL_DIAMOND, RecipeCategory.TOOLS, ModItems.SATCHEL_NETHERITE);
    }

    /**
     * Like {@link ShapedRecipeBuilder#save} (including unlock-advancement wiring), but emits a
     * {@link SatchelUpgradeRecipe} instead of a plain {@code ShapedRecipe} so the output can
     * carry the dyed ingredient's color. {@link ShapedRecipeBuilder} always produces a vanilla
     * {@code ShapedRecipe} with no hook to substitute the type, so this reimplements just enough
     * of its {@code save()} to inject ours instead.
     */
    private static void satchelUpgradeRecipe(
            RecipeOutput output, ResourceLocation id, RecipeCategory category, net.minecraft.world.level.ItemLike result,
            List<String> pattern, Map<Character, Ingredient> key,
            String criterionName, net.minecraft.advancements.Criterion<?> criterion
    ) {
        ShapedRecipePattern shapedPattern = ShapedRecipePattern.of(key, pattern);

        Advancement.Builder advancement = output.advancement()
                .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id))
                .rewards(AdvancementRewards.Builder.recipe(id))
                .requirements(AdvancementRequirements.Strategy.OR)
                .addCriterion(criterionName, criterion);

        CraftingBookCategory bookCategory = RecipeBuilder.determineBookCategory(category);
        SatchelUpgradeRecipe recipe = new SatchelUpgradeRecipe(
                "", bookCategory, shapedPattern, new ItemStack(result), true
        );

        output.accept(id, recipe, advancement.build(id.withPrefix("recipes/" + category.getFolderName() + "/")));
    }
}
