package net.hotbar.satchels;

import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
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
 * <p>
 * <b>bugfix (26.1, recipe missing from JEI):</b> since 1.21.2, Minecraft no longer bulk-sends
 * full {@code Recipe} objects to the client — the vanilla
 * {@code ClientboundUpdateRecipesPacket} was stripped down to just {@code itemSets}/
 * {@code stonecutterRecipes} (confirmed against the real 26.1.2 jar), and the recipe-book UI is
 * now populated from lightweight {@code RecipeDisplayEntry} data instead. Fabric restored
 * full-recipe sync as an opt-in replacement ("Recipe Sync API", fabric-recipe-api-v1, added for
 * 1.21.11/26.1): a mod's custom {@link RecipeSerializer} is only included in that sync if it is
 * explicitly registered via {@link RecipeSynchronization#synchronizeRecipeSerializer}. JEI's own
 * Fabric integration already does this for vanilla serializers (matching this mod's debug log:
 * server-side {@code RecipeManager} loads 1521 recipes, but only 1520 reached the client via
 * {@code ClientRecipeSynchronizedEvent} — our custom recipe was the one silently excluded,
 * since nothing had ever opted {@code satchel_upgrade} into the new sync mechanism). Recipes
 * still craft fine either way (crafting is server-authoritative and never depended on this
 * sync) — this only affects recipe-book/JEI-style client-side visibility.
 */
public class ModRecipeSerializers {
    public static final RecipeSerializer<SatchelUpgradeRecipe> SATCHEL_UPGRADE = Registry.register(
            BuiltInRegistries.RECIPE_SERIALIZER, Satchels.at("satchel_upgrade"), SatchelUpgradeRecipe.Serializer.INSTANCE
    );

    static {
        RecipeSynchronization.synchronizeRecipeSerializer(SATCHEL_UPGRADE);
    }

    /** Called from {@link Satchels#onInitialize()} to force this class to load, triggering
     *  the static field initializers above (that's where the actual registration happens). */
    public static void register() {
    }
}