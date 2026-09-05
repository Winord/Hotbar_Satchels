package net.hotbar.satchels;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.hotbar.satchels.compat.SatchelsCompat;
import net.hotbar.satchels.content.loot.SatchelsLootTables;
import net.hotbar.satchels.network.packets.SatchelOffsetUpdatePacketC2S;
import net.hotbar.satchels.network.packets.SatchelInventorySyncPacketS2C;
import net.hotbar.satchels.network.packets.SatchelSlotUpdatePacketS2C;
import net.hotbar.satchels.network.packets.SatchelStatusPacketS2C;
import net.hotbar.satchels.network.packets.RequestSatchelResyncPacketC2S;
import net.hotbar.satchels.network.packets.ToggleSatchelPacketC2S;

/**
 * Mod entry point (Fabric {@link ModInitializer}).
 * <p>
 * {@code SatchelsCompat.initialize()} must run here, in {@link #onInitialize()} itself —
 * it's what makes {@code VanillaCompat} populate {@code SatchelAccess.CAN_ACCESS_PREDICATES},
 * without which the satchel has no valid menus to attach to and is non-functional.
 */
public class Satchels implements ModInitializer {
    public static final String ID = "satchels";

    @Override
    public void onInitialize() {
        ModItems.register();
        ModSounds.register();
        ModRecipeSerializers.register();

        PayloadTypeRegistry.playS2C().register(SatchelSlotUpdatePacketS2C.TYPE, SatchelSlotUpdatePacketS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SatchelStatusPacketS2C.TYPE, SatchelStatusPacketS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SatchelInventorySyncPacketS2C.TYPE, SatchelInventorySyncPacketS2C.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ToggleSatchelPacketC2S.TYPE, ToggleSatchelPacketC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SatchelOffsetUpdatePacketC2S.TYPE, SatchelOffsetUpdatePacketC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestSatchelResyncPacketC2S.TYPE, RequestSatchelResyncPacketC2S.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ToggleSatchelPacketC2S.TYPE, (packet, context) ->
                context.player().server.execute(() -> ToggleSatchelPacketC2S.handle(packet, context.player()))
        );
        ServerPlayNetworking.registerGlobalReceiver(SatchelOffsetUpdatePacketC2S.TYPE, (packet, context) ->
                context.player().server.execute(() -> SatchelOffsetUpdatePacketC2S.handle(packet, context.player()))
        );
        ServerPlayNetworking.registerGlobalReceiver(RequestSatchelResyncPacketC2S.TYPE, (packet, context) ->
                context.player().server.execute(() -> RequestSatchelResyncPacketC2S.handle(packet, context.player()))
        );

        SatchelsCommonConfig.load();
        SatchelsCompat.initialize();
        SatchelsEventHooks.register();
        SatchelsLootTables.register();

        registerCreativeTabEntries();

        initExtra();
    }

    /**
     * Adds the satchel to the "Tools and Utilities" creative tab, right after the lead.
     * Fabric's {@code addAfter} places the item in the tab and it's automatically included
     * in search results too, so no separate handling is needed for tab vs. search visibility.
     */
    private static void registerCreativeTabEntries() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            // addAfter each in turn so all three land together, right after the lead, in tier order.
            entries.addAfter(Items.LEAD, new ItemStack(ModItems.SATCHEL_GOLDEN));
            entries.addAfter(ModItems.SATCHEL_GOLDEN, new ItemStack(ModItems.SATCHEL_DIAMOND));
            entries.addAfter(ModItems.SATCHEL_DIAMOND, new ItemStack(ModItems.SATCHEL_NETHERITE));
        });
    }

    public static void initExtra() {
        for (var satchel : ModItems.ALL_SATCHELS) {
            CauldronInteraction.WATER.map().put(satchel, CauldronInteraction.DYED_ITEM);
        }
    }

    public static ResourceLocation at(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }
}