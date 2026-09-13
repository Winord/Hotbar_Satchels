package net.hotbar.satchels.compat.recipeviewer;

import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.hotbar.satchels.content.satchel.SatchelUpgradeRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tells JEI how to lay out a {@link SatchelUpgradeRecipe} in the vanilla Crafting category.
 * <p>
 * JEI only knows how to read {@code ShapedRecipe}/{@code ShapelessRecipe} on its own — any other
 * {@code CraftingRecipe} subclass (like this one, which had to become its own class purely to
 * carry the dyed color over from the Golden Satchel ingredient — see {@link SatchelUpgradeRecipe}'s
 * own javadoc) is invisible to JEI's crafting category unless a mod explicitly registers an
 * extension for it via {@code IModPlugin#registerVanillaCategoryExtensions} — see
 * {@link SatchelsJEIPlugin}. This isn't an error or a missing-texture situation; JEI silently
 * skips recipe classes it doesn't recognize, which is why this showed up as "recipe book has it,
 * JEI doesn't" with nothing at all in the log.
 * <p>
 * Confirmed against the real JEI 26.1 source (cloned {@code CommonApi/.../ICraftingCategoryExtension.java}
 * directly from GitHub — the earlier attempt was based on an older/different API shape and didn't
 * compile). {@code getIngredients} is the only method actually required to be overridden; it
 * returns {@code List<SlotDisplay>}, not raw {@code Ingredient}s — the whole crafting category
 * moved onto the same display-based system {@code Recipe#display()} already uses (matches what
 * this mod's own port already found for the recipe book, see {@code SatchelUpgradeRecipe}'s
 * javadoc). {@code setRecipe} has a default implementation that already builds the full layout
 * (output from {@code recipe.display()}, inputs from {@code getIngredients()} + the width/height
 * below) — no need to override it or touch {@code IRecipeLayoutBuilder}/{@code ICraftingGridHelper}
 * directly at all.
 */
public class SatchelUpgradeCraftingCategoryExtension implements ICraftingCategoryExtension<SatchelUpgradeRecipe> {

    @Override
    @NotNull
    public List<SlotDisplay> getIngredients(RecipeHolder<SatchelUpgradeRecipe> recipeHolder) {
        return recipeHolder.value().getPatternIngredients().stream()
                .map(ingredientOpt -> ingredientOpt.<SlotDisplay>map(Ingredient::display).orElse(SlotDisplay.Empty.INSTANCE))
                .toList();
    }

    @Override
    public int getWidth(RecipeHolder<SatchelUpgradeRecipe> recipeHolder) {
        return recipeHolder.value().getWidth();
    }

    @Override
    public int getHeight(RecipeHolder<SatchelUpgradeRecipe> recipeHolder) {
        return recipeHolder.value().getHeight();
    }
}