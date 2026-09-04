package net.hotbar.satchels.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.component.DyedItemColor;
import net.hotbar.satchels.ModItems;
import net.hotbar.satchels.SatchelsCommonConfig;
import net.hotbar.satchels.SatchelsEventHooks;
import net.hotbar.satchels.client.model.SatchelLayer;
import net.hotbar.satchels.client.satchel.SatchelHotbarOverlay;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelItem;
import net.hotbar.satchels.content.satchel.SatchelTier;
import net.hotbar.satchels.network.packets.SatchelInventorySyncPacketS2C;
import net.hotbar.satchels.network.packets.SatchelSlotUpdatePacketS2C;
import net.hotbar.satchels.network.packets.SatchelStatusPacketS2C;
import net.hotbar.satchels.network.packets.ToggleSatchelPacketC2S;
import org.lwjgl.glfw.GLFW;

/**
 * Client mod entry point: keybinding, HUD overlay, satchel render layer, item color handler,
 * and client-side networking/menu-open hooks.
 * <p>
 * The HUD overlay is registered through {@code HudRenderCallback.EVENT} rather than a
 * layered-HUD API (Fabric API's layer registration classes for the {@code LayeredDrawer} HUD
 * rework arrived in a later fabric-api version than this project targets) — it renders on top
 * of the rest of the HUD, the closest equivalent available here to "render above the hotbar".
 * If porting to a fabric-api version with layered-HUD support, consider migrating this.
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
            "key.satchels.toggle_satchel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, KeyMapping.CATEGORY_INVENTORY
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
        registerExtraModels();

        ClientTickEvents.END_CLIENT_TICK.register(SatchelsClient::endClientTick);
        ScreenEvents.BEFORE_INIT.register(SatchelsClient::onScreenOpen);
    }

    private static void registerKeyMappings() {
        KeyBindingHelper.registerKeyBinding(KEYMAPPING_TOGGLE_SATCHEL);
    }

    private static void registerOverlays() {
        HudRenderCallback.EVENT.register(SatchelHotbarOverlay.INSTANCE::render);
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
        // Note: KeyMapping#matches(int, int) — Mojang mappings, not "matchesKey".
        ScreenKeyboardEvents.afterKeyPress(screen).register((scrn, key, scancode, modifiers) -> {
            if (!KEYMAPPING_TOGGLE_SATCHEL.matches(key, scancode)) return;
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

        ResourceLocation location = SatchelMenuLocation.resolve(abs.getMenu());
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

    private static void addEntityRenderLayers() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityRenderer instanceof PlayerRenderer playerRenderer) {
                registrationHelper.register(new SatchelLayer<>(playerRenderer, context.getItemRenderer()));
            }
        });
    }

    /**
     * Registers each tier's {@code satchels:item/satchel_worn_<tier>} model as an extra model
     * to bake, even though nothing in the standard item-model tree references them directly.
     * <p>
     * These models are baked and fetched manually by {@code SatchelLayer} (picking the id for
     * whichever tier is currently equipped) for rendering the worn satchel on the player's
     * back; without this explicit registration they wouldn't be picked up for baking at all,
     * and {@code SatchelLayer} would get {@code null} from the baked model manager.
     */
    private static void registerExtraModels() {
        ModelLoadingPlugin.register(context -> {
            for (SatchelTier tier : SatchelTier.values()) {
                context.addModels(tier.getWornModelId());
            }
        });
    }

    private static void registerItemColorHandlers() {
        ColorProviderRegistry.ITEM.register((stack, layer) -> {
            if (layer == 0) return DyedItemColor.getOrDefault(stack, SatchelItem.DEFAULT_COLOR);
            return 0xffffffff;
        }, ModItems.SATCHEL_GOLDEN, ModItems.SATCHEL_DIAMOND, ModItems.SATCHEL_NETHERITE);
    }

    private static void onScreenOpen(Minecraft client, net.minecraft.client.gui.screens.Screen screen, int width, int height) {
        if (!(screen instanceof AbstractContainerScreen<?> abs)) return;
        if (client.player == null) return;

        AbstractContainerMenu menu = abs.getMenu();
        SatchelsEventHooks.onMenuOpen(client.player, menu);

        registerGuiToggleKeyHandling(client, screen);
    }
}
