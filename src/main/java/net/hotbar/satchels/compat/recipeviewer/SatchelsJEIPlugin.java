package net.hotbar.satchels.compat.recipeviewer;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.hotbar.satchels.Satchels;
import net.hotbar.satchels.content.satchel.SatchelUpgradeRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * JEI plugin: registers an exclusion area so JEI's recipe grid doesn't render over the
 * satchel equipment slot.
 * <p>
 * On Fabric, JEI does not scan the classpath for {@code @JeiPlugin} the way it does on
 * NeoForge/Forge — it instead reads the plugin list from the {@code "jei_mod_plugin"}
 * entrypoint in {@code fabric.mod.json}. The {@code @JeiPlugin} annotation is kept here for
 * documentation/compatibility but the {@code fabric.mod.json} entrypoint is what actually
 * wires the plugin up on Fabric.
 */
@JeiPlugin
public class SatchelsJEIPlugin implements IModPlugin {
    @Override
    @NotNull
    public Identifier getPluginUid() {
        return Satchels.at("jei");
    }

    @Override
    public void registerGuiHandlers(@NotNull IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(InventoryScreen.class, new SatchelsJEIExclusionArea<>());
    }

    /**
     * Registers {@link SatchelUpgradeCraftingCategoryExtension} so JEI's crafting category knows
     * how to display {@link SatchelUpgradeRecipe} (the Golden → Diamond satchel upgrade) — without
     * this, JEI silently ignores any recipe class it doesn't recognize on its own (i.e. anything
     * that isn't a plain {@code ShapedRecipe}/{@code ShapelessRecipe}), even though the vanilla
     * recipe book shows it fine.
     * <p>
     * {@code addExtension} takes a singleton instance directly (confirmed against the real 26.1
     * source), not a per-recipe factory function — extensions receive the recipe as a parameter
     * on each of their own methods instead.
     */
    @Override
    public void registerVanillaCategoryExtensions(@NotNull IVanillaCategoryExtensionRegistration registration) {
        registration.getCraftingCategory().addExtension(SatchelUpgradeRecipe.class, new SatchelUpgradeCraftingCategoryExtension());
    }

    private static class SatchelsJEIExclusionArea<T extends AbstractContainerScreen<InventoryMenu>> implements IGuiContainerHandler<T> {
        @Override
        @NotNull
        public List<Rect2i> getGuiExtraAreas(@NotNull T containerScreen) {
            return SatchelSlotExclusionArea.getGuiExtraAreas(containerScreen);
        }
    }
}