package net.hotbar.satchels.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Resolves the {@code allowed_menus} {@link ResourceLocation} key for a given open menu.
 * <p>
 * Factored out of {@code AbstractContainerScreenMixin.satchels$getMenuLocation} (11.1) so both
 * that mixin's rendering/click gates and {@code SatchelsClient}'s context-dependent {@code V}
 * key resolve the exact same key from the exact same menu, instead of two independently
 * maintained copies of this switch drifting apart over time.
 */
@Environment(EnvType.CLIENT)
public final class SatchelMenuLocation {
    private SatchelMenuLocation() {
    }

    public static ResourceLocation resolve(AbstractContainerMenu menu) {
        return switch (menu) {
            case InventoryMenu ignored -> ResourceLocation.withDefaultNamespace("inventory");
            case CreativeModeInventoryScreen.ItemPickerMenu ignored -> ResourceLocation.withDefaultNamespace("creative_menu");
            case HorseInventoryMenu ignored -> ResourceLocation.withDefaultNamespace("horse");
            case null -> null;
            default -> {
                try {
                    yield BuiltInRegistries.MENU.getKey(menu.getType());
                } catch (Exception ignored) {
                    yield null;
                }
            }
        };
    }
}
