package net.hotbar.satchels.network.packets;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.hotbar.satchels.Satchels;
import net.hotbar.satchels.content.satchel.SatchelData;
import org.jetbrains.annotations.NotNull;

/**
 * Syncs the full contents of {@link net.hotbar.satchels.content.satchel.SatchelInventory}
 * to the owning client.
 * <p>
 * The satchel inventory's contents are normally kept in sync only while
 * {@code InventoryMenu} (or another allowed container menu) is open, via vanilla's
 * {@code AbstractContainerMenu#broadcastChanges()} slot-tracking. When no menu is
 * open — exactly the case when the hotbar overlay is visible — the client-side
 * {@code SatchelInventory} holds stale or empty stacks, so the overlay renders
 * blank slots even though the items exist on the server.
 * <p>
 * This packet is sent by {@link SatchelData#sendInventoryToClient()} whenever the
 * client needs an authoritative snapshot: on join/respawn (via
 * {@link SatchelData#resyncToClient()}) and when the satchel is toggled active
 * (via {@link ToggleSatchelPacketC2S#handle}).
 */
public record SatchelInventorySyncPacketS2C(java.util.List<net.minecraft.world.item.ItemStack> items)
        implements CustomPacketPayload {

    public static final ResourceLocation ID = Satchels.at("satchel_inventory_sync");
    public static final CustomPacketPayload.Type<SatchelInventorySyncPacketS2C> TYPE =
            new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SatchelInventorySyncPacketS2C> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.world.item.ItemStack.OPTIONAL_LIST_STREAM_CODEC,
                    SatchelInventorySyncPacketS2C::items,
                    SatchelInventorySyncPacketS2C::new
            );

    /** Client-side handler — applies the snapshot to the local player's SatchelInventory. */
    public static void handle(SatchelInventorySyncPacketS2C packet, Player player) {
        SatchelData.get(player).getSatchelInventory().deserializeFromByteBufList(packet.items());
    }

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
