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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.hotbar.satchels.ModItems;
import net.hotbar.satchels.SatchelsCommonConfig;
import net.hotbar.satchels.SatchelsEventHooks;
import net.hotbar.satchels.client.model.SatchelLayer;
import net.hotbar.satchels.client.satchel.SatchelHotbarOverlay;
import net.hotbar.satchels.compat.flashback.FlashbackCompat;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelTier;
import net.hotbar.satchels.network.packets.SatchelInventorySyncPacketS2C;
import net.hotbar.satchels.network.packets.SatchelSlotUpdatePacketS2C;
import net.hotbar.satchels.network.packets.SatchelStatusPacketS2C;
import net.hotbar.satchels.network.packets.ToggleSatchelPacketC2S;
import org.lwjgl.glfw.GLFW;

/**
 * Client mod entry point.
 * <p>
 * <b>1.21.4 change — item color tinting:</b> {@code ColorProviderRegistry.registerItemColors}
 * and the entire {@code ItemColors} API were removed in 1.21.4. Item color tints are now
 * controlled by {@code assets/<namespace>/items/<item>.json} files (the new item model
 * definition system). For dyeable satchels, each item has a corresponding JSON file in
 * {@code assets/satchels/items/} that specifies {@code "minecraft:dye"} as its tint source.
 * {@code registerItemColorHandlers()} has been removed accordingly.
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
        // registerItemColorHandlers() removed: ColorProviderRegistry API was dropped in 1.21.4.
        // Dye tinting is now handled by assets/satchels/items/<satchel>.json (minecraft:dye tint source).
        registerExtraModels();

        if (FabricLoader.getInstance().isModLoaded("flashback")) {
            new FlashbackCompat().initialize();
        }

        ClientTickEvents.END_CLIENT_TICK.register(SatchelsClient::endClientTick);
        ScreenEvents.BEFORE_INIT.register(SatchelsClient::onScreenOpen);
    }

    private static void registerKeyMappings() {
        KeyBindingHelper.registerKeyBinding(KEYMAPPING_TOGGLE_SATCHEL);
    }

    private static void registerOverlays() {
        HudRenderCallback.EVENT.register(SatchelHotbarOverlay.INSTANCE::render);
    }

    private static void endClientTick(Minecraft client) {
        while (KEYMAPPING_TOGGLE_SATCHEL.consumeClick()) {
            toggleSatchel(client);
        }
    }

    private static void registerGuiToggleKeyHandling(Minecraft client, net.minecraft.client.gui.screens.Screen screen) {
        ScreenKeyboardEvents.afterKeyPress(screen).register((scrn, key, scancode, modifiers) -> {
            if (!KEYMAPPING_TOGGLE_SATCHEL.matches(key, scancode)) return;
            if (!isAllowedContainerScreenOpen(client)) return;
            toggleInventorySatchelVisibility();
        });
    }

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

    public static void toggleInventorySatchelVisibility() {
        SatchelsClientConfig.setSatchelHiddenInInventory(!SatchelsClientConfig.isSatchelHiddenInInventory());
    }

    private static void addEntityRenderLayers() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityRenderer instanceof PlayerRenderer playerRenderer) {
                registrationHelper.register(new SatchelLayer(playerRenderer));
            }
        });
    }

    private static void registerExtraModels() {
        ModelLoadingPlugin.register(context -> {
            for (SatchelTier tier : SatchelTier.values()) {
                context.addModels(tier.getWornModelId());
            }
        });
    }

    private static void onScreenOpen(Minecraft client, net.minecraft.client.gui.screens.Screen screen, int width, int height) {
        if (!(screen instanceof AbstractContainerScreen<?> abs)) return;
        if (client.player == null) return;
        AbstractContainerMenu menu = abs.getMenu();
        SatchelsEventHooks.onMenuOpen(client.player, menu);
        registerGuiToggleKeyHandling(client, screen);
    }
}
