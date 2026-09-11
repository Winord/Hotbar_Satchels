package net.hotbar.satchels;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
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

        PayloadTypeRegistry.clientboundPlay().register(SatchelSlotUpdatePacketS2C.TYPE, SatchelSlotUpdatePacketS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SatchelStatusPacketS2C.TYPE, SatchelStatusPacketS2C.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SatchelInventorySyncPacketS2C.TYPE, SatchelInventorySyncPacketS2C.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ToggleSatchelPacketC2S.TYPE, ToggleSatchelPacketC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SatchelOffsetUpdatePacketC2S.TYPE, SatchelOffsetUpdatePacketC2S.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestSatchelResyncPacketC2S.TYPE, RequestSatchelResyncPacketC2S.STREAM_CODEC);
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
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            // addAfter each in turn so all three land together, right after the lead, in tier order.
            entries.insertAfter(Items.LEAD, new ItemStack(ModItems.SATCHEL_GOLDEN));
            entries.insertAfter(ModItems.SATCHEL_GOLDEN, new ItemStack(ModItems.SATCHEL_DIAMOND));
            entries.insertAfter(ModItems.SATCHEL_DIAMOND, new ItemStack(ModItems.SATCHEL_NETHERITE));
        });
    }

    public static void initExtra() {
        for (var satchel : ModItems.ALL_SATCHELS) {
            // 26.1: CauldronInteraction.WATER/.DYED_ITEM removed.
            // CauldronInteractions.WATER is a Dispatcher; DYED_ITEM replaced by inline lambda.
            CauldronInteractions.WATER.put(satchel, (state, level, pos, player, hand, stack) -> {
                if (!stack.has(DataComponents.DYED_COLOR)) return net.minecraft.world.InteractionResult.PASS;
                // 26.1: Level.isClientSide is now a private field with a public isClientSide()
                // method instead (confirmed via javap) — same name, now needs parens.
                if (!level.isClientSide()) {
                    stack.remove(DataComponents.DYED_COLOR);
                    player.awardStat(net.minecraft.stats.Stats.USE_CAULDRON);
                    player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(stack.getItem()));
                    net.minecraft.world.level.block.LayeredCauldronBlock.lowerFillLevel(state, level, pos);
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.FLUID_PICKUP, pos, net.minecraft.world.level.gameevent.GameEvent.Context.of(player));
                }
                return net.minecraft.world.InteractionResult.SUCCESS;
            });
        }
    }

    public static Identifier at(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }
}