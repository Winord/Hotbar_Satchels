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
 * The Cloth Config settings screen is reached through Mod Menu; entrypoint is
 * {@code SatchelsModMenuPlugin}, which delegates to {@code SatchelsConfigScreen}. Config is
 * persisted directly through GSON, not Cloth Config's own file storage.
 * <p>
 * The {@code V} key ({@link #KEYMAPPING_TOGGLE_SATCHEL}) is context-dependent: outside any
 * container screen it fully toggles the satchel (hotbar swap + overlay, synced to the server);
 * with an {@code allowed_menus} container screen open it instead hides/shows the satchel row on
 * just that screen, a purely client-side flag. Split across two hooks — {@link #endClientTick}
 * and {@link #registerGuiToggleKeyHandling} — since vanilla only delivers {@code KeyMapping}
 * clicks to {@code consumeClick()} while no {@code Screen} is open.
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

        // Flashback replay mod compat. Initialized here (client entrypoint), not in
        // SatchelsCompat, since that enum's field initializers run on both sides at
        // class-load time and this is an @Environment(CLIENT)-only class.
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
     * Handles {@code V} for the "no screen open" case only — vanilla's {@code KeyboardHandler}
     * never delivers {@code KeyMapping} clicks to {@code consumeClick()} while any
     * {@code Screen} is open. The in-GUI half is {@link #registerGuiToggleKeyHandling}.
     */
    private static void endClientTick(Minecraft client) {
        while (KEYMAPPING_TOGGLE_SATCHEL.consumeClick()) {
            toggleSatchel(client);
        }
    }

    /**
     * {@code V}'s other half: while a container screen is open, {@code consumeClick()} never
     * fires (see {@link #endClientTick}). {@code ScreenKeyboardEvents.afterKeyPress} is Fabric
     * API's hook for keybinds that should still work with a GUI open; registered fresh per
     * screen instance from {@link #onScreenOpen} and cleaned up with it.
     */
    private static void registerGuiToggleKeyHandling(Minecraft client, net.minecraft.client.gui.screens.Screen screen) {
        ScreenKeyboardEvents.afterKeyPress(screen).register((scrn, event) -> {
            if (!KEYMAPPING_TOGGLE_SATCHEL.matches(event)) return;
            if (!isAllowedContainerScreenOpen(client)) return;

            toggleInventorySatchelVisibility();
        });
    }

    /** True when the open screen's menu is on the {@code allowed_menus} list. */
    private static boolean isAllowedContainerScreenOpen(Minecraft client) {
        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> abs)) return false;

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

    private static void addEntityRenderLayers() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityRenderer instanceof AvatarRenderer<?> avatarRenderer) {
                @SuppressWarnings("unchecked")
                AvatarRenderer<AbstractClientPlayer> playerRenderer = (AvatarRenderer<AbstractClientPlayer>) avatarRenderer;
                registrationHelper.register(new SatchelLayer<
                        net.minecraft.client.renderer.entity.state.AvatarRenderState,
                        net.minecraft.client.model.player.PlayerModel>(
                        playerRenderer, context.getItemModelResolver()));
            }
        });
    }

    /**
     * Intentionally empty. Item tinting is fully data-driven via a {@code minecraft:dye}
     * {@code tint_source} on the model's dyeable layer (see {@code SatchelsModelProvider} and
     * {@code assets/satchels/items/satchel_worn_*.json}) — there's no Java-side color-handler
     * API to register with any more.
     */
    private static void registerItemColorHandlers() {
    }

    private static void onScreenOpen(Minecraft client, net.minecraft.client.gui.screens.Screen screen, int width, int height) {
        if (!(screen instanceof AbstractContainerScreen<?> abs)) return;
        if (client.player == null) return;

        AbstractContainerMenu menu = abs.getMenu();
        SatchelsEventHooks.onMenuOpen(client.player, menu);

        registerGuiToggleKeyHandling(client, screen);
    }
}