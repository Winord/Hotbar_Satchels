package net.hotbar.satchels.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.core.HolderLookup;
import net.hotbar.satchels.ModItems;
import net.hotbar.satchels.ModTags;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Generates {@code assets/satchels/lang/en_us.json} — the only officially supported language.
 * <p>
 * Item name keys use the typed {@code add(Item, String)} / {@code add(TagKey, String)}
 * overloads so a future registry rename becomes a compile error instead of a silently stale
 * json entry. All other keys (keybinding, sound, Cloth Config, Accessories slot label,
 * advancements) have no registry object and are added as plain string pairs.
 */
public class SatchelsLanguageProvider extends FabricLanguageProvider {
    public SatchelsLanguageProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, "en_us", registriesFuture);
    }

    @Override
    public void generateTranslations(@NotNull HolderLookup.Provider registryLookup, @NotNull TranslationBuilder translationBuilder) {
        // Item names — typed overloads tie these to ModItems constants.
        translationBuilder.add(ModItems.SATCHEL_GOLDEN, "Golden Satchel");
        translationBuilder.add(ModItems.SATCHEL_DIAMOND, "Diamond Satchel");
        translationBuilder.add(ModItems.SATCHEL_NETHERITE, "Netherite Satchel");
        translationBuilder.add(ModTags.SATCHEL, "Satchels");

        translationBuilder.add("key.satchels.toggle_satchel", "Toggle Satchel");
        translationBuilder.add("sound.satchels.satchel_rustle", "Satchel Rustles");
        translationBuilder.add("accessories.slot.satchel", "Satchel");

        // Advancement titles / descriptions (SatchelsAdvancementProvider).
        translationBuilder.add("satchels.advancements.root.title", "Satchels");
        translationBuilder.add("satchels.advancements.root.description", "A new way to carry your essentials.");
        translationBuilder.add("satchels.advancements.golden.title", "Very Convenient");
        translationBuilder.add("satchels.advancements.golden.description", "This is not just a bag, it is a faithful companion");
        translationBuilder.add("satchels.advancements.diamond.title", "Double the size?!");
        translationBuilder.add("satchels.advancements.diamond.description", "This faithful companion has serious capacity");
        translationBuilder.add("satchels.advancements.netherite.title", "Satchelization!");
        translationBuilder.add("satchels.advancements.netherite.description", "All that is mine I carry with me");
        translationBuilder.add("satchels.advancements.satchel_full.title", "Packed to the Brim");
        translationBuilder.add("satchels.advancements.satchel_full.description", "Fill every slot of a Netherite Satchel with full stacks");

        // Cloth Config screen / categories (SatchelsConfigScreen via Mod Menu).
        translationBuilder.add("satchels.configuration.title", "Hotbar Satchels");
        translationBuilder.add("satchels.configuration.server", "Server");
        translationBuilder.add("satchels.configuration.client", "Client");

        // Cloth Config option labels — server (SatchelsCommonConfig).
        translationBuilder.add("satchels.configuration.allowed_menus", "Menus with Satchel Slots");
        translationBuilder.add("satchels.configuration.log_opened_menu", "Log Opened Menus");

        // Cloth Config option labels — client (SatchelsClientConfig).
        translationBuilder.add("satchels.configuration.gui_animation", "GUI/HUD Animation");
        translationBuilder.add("satchels.configuration.satchel_layer", "Render Satchel on Players");
        translationBuilder.add("satchels.configuration.shift_swap", "Satchel Slot Swapping");

        // Per-tier hotbar slot-start sliders (§11.4).
        // Netherite has no entry — it always occupies the full hotbar.
        translationBuilder.add("satchels.configuration.golden_position", "Golden Satchel Position");
        translationBuilder.add("satchels.configuration.golden_position.tooltip", "The hotbar slot the Golden Satchel starts from.");
        translationBuilder.add("satchels.configuration.diamond_position", "Diamond Satchel Position");
        translationBuilder.add("satchels.configuration.diamond_position.tooltip", "The hotbar slot the Diamond Satchel starts from.");
        translationBuilder.add("satchels.configuration.slot_start_selection", "Slots %s-%s");

        // Legacy keys kept for compatibility with any existing lang forks.
        translationBuilder.add("satchels.configuration.gameplay", "Gameplay");
        translationBuilder.add("satchels.configuration.rendering", "Rendering");
    }
}
