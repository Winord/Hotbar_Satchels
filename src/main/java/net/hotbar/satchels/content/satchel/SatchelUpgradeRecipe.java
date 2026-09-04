package net.hotbar.satchels.content.satchel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.hotbar.satchels.ModItems;
import org.jetbrains.annotations.NotNull;

/**
 * Same shape/ingredients as a plain {@link ShapedRecipe} (in fact used only for the Golden →
 * Diamond satchel upgrade), but with one difference in {@link #assemble}: a dyed
 * {@link DataComponents#DYED_COLOR} on the Golden Satchel ingredient is carried over onto the
 * freshly crafted Diamond Satchel result instead of being lost.
 * <p>
 * Vanilla's plain {@code ShapedRecipe#assemble} just returns {@code this.result.copy()} — a
 * fixed {@link ItemStack} baked in at datagen time, with no awareness of what was actually in
 * the crafting grid. That's normally fine (nothing else a shaped recipe produces needs to carry
 * data over from its ingredients), but here it silently un-dyes any painted Golden Satchel the
 * moment it's upgraded. Contrast with the Diamond → Netherite step, which goes through a
 * vanilla {@code SmithingTransformRecipe} instead — that recipe type copies the base item's
 * whole component patch onto the result for free (the same mechanism that keeps netherite
 * armor's enchantments/trim through the smithing table), so color already survives that hop
 * without any mod code. This class exists purely to give the Diamond step the same behavior for
 * color specifically, without pulling in every other component a smithing upgrade would carry
 * over (a plain crafting-table recipe isn't expected to preserve anything else, e.g. it's still
 * fine — and desired — for a custom name to *not* survive an upgrade the way it wouldn't survive
 * any other shaped recipe).
 */
public class SatchelUpgradeRecipe extends ShapedRecipe {
    // Vanilla's ShapedRecipe keeps its own copies of these as package-private fields (see
    // decompiled net.minecraft.world.item.crafting.ShapedRecipe) — inaccessible from here since
    // this class lives in a different package. Duplicated locally rather than relying on
    // ShapedRecipe's own public accessors (getGroup()/category()/getIngredients()/etc.), since
    // those don't expose the raw ShapedRecipePattern object the Serializer's codec needs to
    // round-trip faithfully.
    final String group;
    final CraftingBookCategory category;
    final ShapedRecipePattern pattern;
    final ItemStack result;
    final boolean showNotification;

    public SatchelUpgradeRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification) {
        super(group, category, pattern, result, showNotification);
        this.group = group;
        this.category = category;
        this.pattern = pattern;
        this.result = result;
        this.showNotification = showNotification;
    }

    public SatchelUpgradeRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result) {
        this(group, category, pattern, result, true);
    }

    @Override
    @NotNull
    public RecipeSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    @NotNull
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = super.assemble(input, registries);

        for (ItemStack ingredient : input.items()) {
            if (ingredient.isEmpty() || !(ingredient.getItem() instanceof SatchelItem)) continue;

            DyedItemColor color = ingredient.get(DataComponents.DYED_COLOR);
            if (color != null) result.set(DataComponents.DYED_COLOR, color);
            break;
        }

        return result;
    }

    /**
     * Mirrors {@code ShapedRecipe.Serializer} field-for-field (same {@code group}/{@code
     * category}/{@code pattern}/{@code result}/{@code show_notification} JSON shape, so the
     * generated recipe JSON only needs its {@code "type"} changed) — the only difference is the
     * codec's constructor reference builds a {@link SatchelUpgradeRecipe} instead of a plain
     * {@link ShapedRecipe}. Needed as its own serializer (rather than reusing {@code
     * RecipeSerializer.SHAPED_RECIPE}) because recipe deserialization dispatches purely on the
     * JSON {@code "type"} id — the only way to get {@code SatchelUpgradeRecipe} instances out of
     * {@code RecipeManager} (and, via {@link #getSerializer}, back out over the network the same
     * way) is a distinct registered serializer with its own id.
     */
    public static class Serializer implements RecipeSerializer<SatchelUpgradeRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        public static final MapCodec<SatchelUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(recipe -> recipe.group),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(recipe -> recipe.category),
                ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
                Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(recipe -> recipe.showNotification)
        ).apply(instance, SatchelUpgradeRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, SatchelUpgradeRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork
        );

        @Override
        @NotNull
        public MapCodec<SatchelUpgradeRecipe> codec() {
            return CODEC;
        }

        @Override
        @NotNull
        public StreamCodec<RegistryFriendlyByteBuf, SatchelUpgradeRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static SatchelUpgradeRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
            ShapedRecipePattern pattern = ShapedRecipePattern.STREAM_CODEC.decode(buffer);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            boolean showNotification = buffer.readBoolean();
            return new SatchelUpgradeRecipe(group, category, pattern, result, showNotification);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, SatchelUpgradeRecipe recipe) {
            buffer.writeUtf(recipe.group);
            buffer.writeEnum(recipe.category);
            ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
            buffer.writeBoolean(recipe.showNotification);
        }
    }
}