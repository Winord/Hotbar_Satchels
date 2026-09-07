package net.hotbar.satchels;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.hotbar.satchels.content.satchel.SatchelDyeRecipe;
import net.hotbar.satchels.content.satchel.SatchelUpgradeRecipe;

public class ModRecipeSerializers {
    public static final RecipeSerializer<SatchelUpgradeRecipe> SATCHEL_UPGRADE = Registry.register(
            BuiltInRegistries.RECIPE_SERIALIZER, Satchels.at("satchel_upgrade"), SatchelUpgradeRecipe.Serializer.INSTANCE
    );

    public static final RecipeSerializer<SatchelDyeRecipe> SATCHEL_DYE = Registry.register(
            BuiltInRegistries.RECIPE_SERIALIZER, Satchels.at("satchel_dye"), SatchelDyeRecipe.SERIALIZER
    );

    public static void register() {
    }
}