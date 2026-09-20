package net.hotbar.satchels.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.hotbar.satchels.SatchelsCommonConfig;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelTier;
import net.hotbar.satchels.network.packets.SatchelOffsetUpdatePacketC2S;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Client-side settings, persisted directly via GSON to {@code config/satchels-client.json}
 * (same approach as {@link net.hotbar.satchels.SatchelsCommonConfig}). Editable through the
 * Cloth Config screen ({@code SatchelsConfigScreen}, via Mod Menu).
 * <p>
 * Each Golden/Diamond satchel keeps its own persisted 1-based hotbar slot-start
 * ({@link #goldenSlotStart}/{@link #diamondSlotStart}), valid range {@code 1..maxStart(tier)}
 * ({@link #getMaxSlotStart}). Netherite has no field: a 9-slot satchel always fills the whole
 * hotbar. Keeping the two fields independent means a tier switch never has to reconcile one
 * tier's start against another tier's slot count — see {@link #applyPersistedOffsetForTier}.
 * <p>
 * {@code corner_menus} is a second menu list, analogous to {@code allowed_menus} but purely
 * visual and therefore client-side (a resource pack that re-textures a menu is a per-player
 * thing, so the player needs to be able to change it without touching the server's config):
 * it names the menus whose satchel row also draws the 1px corner "tuck" pixel — see
 * {@link #isCornerEnabled}. Entries are bare {@code resource:location} ids, no offsets.
 */
@Environment(EnvType.CLIENT)
public class SatchelsClientConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("satchels-client.json");

    /** Default 1-based slot-start applied whenever a stored/loaded value is out of range. */
    private static final int DEFAULT_SLOT_START = 1;

    private static int goldenSlotStart = DEFAULT_SLOT_START;
    private static int diamondSlotStart = DEFAULT_SLOT_START;
    private static boolean shiftSwap = true;
    private static boolean satchelLayer = true;
    private static boolean guiAnimation = true;
    private static boolean satchelHiddenInInventory = false;

    /** Raw {@code corner_menus} entries exactly as stored/shown in the config screen. */
    private static List<String> cornerMenusRaw = new ArrayList<>();
    /** Parsed form of {@link #cornerMenusRaw}, rebuilt by {@link #rebuildCornerLookup}. */
    private static final Set<Identifier> cornerMenus = new HashSet<>();

    /**
     * Menus that get the corner pixel on a fresh install, and the config screen's "reset" value.
     * Trim this list down to the panels whose texture actually has the notch. It only affects
     * configs that have no {@code corner_menus} yet — an existing key in the json always wins.
     * Mirrors the ids of {@code SatchelsCommonConfig#getMenuDefaults()} (offsets stripped).
     */
    private static final List<String> CORNER_MENU_DEFAULTS = List.of(
            "minecraft:inventory",
            "minecraft:crafting",
            "minecraft:crafter_3x3",
            "minecraft:generic_9x1",
            "minecraft:generic_9x2",
            "minecraft:generic_9x3",
            "minecraft:generic_9x4",
            "minecraft:generic_9x5",
            "minecraft:generic_9x6",
            "minecraft:shulker_box",
            "minecraft:furnace",
            "minecraft:smoker",
            "minecraft:blast_furnace",
            "minecraft:cartography_table",
            "minecraft:smithing",
            "minecraft:loom",
            "minecraft:stonecutter",
            "minecraft:enchantment",
            "minecraft:anvil",
            "minecraft:grindstone",
            "minecraft:brewing_stand",
            "minecraft:hopper",
            "minecraft:generic_3x3",
            "minecraft:horse",
            "farmersdelight:cooking_pot",
            "curios:curios_container",
            "ohmega:accessory_menu",
            "supplementaries:sack"
    );

    private record Data(int golden_slot_start, int diamond_slot_start, boolean shift_swap, boolean satchel_layer, boolean gui_animation, boolean satchel_hidden_in_inventory, List<String> corner_menus) {
    }

    /** Call from {@code SatchelsClient.onInitializeClient()}. Reads the config file, or creates it with defaults. */
    public static void load() {
        boolean configExisted = Files.exists(FILE);
        boolean cornerMenusPresent = false;

        if (configExisted) {
            try {
                Data data = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Data.class);
                if (data != null) {
                    // Out-of-range values (including 0, which GSON fills in for a field missing
                    // from an older config) reset to the default rather than clamping.
                    goldenSlotStart = isValidSlotStart(data.golden_slot_start(), SatchelTier.GOLDEN)
                            ? data.golden_slot_start() : DEFAULT_SLOT_START;
                    diamondSlotStart = isValidSlotStart(data.diamond_slot_start(), SatchelTier.DIAMOND)
                            ? data.diamond_slot_start() : DEFAULT_SLOT_START;
                    shiftSwap = data.shift_swap();
                    satchelLayer = data.satchel_layer();
                    guiAnimation = data.gui_animation();
                    satchelHiddenInInventory = data.satchel_hidden_in_inventory();

                    // null means the key is absent (config written by an older version); an
                    // empty list is a deliberate "no menu gets the corner pixel" and is kept.
                    if (data.corner_menus() != null) {
                        cornerMenusRaw = new ArrayList<>(data.corner_menus());
                        cornerMenusRaw.removeIf(Objects::isNull);
                        cornerMenusPresent = true;
                    }
                }
            } catch (IOException | JsonParseException e) {
                LOGGER.warn("satchels: failed to read satchels-client.json, using defaults", e);
            }
        }

        // No corner_menus in the file yet. Fresh install (no config at all): the built-in
        // CORNER_MENU_DEFAULTS. Upgrade (config exists, key missing): start from the menus the
        // satchel is currently allowed in, so every panel keeps drawing the corner pixel exactly
        // as before until the player trims the list. SatchelsCommonConfig.load() has already run
        // by now — Fabric initialises all "main" entrypoints before any "client" one. An empty
        // allowed list also falls back to the defaults, so a load-order surprise can never
        // persist an empty corner_menus that silently disables the pixel.
        if (!cornerMenusPresent) {
            List<String> seed = configExisted ? SatchelsCommonConfig.getAllowedMenuIds() : List.of();
            cornerMenusRaw = new ArrayList<>(seed.isEmpty() ? CORNER_MENU_DEFAULTS : seed);
        }
        rebuildCornerLookup();

        save();
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(new Data(goldenSlotStart, diamondSlotStart, shiftSwap, satchelLayer, guiAnimation, satchelHiddenInInventory, cornerMenusRaw)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("satchels: failed to save satchels-client.json", e);
        }
    }

    public static boolean shouldSwapWithShiftKey() {
        return shiftSwap;
    }

    public static boolean shouldRenderSatchel() {
        return satchelLayer;
    }

    public static boolean shouldAnimateGUI() {
        return guiAnimation;
    }

    public static boolean isSatchelHiddenInInventory() {
        return satchelHiddenInInventory;
    }

    /**
     * Whether the satchel row on the menu identified by {@code menuLocation} (the same key
     * {@link SatchelMenuLocation#resolve} produces for {@code allowed_menus}) should also draw its
     * corner "tuck" pixel — the second scissor pass, {@code
     * ScreenWithSatchel#renderSatchelInventoryCorners}. Independent of {@code allowed_menus}: a
     * menu must be allowed <i>and</i> listed in {@code corner_menus} to get the pixel.
     */
    public static boolean isCornerEnabled(Identifier menuLocation) {
        return cornerMenus.contains(menuLocation);
    }

    private static void rebuildCornerLookup() {
        cornerMenus.clear();

        for (String entry : cornerMenusRaw) {
            if (entry == null) continue;

            String trimmed = entry.trim();
            // Cloth's list widget happily keeps a blank row, and Identifier.tryParse("") would
            // accept it as "minecraft:" — skip silently instead of warning about nothing.
            if (trimmed.isEmpty()) continue;

            Identifier location = Identifier.tryParse(trimmed);
            if (location == null) {
                LOGGER.warn("satchels: invalid entry in corner_menus, skipping: '{}'", entry);
                continue;
            }
            cornerMenus.add(location);
        }
    }

    // region Used by the Cloth Config GUI screen (SatchelsConfigScreen, via Mod Menu)
    public static void setShiftSwap(boolean value) {
        shiftSwap = value;
        save();
    }

    public static void setSatchelLayer(boolean value) {
        satchelLayer = value;
        save();
    }

    public static void setGuiAnimation(boolean value) {
        guiAnimation = value;
        save();
    }

    /**
     * Purely a client-side render flag for {@code ScreenWithSatchel.renderSatchelInventory} —
     * not synced to the server, and independent of {@code SatchelData#isActive()} (which drives
     * the hotbar swap/overlay via the {@code V} key).
     */
    public static void setSatchelHiddenInInventory(boolean value) {
        satchelHiddenInInventory = value;
        save();
    }

    public static List<String> getCornerMenusRaw() {
        return new ArrayList<>(cornerMenusRaw);
    }

    /** The config screen's "reset" value for {@code corner_menus}: {@link #CORNER_MENU_DEFAULTS}. */
    public static List<String> getDefaultCornerMenus() {
        return CORNER_MENU_DEFAULTS;
    }

    /** Accepts a new list of raw entries, validates them, rebuilds the lookup set and saves. */
    public static void setCornerMenusRaw(List<String> value) {
        cornerMenusRaw = new ArrayList<>(value);
        rebuildCornerLookup();
        save();
    }
    // endregion

    // region Per-tier hotbar slot-start

    /**
     * The 1-based slot {@code tier}'s satchel starts from on the hotbar. Netherite always
     * returns {@link #DEFAULT_SLOT_START} (no persisted field); use
     * {@link #getHotbarOffset(SatchelTier)} for its actual 0-based network offset.
     */
    public static int getSlotStart(SatchelTier tier) {
        return switch (tier) {
            case GOLDEN -> goldenSlotStart;
            case DIAMOND -> diamondSlotStart;
            case NETHERITE -> DEFAULT_SLOT_START;
        };
    }

    /** Highest valid 1-based slot-start for {@code tier}: {@code 9 - slotsInTier + 1}. */
    public static int getMaxSlotStart(SatchelTier tier) {
        return 9 - tier.getSlotCount() + 1;
    }

    private static boolean isValidSlotStart(int value, SatchelTier tier) {
        return value >= DEFAULT_SLOT_START && value <= getMaxSlotStart(tier);
    }

    /**
     * {@code tier}'s persisted slot-start converted to the 0-based offset that
     * {@code SatchelData#setHotbarOffset}/{@code SatchelOffsetUpdatePacketC2S} use on the wire.
     * Netherite is hardcoded to 0 (fills the whole hotbar; has no persisted field).
     */
    public static int getHotbarOffset(SatchelTier tier) {
        if (tier == SatchelTier.NETHERITE) return 0;
        return getSlotStart(tier) - 1;
    }

    /**
     * Called from the config screen's per-tier slider. Clamps to {@code 1..maxStart(tier)},
     * saves, and — if the local player currently has this tier equipped — applies the offset
     * live and pushes it to the server. No-op for {@link SatchelTier#NETHERITE}.
     */
    public static void updateSlotStart(SatchelTier tier, int start) {
        if (tier == SatchelTier.NETHERITE) return;

        int clamped = Math.max(DEFAULT_SLOT_START, Math.min(getMaxSlotStart(tier), start));
        switch (tier) {
            case GOLDEN -> goldenSlotStart = clamped;
            case DIAMOND -> diamondSlotStart = clamped;
            case NETHERITE -> { return; }
        }
        save();

        if (Minecraft.getInstance().getConnection() == null) return;
        SatchelData localData = SatchelData.get(Minecraft.getInstance().player);
        if (localData.getCurrentTier() != tier) return;

        int offset = clamped - 1;
        localData.setHotbarOffset(offset);
        ClientPlayNetworking.send(new SatchelOffsetUpdatePacketC2S(offset));
    }

    /**
     * Applies {@code tier}'s persisted slot-start to {@code data}'s hotbar offset and syncs it
     * to the server. Called from {@code SatchelData#updateTierFromStack} whenever a new tier
     * becomes equipped on the local player.
     */
    public static void applyPersistedOffsetForTier(SatchelData data, SatchelTier tier) {
        int offset = getHotbarOffset(tier);
        data.setHotbarOffset(offset);

        if (Minecraft.getInstance().getConnection() != null) {
            ClientPlayNetworking.send(new SatchelOffsetUpdatePacketC2S(offset));
        }
    }
    // endregion
}
