package net.hotbar.satchels;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.hotbar.satchels.content.satchel.SatchelUpgradeRecipe;

/**
 * Custom {@link RecipeSerializer} registration, following the same pattern as
 * {@link ModSounds}/{@link ModItems}. Must run from {@link Satchels#onInitialize()} before any
 * recipe JSON referencing {@code "type": "satchels:satchel_upgrade"} is loaded — otherwise
 * recipe deserialization fails to resolve the type and the Diamond Satchel recipe silently
 * doesn't load.
 * <p>
 * The registration id here ({@code "satchel_upgrade"}) MUST match the {@code "type"} field
 * used in {@code data/satchels/recipe/satchel_diamond.json} and in
 * {@code SatchelsRecipeProvider#satchelUpgradeRecipe}.
 */
public class ModRecipeSerializers {
    public static final RecipeSerializer<SatchelUpgradeRecipe> SATCHEL_UPGRADE = Registry.register(
            BuiltInRegistries.RECIPE_SERIALIZER, Satchels.at("satchel_upgrade"), SatchelUpgradeRecipe.Serializer.INSTANCE
    );

    /** Called from {@link Satchels#onInitialize()} to force this class to load, triggering
     *  the static field initializer above (that's where the actual registration happens). */
    public static void register() {
    }
}
