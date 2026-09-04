package net.hotbar.satchels.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.hotbar.satchels.SatchelsCommonConfig;
import net.hotbar.satchels.client.SatchelsClientConfig;
import net.hotbar.satchels.content.satchel.SatchelTier;

import java.util.ArrayList;

/**
 * Builds and returns the Cloth Config settings screen for Hotbar Satchels.
 * <p>
 * Two categories:
 * <ul>
 *   <li><b>Server</b> ({@link SatchelsCommonConfig}): allowed container menus, debug logging.
 *       Persisted to {@code config/satchels-common.json} via {@code save()} in the
 *       save-callback.</li>
 *   <li><b>Client</b> ({@link SatchelsClientConfig}): shift-swap, satchel layer, GUI animation
 *       toggles, and the Golden/Diamond per-tier hotbar slot-start sliders. Each setter already
 *       calls {@code save()} internally.</li>
 * </ul>
 * <p>
 * Persistence is handled entirely by the two config classes' own GSON round-trips — Cloth
 * Config's own file storage is not used.
 * <p>
 * Netherite has no slider: it always occupies the full 9-slot hotbar and has no persisted
 * slot-start field (see {@code SatchelsClientConfig}).
 */
@Environment(EnvType.CLIENT)
public final class SatchelsConfigScreen {

    private SatchelsConfigScreen() {}

    /**
     * Creates and returns the config {@link Screen}.
     *
     * @param parent the screen to return to when the user closes or saves the config screen
     */
    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("satchels.configuration.title"))
                .setSavingRunnable(SatchelsConfigScreen::onSave);

        ConfigEntryBuilder entries = builder.entryBuilder();

        buildServerCategory(builder, entries);
        buildClientCategory(builder, entries);

        return builder.build();
    }

    // -------------------------------------------------------------------------
    // Server category  (SatchelsCommonConfig)
    // -------------------------------------------------------------------------

    private static void buildServerCategory(ConfigBuilder builder, ConfigEntryBuilder entries) {
        ConfigCategory server = builder.getOrCreateCategory(
                Component.translatable("satchels.configuration.server"));

        // log_opened_menu — bool toggle
        server.addEntry(entries
                .startBooleanToggle(
                        Component.translatable("satchels.configuration.log_opened_menu"),
                        SatchelsCommonConfig.shouldLog())
                .setDefaultValue(false)
                .setSaveConsumer(SatchelsCommonConfig::setLogOpenedMenu)
                .build());

        // allowed_menus — list of strings
        server.addEntry(entries
                .startStrList(
                        Component.translatable("satchels.configuration.allowed_menus"),
                        new ArrayList<>(SatchelsCommonConfig.getAllowedMenusRaw()))
                .setDefaultValue(new ArrayList<>())
                .setSaveConsumer(SatchelsCommonConfig::setAllowedMenusRaw)
                .build());
    }

    // -------------------------------------------------------------------------
    // Client category  (SatchelsClientConfig)
    // -------------------------------------------------------------------------

    private static void buildClientCategory(ConfigBuilder builder, ConfigEntryBuilder entries) {
        ConfigCategory client = builder.getOrCreateCategory(
                Component.translatable("satchels.configuration.client"));

        // shift_swap
        client.addEntry(entries
                .startBooleanToggle(
                        Component.translatable("satchels.configuration.shift_swap"),
                        SatchelsClientConfig.shouldSwapWithShiftKey())
                .setDefaultValue(true)
                .setSaveConsumer(SatchelsClientConfig::setShiftSwap)
                .build());

        // satchel_layer
        client.addEntry(entries
                .startBooleanToggle(
                        Component.translatable("satchels.configuration.satchel_layer"),
                        SatchelsClientConfig.shouldRenderSatchel())
                .setDefaultValue(true)
                .setSaveConsumer(SatchelsClientConfig::setSatchelLayer)
                .build());

        // gui_animation
        client.addEntry(entries
                .startBooleanToggle(
                        Component.translatable("satchels.configuration.gui_animation"),
                        SatchelsClientConfig.shouldAnimateGUI())
                .setDefaultValue(true)
                .setSaveConsumer(SatchelsClientConfig::setGuiAnimation)
                .build());

        // golden_slot_start / diamond_slot_start — per-tier sliders. Netherite has no entry:
        // it always occupies the full hotbar, so there is nothing to configure for it.
        client.addEntry(buildSlotStartSlider(entries, SatchelTier.GOLDEN,
                "satchels.configuration.golden_position", "satchels.configuration.golden_position.tooltip"));
        client.addEntry(buildSlotStartSlider(entries, SatchelTier.DIAMOND,
                "satchels.configuration.diamond_position", "satchels.configuration.diamond_position.tooltip"));
    }

    /**
     * Builds a single per-tier hotbar slot-start slider: range {@code 1..maxStart(tier)} (the
     * slider itself enforces the bound), with the text readout formatted as "Slots start-end"
     * via {@code satchels.configuration.slot_start_selection}.
     */
    private static me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry buildSlotStartSlider(
            ConfigEntryBuilder entries, SatchelTier tier, String labelKey, String tooltipKey) {
        int max = SatchelsClientConfig.getMaxSlotStart(tier);
        int slots = tier.getSlotCount();

        return entries.startIntSlider(
                        Component.translatable(labelKey),
                        SatchelsClientConfig.getSlotStart(tier),
                        1, max)
                .setDefaultValue(() -> 1)
                .setTextGetter(start -> Component.translatable(
                        "satchels.configuration.slot_start_selection", start, start + slots - 1))
                .setTooltip(Component.translatable(tooltipKey))
                .setSaveConsumer(start -> SatchelsClientConfig.updateSlotStart(tier, start))
                .build();
    }

    // -------------------------------------------------------------------------
    // Save callback
    // -------------------------------------------------------------------------

    /**
     * Called by Cloth Config after all per-entry {@code setSaveConsumer} callbacks have run.
     * The client-side setters ({@link SatchelsClientConfig#setShiftSwap} etc.) each call
     * {@code save()} internally, so only the server config needs an explicit persist here.
     */
    private static void onSave() {
        SatchelsCommonConfig.save();
    }
}
