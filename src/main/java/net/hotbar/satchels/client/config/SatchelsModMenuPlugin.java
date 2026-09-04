package net.hotbar.satchels.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Mod Menu entrypoint: returns the Cloth Config-based settings screen factory so the user
 * can open it from the Mod Menu mods list (the gear/config icon next to Hotbar Satchels).
 * <p>
 * Registered as {@code "modmenu"} entrypoint in {@code fabric.mod.json}. Mod Menu is listed
 * in {@code suggests}, not {@code depends}, so the mod works fine when Mod Menu is absent —
 * in that case this entrypoint is never called.
 */
@Environment(EnvType.CLIENT)
public class SatchelsModMenuPlugin implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> SatchelsConfigScreen.create(parent);
    }
}
