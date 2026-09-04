package net.hotbar.satchels;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side config (which menus the satchel can attach to, with per-menu slot offsets,
 * plus a debug logging toggle). Persisted directly via GSON to {@code config/satchels-common.json}.
 * <p>
 * Cloth Config is used for the GUI screen ({@code SatchelsConfigScreen}, accessible via Mod
 * Menu) and does not persist data on its own — hence the manual GSON read/write here.
 * <p>
 * {@code allowed_menus} entries use the format
 * {@code resource:location [xOffset yOffset] [overlayXOffset overlayYOffset]}.
 */
public class SatchelsCommonConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("satchels-common.json");

    private static boolean logOpenedMenu = false;
    private static List<String> allowedMenusRaw = new ArrayList<>(getMenuDefaults());

    private static final List<ResourceLocation> allowed = new ArrayList<>();
    private static final Map<ResourceLocation, Tuple<Integer, Integer>> offsets = new HashMap<>();
    private static final Map<ResourceLocation, Tuple<Integer, Integer>> overlayOffsets = new HashMap<>();

    private record Data(boolean log_opened_menu, List<String> allowed_menus) {
    }

    /** Call from {@code Satchels.onInitialize()}. Reads the config file, or creates it with defaults. */
    public static void load() {
        if (Files.exists(FILE)) {
            try {
                Data data = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Data.class);
                if (data != null) {
                    logOpenedMenu = data.log_opened_menu();
                    if (data.allowed_menus() != null && !data.allowed_menus().isEmpty()) {
                        allowedMenusRaw = new ArrayList<>(data.allowed_menus());
                    }
                }
            } catch (IOException | JsonParseException e) {
                LOGGER.warn("satchels: failed to read satchels-common.json, using defaults", e);
            }
        }

        rebuildLookups();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(new Data(logOpenedMenu, allowedMenusRaw)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("satchels: failed to save satchels-common.json", e);
        }
    }

    private static void rebuildLookups() {
        allowed.clear();
        offsets.clear();
        overlayOffsets.clear();

        for (String entry : allowedMenusRaw) {
            if (!validateMenu(entry)) {
                LOGGER.warn("satchels: invalid entry in allowed_menus, skipping: '{}'", entry);
                continue;
            }

            String[] split = entry.split(" ");
            ResourceLocation location = ResourceLocation.parse(split[0]);
            allowed.add(location);

            if (split.length == 3) {
                int x = Integer.parseInt(split[1]);
                int y = Integer.parseInt(split[2]);
                offsets.put(location, new Tuple<>(x, y));
            } else if (split.length == 5) {
                int x = Integer.parseInt(split[1]);
                int y = Integer.parseInt(split[2]);
                int xOverlay = Integer.parseInt(split[3]);
                int yOverlay = Integer.parseInt(split[4]);
                offsets.put(location, new Tuple<>(x, y));
                overlayOffsets.put(location, new Tuple<>(xOverlay, yOverlay));
            }
        }
    }

    public static boolean shouldLog() {
        return logOpenedMenu;
    }

    public static boolean isAllowed(ResourceLocation menuLocation) {
        return allowed.contains(menuLocation);
    }

    public static Tuple<Integer, Integer> getOffset(ResourceLocation menuLocation) {
        return offsets.getOrDefault(menuLocation, new Tuple<>(0, 0));
    }

    public static Tuple<Integer, Integer> getOverlayOffset(ResourceLocation menuLocation) {
        return overlayOffsets.getOrDefault(menuLocation, new Tuple<>(0, 0));
    }

    // region Used by the Cloth Config GUI screen (SatchelsConfigScreen, via Mod Menu)
    public static List<String> getAllowedMenusRaw() {
        return List.copyOf(allowedMenusRaw);
    }

    public static void setLogOpenedMenu(boolean value) {
        logOpenedMenu = value;
    }

    /** Accepts a new list of raw entries, validates them and rebuilds the lookup maps. */
    public static void setAllowedMenusRaw(List<String> value) {
        allowedMenusRaw = new ArrayList<>(value);
        rebuildLookups();
    }
    // endregion

    private static boolean validateMenu(Object input) {
        if (!(input instanceof String entry)) return false;

        String[] split = entry.split(" ");
        if (split.length == 1) {
            ResourceLocation location = ResourceLocation.tryParse(split[0]);
            return location != null;
        } else if (split.length == 3) {
            ResourceLocation location = ResourceLocation.tryParse(split[0]);
            if (location == null) return false;

            try {
                Integer.parseInt(split[1]);
                Integer.parseInt(split[2]);
                return true;
            } catch (NumberFormatException ignored) {
            }
        } else if (split.length == 5) {
            ResourceLocation location = ResourceLocation.tryParse(split[0]);
            if (location == null) return false;

            try {
                Integer.parseInt(split[1]);
                Integer.parseInt(split[2]);
                Integer.parseInt(split[3]);
                Integer.parseInt(split[4]);
                return true;
            } catch (NumberFormatException ignored) {
            }
        }

        return false;
    }

    /** Default set of menus the satchel attaches to out of the box. */
    private static List<String> getMenuDefaults() {
        return List.of(
                "minecraft:inventory",
                "minecraft:crafting",
                "minecraft:crafter_3x3",
                "minecraft:generic_9x1 0 -35 0 -1",
                "minecraft:generic_9x2 0 -17 0 -1",
                "minecraft:generic_9x3 0 1 0 -1",
                "minecraft:generic_9x4 0 19 0 -1",
                "minecraft:generic_9x5 0 37 0 -1",
                "minecraft:generic_9x6 0 55 0 -1",
                "minecraft:shulker_box 0 0 0 -1",
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
                "minecraft:hopper 0 -33",
                "minecraft:merchant 100 0 100 0",
                "minecraft:beacon 28 53 28 0",
                "farmersdelight:cooking_pot",
                "curios:curios_container",
                "accessories:original_menu",
                "create:schematic_table 30 23 30 -8",
                "create:schematicannon 29 77 29 -8",
                "create:toolbox 0 81 0 -8",
                "create:package_port 30 24 30 0",
                "supplementaries:sack",
                "slag:melter",
                "slag:interface",
                "slag:forge",
                "brewinandchewin:keg"
        );
    }
}
