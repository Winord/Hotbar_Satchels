package net.hotbar.satchels.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.component.DyedItemColor;
import net.hotbar.satchels.ModItems;
import net.hotbar.satchels.SatchelsCommonConfig;
import net.hotbar.satchels.SatchelsEventHooks;
import net.hotbar.satchels.client.model.SatchelLayer;
import net.hotbar.satchels.client.satchel.SatchelHotbarOverlay;
import net.hotbar.satchels.compat.flashback.FlashbackCompat;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelItem;
import net.hotbar.satchels.network.packets.SatchelInventorySyncPacketS2C;
import net.hotbar.satchels.network.packets.SatchelSlotUpdatePacketS2C;
import net.hotbar.satchels.network.packets.SatchelStatusPacketS2C;
import net.hotbar.satchels.network.packets.ToggleSatchelPacketC2S;
import org.lwjgl.glfw.GLFW;

/**
 * Client mod entry point: keybinding, HUD overlay, satchel render layer, item color handler,
 * and client-side networking/menu-open hooks.
 * <p>
 * The HUD overlay is registered via {@code HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ...)}
 * (Fabric API's layered-HUD API, available since Fabric API 0.116 / Minecraft 26.1).
 * The layer renders immediately before the chat HUD layer — visually above the hotbar.
 * <p>
 * No explicit join/respawn hook is needed to sync the per-tier hotbar slot-start:
 * {@code SatchelData#resyncToClient()} already re-sends the equipped-satchel stack on every
 * login, respawn, and dimension change, and receiving it client-side re-derives the tier and
 * re-applies the slot-start as a side effect (see {@code SatchelData#updateTierFromStack} and
 * {@code SatchelsClientConfig#applyPersistedOffsetForTier}).
 * <p>
 * The Cloth Config settings screen is accessible through Mod Menu (gear icon next to Hotbar
 * Satchels in the mods list). Entrypoint is {@code SatchelsModMenuPlugin}, registered as
 * {@code "modmenu"} in {@code fabric.mod.json}; it delegates to {@code SatchelsConfigScreen}.
 * Config data is persisted directly through GSON ({@code SatchelsCommonConfig}/
 * {@code SatchelsClientConfig}) — Cloth Config's own file storage is not used.
 * <p>
 * The {@code V} key ({@link #KEYMAPPING_TOGGLE_SATCHEL}) is context-dependent: outside any
 * container screen it fully toggles the satchel (hotbar swap + overlay, synced to the server);
 * with an {@code allowed_menus} container screen open it instead hides/shows the satchel row on
 * just that screen, a purely client-side flag that never touches the player's actual
 * equipped/active state. Split across two hooks since vanilla only delivers {@code KeyMapping}
 * clicks to {@link #endClientTick}'s {@code consumeClick()} while no {@code Screen} is open —
 * see {@link #endClientTick} and {@link #registerGuiToggleKeyHandling}.
 */
public class SatchelsClient implements ClientModInitializer {
    public static final KeyMapping KEYMAPPING_TOGGLE_SATCHEL = new KeyMapping(
            "key.satchels.toggle_satchel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, KeyMapping.Category.INVENTORY
    );

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SatchelSlotUpdatePacketS2C.TYPE, (packet, context) -> {
            Minecraft mc = context.client();
            if (mc.level != null) SatchelSlotUpdatePacketS2C.handle(packet, mc.level);
        });
        ClientPlayNetworking.registerGlobalReceiver(SatchelStatusPacketS2C.TYPE, (packet, context) -> {
            if (context.player() != null) SatchelStatusPacketS2C.handle(packet, context.player());
        });
        ClientPlayNetworking.registerGlobalReceiver(SatchelInventorySyncPacketS2C.TYPE, (packet, context) -> {
            if (context.player() != null) SatchelInventorySyncPacketS2C.handle(packet, context.player());
        });

        SatchelsClientConfig.load();

        registerKeyMappings();
        registerOverlays();
        addEntityRenderLayers();
        registerItemColorHandlers();


        // Flashback replay mod compat: deferred SatchelSlotUpdatePacketS2C application.
        // Initialized here (client entrypoint) rather than in SatchelsCompat to avoid
        // loading an @Environment(CLIENT) class on the dedicated server — enum field
        // initializers in SatchelsCompat run on both sides at class-load time.
        if (FabricLoader.getInstance().isModLoaded("flashback")) {
            new FlashbackCompat().initialize();
        }

        ClientTickEvents.END_CLIENT_TICK.register(SatchelsClient::endClientTick);
        ScreenEvents.BEFORE_INIT.register(SatchelsClient::onScreenOpen);
    }

    private static void registerKeyMappings() {
        KeyMappingHelper.registerKeyMapping(KEYMAPPING_TOGGLE_SATCHEL);
    }

    private static void registerOverlays() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                net.hotbar.satchels.Satchels.at(SatchelHotbarOverlay.ID),
                SatchelHotbarOverlay.INSTANCE::render
        );
    }

    /**
     * Handles {@code V} for the "no screen open" case only. Vanilla's {@code KeyboardHandler}
     * never calls {@code KeyMapping.click()} for a key while any {@code Screen} is open — so
     * {@code consumeClick()} silently never fires while a container screen is up, regardless of
     * {@code allowed_menus}. That's fine here: this branch is only ever meant to run the full
     * hotbar toggle, which only makes sense with no screen open anyway. The in-GUI half of the
     * behavior is handled separately by {@link #registerGuiToggleKeyHandling} below, via
     * {@code ScreenKeyboardEvents}, which — unlike {@code consumeClick()} — does fire while a
     * screen is open.
     */
    private static void endClientTick(Minecraft client) {
        while (KEYMAPPING_TOGGLE_SATCHEL.consumeClick()) {
            toggleSatchel(client);
        }
    }

    /**
     * {@code V}'s other half: while an {@code allowed_menus} container screen is open,
     * {@code KEYMAPPING_TOGGLE_SATCHEL.consumeClick()} in {@link #endClientTick} never fires at
     * all — vanilla's {@code KeyboardHandler} only calls {@code KeyMapping.click()} when
     * {@code Minecraft.screen == null}. Reusing {@code ScreenKeyboardEvents.afterKeyPress}
     * (Fabric API's hook specifically meant for keybinds that should still work with a GUI open)
     * fixes that: registered fresh per screen instance from {@link #onScreenOpen}, so it only
     * ever fires while that particular screen is open and is cleaned up with it.
     */
    private static void registerGuiToggleKeyHandling(Minecraft client, net.minecraft.client.gui.screens.Screen screen) {
        // 26.1: both changed together — confirmed via the real Fabric API 26.1.2 branch source
        // and the real jar. ScreenKeyboardEvents.AfterKeyPress is now (Screen, KeyEvent), and
        // KeyMapping#matches now takes a KeyEvent directly instead of (int key, int scancode).
        ScreenKeyboardEvents.afterKeyPress(screen).register((scrn, event) -> {
            if (!KEYMAPPING_TOGGLE_SATCHEL.matches(event)) return;
            if (!isAllowedContainerScreenOpen(client)) return;

            toggleInventorySatchelVisibility();
        });
    }

    /**
     * True when the currently open screen is a container screen whose menu is on the
     * {@code allowed_menus} list — the same gate already used for satchel rendering/clicks
     * ({@code SatchelsCommonConfig.isAllowed}), reused here so {@code V}'s two behaviors switch
     * on exactly the same condition as whether the satchel row is shown on that screen at all.
     */
    private static boolean isAllowedContainerScreenOpen(Minecraft client) {
        if (!(client.screen instanceof AbstractContainerScreen<?> abs)) return false;

        Identifier location = SatchelMenuLocation.resolve(abs.getMenu());
        return location != null && SatchelsCommonConfig.isAllowed(location);
    }

    public static void toggleSatchel(Minecraft client) {
        if (client.player == null) return;
        SatchelData satchelData = SatchelData.get(client.player);

        if (!satchelData.canAccess()) return;
        boolean willEnable = !satchelData.isActive();
        satchelData.setActive(willEnable, true);
        ClientPlayNetworking.send(new ToggleSatchelPacketC2S(willEnable));
    }

    /**
     * Toggles whether the satchel content row is visually hidden on the vanilla inventory
     * screen. Unlike {@link #toggleSatchel}, this is a pure client-side render flag — it does
     * not touch {@link SatchelData#isActive()} (the hotbar swap/overlay driven by the {@code V}
     * key) and needs no server sync, since it only affects how {@code ScreenWithSatchel} draws
     * on this one screen.
     */
    public static void toggleInventorySatchelVisibility() {
        SatchelsClientConfig.setSatchelHiddenInInventory(!SatchelsClientConfig.isSatchelHiddenInInventory());
    }

    // TODO(26.1 port): PlayerRenderer was replaced by the generic
    // AvatarRenderer<AvatarlikeEntity extends Avatar> (net.minecraft.client.renderer.entity.player,
    // Fabric render refactor for 26.1). For the actual player entity this is
    // AvatarRenderer<AbstractClientPlayer>. SatchelLayer's constructor/generic bound
    // (currently written against RenderLayerParent<PlayerRenderState, PlayerModel> or similar
    // from the old PlayerRenderer) will need to be updated to match AvatarRenderer's new
    // render-state type (AvatarRenderState) — verify against generated sources before building.
    private static void addEntityRenderLayers() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityRenderer instanceof AvatarRenderer<?> avatarRenderer) {
                @SuppressWarnings("unchecked")
                AvatarRenderer<AbstractClientPlayer> playerRenderer = (AvatarRenderer<AbstractClientPlayer>) avatarRenderer;
                // 26.1: SatchelLayer rewritten for AvatarRenderState + PlayerModel.
                registrationHelper.register(new SatchelLayer<
                        net.minecraft.client.renderer.entity.state.AvatarRenderState,
                        net.minecraft.client.model.player.PlayerModel>(
                        playerRenderer, context.getItemModelResolver()));
            }
        });
    }

    /**
     * 26.1: removed entirely. {@code ModelLoadingPlugin.Context#addModels} — the whole Fabric
     * "extra model" registration API this used — was replaced by a completely different
     * {@code ExtraModelKey}/{@code UnbakedExtraModel} mechanism (confirmed against the real
     * Fabric API 26.1.2 branch source on GitHub). But tracing {@code ModelManager} /
     * {@code ClientItemInfoLoader} in the actual 26.1.2 jar showed the "extra model" concept
     * isn't needed here at all anymore: any json under {@code assets/<ns>/items/} is
     * auto-baked and retrievable via {@code ModelManager#getItemModel(Identifier)} regardless
     * of whether it's tied to a registered Item — that's a real architecture simplification,
     * not a rename. See the {@code assets/satchels/items/satchel_worn_*.json} wrapper files and
     * {@code SatchelTier#getWornModelId}, which {@code SatchelLayer} now reads directly with no
     * registration step required.
     */

    /**
     * TODO(26.1 port): {@code ColorProviderRegistry.ITEM} — and the whole imagined
     * {@code net.fabricmc.fabric.api.client.rendering.v1.item.ItemColorRegistry} — do not exist.
     * {@code ColorProviderRegistry.ITEM} was removed as far back as 1.21.4: item tinting is now
     * data-driven via a {@code tint_source} entry on the relevant layer in the item's client
     * model JSON (e.g. {@code {"type": "minecraft:dye", "default": <argb>}}, the same mechanism
     * vanilla uses for dyed leather armor/bundles), not a Java-side color callback. Since this
     * mod already stores the dye as a {@code DyedItemColor} data component (see
     * {@link SatchelItem#DEFAULT_COLOR} usage), the layer-0 texture in
     * {@code SatchelsModelProvider}'s generated model likely just needs a
     * {@code minecraft:dye} tint source with {@code default: SatchelItem.DEFAULT_COLOR} instead
     * of any Java registration here. This method (and its call in {@link #onInitializeClient})
     * should be removed once the model-side tint_source is wired up — left as a stub so the
     * project still compiles while that's sorted out.
     */
    private static void registerItemColorHandlers() {
        // Intentionally empty — see TODO above. Was: ItemColorRegistry.register(...).
    }

    private static void onScreenOpen(Minecraft client, net.minecraft.client.gui.screens.Screen screen, int width, int height) {
        if (!(screen instanceof AbstractContainerScreen<?> abs)) return;
        if (client.player == null) return;

        AbstractContainerMenu menu = abs.getMenu();
        SatchelsEventHooks.onMenuOpen(client.player, menu);

        registerGuiToggleKeyHandling(client, screen);
    }
}
