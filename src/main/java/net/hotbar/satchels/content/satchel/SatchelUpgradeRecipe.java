package net.hotbar.satchels.content.satchel;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Same shape/ingredients as a plain shaped recipe (in fact used only for the Golden → Diamond
 * satchel upgrade), but with one difference in {@link #assemble}: a dyed
 * {@link DataComponents#DYED_COLOR} on the Golden Satchel ingredient is carried over onto the
 * freshly crafted Diamond Satchel result instead of being lost.
 * <p>
 * Vanilla's plain shaped recipe just returns {@code this.result.create()} — a fixed result
 * template baked in at datagen time, with no awareness of what was actually in the crafting
 * grid. That's normally fine, but here it silently un-dyes any painted Golden Satchel the moment
 * it's upgraded. Contrast with the Diamond → Netherite step, which goes through a vanilla
 * {@code SmithingTransformRecipe} instead — that recipe type copies the base item's whole
 * component patch onto the result for free, so color already survives that hop without any mod
 * code. This class exists purely to give the Diamond step the same behavior for color
 * specifically, without pulling in every other component a smithing upgrade would carry over.
 * <p>
 * Extends {@code NormalCraftingRecipe} directly rather than {@code ShapedRecipe}: {@code
 * ShapedRecipe} overrides {@code getSerializer()} to return the concrete
 * {@code RecipeSerializer<ShapedRecipe>}, not the permissive wildcard
 * {@code RecipeSerializer<? extends NormalCraftingRecipe>} that {@code NormalCraftingRecipe}
 * declares — since generics are invariant, a custom-serializer subclass of {@code ShapedRecipe}
 * can't override {@code getSerializer()} with its own return type. {@code NormalCraftingRecipe}'s
 * wildcard bound makes the covariant override valid instead, at the cost of composing a
 * {@code ShapedRecipePattern} field and replicating {@code ShapedRecipe}'s own
 * {@code matches}/{@code createPlacementInfo}/{@code assemble}/{@code display} bodies directly.
 * {@code assemble} takes only {@code CraftingInput}, matching the current {@code Recipe<T>}
 * interface.
 */
public class SatchelUpgradeRecipe extends NormalCraftingRecipe {
    private final ShapedRecipePattern pattern;
    private final ItemStackTemplate result;

    public SatchelUpgradeRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern, ItemStackTemplate result) {
        super(commonInfo, bookInfo);
        this.pattern = pattern;
        this.result = result;
    }

    /** Convenience constructor mirroring the old (group, category, pattern, result, showNotification) shape. */
    public SatchelUpgradeRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStackTemplate result, boolean showNotification) {
        this(new Recipe.CommonInfo(showNotification), new CraftingRecipe.CraftingBookInfo(category, group), pattern, result);
    }

    public SatchelUpgradeRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStackTemplate result) {
        this(group, category, pattern, result, true);
    }

    @Override
    @NotNull
    public RecipeSerializer<SatchelUpgradeRecipe> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(this.pattern.ingredients());
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.pattern.matches(input);
    }

    public int getWidth() {
        return this.pattern.width();
    }

    public int getHeight() {
        return this.pattern.height();
    }

    /** Exposed for {@link net.hotbar.satchels.compat.recipeviewer.SatchelUpgradeCraftingCategoryExtension} — {@link #pattern} itself is private. */
    @NotNull
    public List<Optional<Ingredient>> getPatternIngredients() {
        return this.pattern.ingredients();
    }

    @Override
    @NotNull
    public List<RecipeDisplay> display() {
        return List.of(new ShapedCraftingRecipeDisplay(
                this.pattern.width(),
                this.pattern.height(),
                this.pattern.ingredients().stream()
                        .map(e -> e.map(net.minecraft.world.item.crafting.Ingredient::display).orElse(SlotDisplay.Empty.INSTANCE))
                        .toList(),
                new SlotDisplay.ItemStackSlotDisplay(this.result),
                new SlotDisplay.ItemSlotDisplay(net.minecraft.world.item.Items.CRAFTING_TABLE)
        ));
    }

    @Override
    @NotNull
    public ItemStack assemble(CraftingInput input) {
        ItemStack assembled = this.result.create();

        for (ItemStack ingredient : input.items()) {
            if (ingredient.isEmpty() || !(ingredient.getItem() instanceof SatchelItem)) continue;

            DyedItemColor color = ingredient.get(DataComponents.DYED_COLOR);
            if (color != null) assembled.set(DataComponents.DYED_COLOR, color);
            break;
        }

        return assembled;
    }

    /**
     * Mirrors {@code ShapedRecipe}'s own {@code MAP_CODEC}/{@code STREAM_CODEC}/{@code SERIALIZER}
     * field-for-field, so the generated recipe JSON only needs its {@code "type"}
     * changed. Needed as its own serializer (rather than reusing {@code
     * RecipeSerializer.SHAPED_RECIPE}) because recipe deserialization dispatches purely on the
     * JSON {@code "type"} id — the only way to get {@code SatchelUpgradeRecipe} instances out of
     * {@code RecipeManager} (and, via {@link #getSerializer}, back out over the network the same
     * way) is a distinct registered serializer with its own id.
     */
    public static final class Serializer {
        // Built from the public group()/category()/showNotification() accessors (all `public
        // final` on NormalCraftingRecipe) rather than the recipe.commonInfo/recipe.bookInfo
        // fields directly — those are only `protected`, and this Serializer is a *nested* class
        // of the SatchelUpgradeRecipe subclass rather than the subclass itself, which puts
        // direct field access in genuinely ambiguous JLS territory (protected-member access
        // through a subtype reference is guaranteed for the subclass's own body, not clearly for
        // classes merely nested within it) — not worth risking on a detail this replaceable.
        public static final MapCodec<SatchelUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> new Recipe.CommonInfo(recipe.showNotification())),
                CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(recipe -> new CraftingRecipe.CraftingBookInfo(recipe.category(), recipe.group())),
                ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
        ).apply(instance, SatchelUpgradeRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, SatchelUpgradeRecipe> STREAM_CODEC = StreamCodec.composite(
                Recipe.CommonInfo.STREAM_CODEC, recipe -> new Recipe.CommonInfo(recipe.showNotification()),
                CraftingRecipe.CraftingBookInfo.STREAM_CODEC, recipe -> new CraftingRecipe.CraftingBookInfo(recipe.category(), recipe.group()),
                ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.pattern,
                ItemStackTemplate.STREAM_CODEC, recipe -> recipe.result,
                SatchelUpgradeRecipe::new
        );

        // RecipeSerializer.of(...) doesn't exist — RecipeSerializer is now a record with a
        // public (MapCodec<T>, StreamCodec<...>) constructor; construct it directly, exactly like
        // vanilla's own ShapedRecipe.SERIALIZER does.
        public static final RecipeSerializer<SatchelUpgradeRecipe> INSTANCE =
                new RecipeSerializer<>(CODEC, STREAM_CODEC);
    }
}
