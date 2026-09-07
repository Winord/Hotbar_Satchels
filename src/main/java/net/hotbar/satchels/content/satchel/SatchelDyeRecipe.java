package net.hotbar.satchels.content.satchel;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SatchelDyeRecipe extends CustomRecipe {

    public static final RecipeSerializer<SatchelDyeRecipe> SERIALIZER = new RecipeSerializer<>() {
        private static final MapCodec<SatchelDyeRecipe> CODEC =
                CraftingBookCategory.CODEC
                        .fieldOf("category")
                        .orElse(CraftingBookCategory.MISC)
                        .xmap(SatchelDyeRecipe::new, r -> r.category());

        private static final StreamCodec<RegistryFriendlyByteBuf, SatchelDyeRecipe> STREAM =
                StreamCodec.of(
                        (buf, recipe) -> buf.writeEnum(recipe.category()),
                        buf -> new SatchelDyeRecipe(buf.readEnum(CraftingBookCategory.class))
                );

        @Override public @NotNull MapCodec<SatchelDyeRecipe> codec() { return CODEC; }
        @Override public @NotNull StreamCodec<RegistryFriendlyByteBuf, SatchelDyeRecipe> streamCodec() { return STREAM; }
    };

    public SatchelDyeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(@NotNull CraftingInput input, @NotNull Level level) {
        ItemStack satchel = ItemStack.EMPTY;
        List<DyeItem> dyes = new ArrayList<>();

        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof SatchelItem && satchel.isEmpty()) {
                satchel = stack;
            } else if (stack.getItem() instanceof DyeItem dye) {
                dyes.add(dye);
            } else {
                return false;
            }
        }
        return !satchel.isEmpty() && !dyes.isEmpty();
    }

    @Override
    @NotNull
    public ItemStack assemble(@NotNull CraftingInput input, @NotNull HolderLookup.Provider registries) {
        ItemStack satchel = ItemStack.EMPTY;
        List<DyeItem> dyes = new ArrayList<>();

        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof SatchelItem && satchel.isEmpty()) {
                satchel = stack;
            } else if (stack.getItem() instanceof DyeItem dye) {
                dyes.add(dye);
            }
        }
        if (satchel.isEmpty() || dyes.isEmpty()) return ItemStack.EMPTY;

        int r = 0, g = 0, b = 0;
        for (DyeItem dye : dyes) {
            int color = dye.getDyeColor().getTextureDiffuseColor();
            r += (color >> 16) & 0xFF;
            g += (color >> 8) & 0xFF;
            b += color & 0xFF;
        }
        r /= dyes.size();
        g /= dyes.size();
        b /= dyes.size();
        int newColor = (r << 16) | (g << 8) | b;

        ItemStack result = satchel.copy();
        result.set(DataComponents.DYED_COLOR, new DyedItemColor(newColor, false));
        return result;
    }

    @Override
    public @NotNull RecipeSerializer<SatchelDyeRecipe> getSerializer() {
        return SERIALIZER;
    }
}