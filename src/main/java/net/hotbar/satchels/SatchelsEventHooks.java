package net.hotbar.satchels;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.hotbar.satchels.api.SatchelAccess;
import net.hotbar.satchels.api.MenuWithSatchel;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelInventorySlot;

/**
 * Player lifecycle hooks: join, clone-on-respawn, and menu-open handling.
 * <p>
 * {@link #onMenuOpen} is invoked from two separate places, both server and client:
 * <ol>
 *   <li>Server: {@code mixin.ServerPlayerMixin}, injected into
 *       {@code ServerPlayer#openMenu(MenuProvider)}.</li>
 *   <li>Client: {@code SatchelsClient}, via {@code ScreenEvents.BEFORE_INIT} — needed
 *       separately because the client has its own mirrored menu object for rendering/clicks.</li>
 * </ol>
 */
public class SatchelsEventHooks {
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> playerJoin(handler.player));
        ServerPlayerEvents.COPY_FROM.register(SatchelsEventHooks::playerClone);
        ServerPlayerEvents.AFTER_RESPAWN.register(SatchelsEventHooks::playerAfterRespawn);
    }

    private static void playerJoin(ServerPlayer player) {
        SatchelData.get(player).resyncToClient();
    }

    private static void playerClone(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        if (oldPlayer.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            ItemStack previous = SatchelData.get(oldPlayer).getSatchelSlotStack();
            SatchelData.get(newPlayer).setSatchelSlotStack(previous.copy());
            SatchelData.get(newPlayer).setActive(SatchelData.get(oldPlayer).isActive(), false);
        }

        SatchelData original = SatchelData.get(oldPlayer);
        SatchelData.get(newPlayer).copyFrom(original);
    }

    private static void playerAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        SatchelData.get(newPlayer).resyncToClient();
        SatchelAccess.notifyPlayerRespawned(oldPlayer, newPlayer);
    }

    /** See class doc for why this is invoked from both the server and the client. */
    public static void onMenuOpen(Player player, AbstractContainerMenu menu) {
        // EMI fix; EMI re-opens the menu but doesn't clear the slots when quick-crafting
        if (menu.slots.stream().anyMatch(s -> s instanceof SatchelInventorySlot)) return;

        SatchelData satchelData = SatchelData.get(player);

        ResourceLocation menuLocation;
        try {
            menuLocation = BuiltInRegistries.MENU.getKey(menu.getType());
        } catch (Exception ignored) {
            return;
        }

        if (SatchelsCommonConfig.shouldLog()) LogUtils.getLogger().info("satchels: opened {}", menuLocation);
        if (!SatchelsCommonConfig.isAllowed(menuLocation)) return;

        Tuple<Integer, Integer> offset = SatchelsCommonConfig.getOffset(menuLocation);
        MenuWithSatchel.addInventorySlots(satchelData, menu::addSlot, 8 + offset.getA(), 170 + offset.getB(), 18);
    }
}
