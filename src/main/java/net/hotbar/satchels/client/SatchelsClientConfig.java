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
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelTier;
import net.hotbar.satchels.network.packets.SatchelOffsetUpdatePacketC2S;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

    private record Data(int golden_slot_start, int diamond_slot_start, boolean shift_swap, boolean satchel_layer, boolean gui_animation, boolean satchel_hidden_in_inventory) {
    }

    /** Call from {@code SatchelsClient.onInitializeClient()}. Reads the config file, or creates it with defaults. */
    public static void load() {
        if (Files.exists(FILE)) {
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
                }
            } catch (IOException | JsonParseException e) {
                LOGGER.warn("satchels: failed to read satchels-client.json, using defaults", e);
            }
        }

        save();
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(new Data(goldenSlotStart, diamondSlotStart, shiftSwap, satchelLayer, guiAnimation, satchelHiddenInInventory)), StandardCharsets.UTF_8);
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
