package net.hotbar.satchels.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.hotbar.satchels.Satchels;
import net.hotbar.satchels.content.satchel.SatchelTier;

import java.util.EnumMap;
import java.util.Map;

/** Sprite resource locations for the satchel GUI textures ({@code textures/gui/sprites/*}). */
@Environment(EnvType.CLIENT)
public class ModSprites {
    /**
     * A GUI sprite id paired with its exact pixel size, for
     * {@code GuiGraphics#blitSprite(ResourceLocation, int, int, int, int)}.
     */
    public record Sprite(ResourceLocation id, int width, int height) {}

    /**
     * Per-tier inventory-row background, used by {@code ScreenWithSatchel#renderSatchelInventory}.
     * Widths (64/118/172 for 3/6/9 slots) follow {@code 10 + slotCount * 18}, but are listed
     * explicitly rather than computed — see {@link #HOTBAR_SPRITES}, whose widths don't.
     */
    private static final Map<SatchelTier, Sprite> INVENTORY_SPRITES = new EnumMap<>(SatchelTier.class);

    /**
     * Per-tier hotbar-overlay background, used by {@code SatchelHotbarOverlay}. Widths
     * (61/121/182) don't follow a clean formula from slot count — always read them off the
     * actual PNG dimensions when a texture changes, rather than assuming a pattern. Each PNG
     * includes a 1px left border column so the overlay fully covers the vanilla hotbar's left
     * edge; {@code SatchelHotbarOverlay} draws starting exactly at the vanilla hotbar's left
     * edge to match.
     */
    private static final Map<SatchelTier, Sprite> HOTBAR_SPRITES = new EnumMap<>(SatchelTier.class);

    static {
        INVENTORY_SPRITES.put(SatchelTier.GOLDEN, new Sprite(Satchels.at("satchel_inventory_golden"), 64, 27));
        INVENTORY_SPRITES.put(SatchelTier.DIAMOND, new Sprite(Satchels.at("satchel_inventory_diamond"), 118, 27));
        INVENTORY_SPRITES.put(SatchelTier.NETHERITE, new Sprite(Satchels.at("satchel_inventory_netherite"), 172, 27));

        HOTBAR_SPRITES.put(SatchelTier.GOLDEN, new Sprite(Satchels.at("satchel_hotbar_golden"), 61, 22));
        HOTBAR_SPRITES.put(SatchelTier.DIAMOND, new Sprite(Satchels.at("satchel_hotbar_diamond"), 121, 22));
        HOTBAR_SPRITES.put(SatchelTier.NETHERITE, new Sprite(Satchels.at("satchel_hotbar_netherite"), 182, 22));
    }

    public static Sprite getInventorySprite(SatchelTier tier) {
        return INVENTORY_SPRITES.get(tier);
    }

    public static Sprite getHotbarSprite(SatchelTier tier) {
        return HOTBAR_SPRITES.get(tier);
    }

    /** The single equip-slot widget on the vanilla inventory screen — one fixed size regardless of tier. */
    public static final ResourceLocation SATCHEL_SLOT_INVENTORY = Satchels.at("satchel_slot_inventory");
    public static final ResourceLocation SATCHEL_SLOT_ICON = Satchels.at("slot/satchel");
    public static final ResourceLocation SATCHEL_HOTBAR_SELECTION = Satchels.at("satchel_hotbar_selection");
    public static final ResourceLocation VANILLA_HOTBAR_SELECTION = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");
}
