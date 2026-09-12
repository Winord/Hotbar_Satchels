package net.hotbar.satchels.network.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.hotbar.satchels.Satchels;
import net.hotbar.satchels.content.satchel.SatchelData;
import org.jetbrains.annotations.NotNull;
public record ToggleSatchelPacketC2S(boolean enabled) implements CustomPacketPayload {
    public static final Identifier ID = Satchels.at("toggle_satchel");
    public static final CustomPacketPayload.Type<ToggleSatchelPacketC2S> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<ByteBuf, ToggleSatchelPacketC2S> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, ToggleSatchelPacketC2S::enabled, ToggleSatchelPacketC2S::new);

    public static void handle(ToggleSatchelPacketC2S packet, ServerPlayer player) {
        SatchelData satchelData = SatchelData.get(player);
        if (!satchelData.canAccess()) {
            satchelData.setActive(false, true);
        } else {
            satchelData.setActive(packet.enabled, true);
            // When toggling ON, push a full inventory snapshot to the client so the
            // hotbar overlay can render item icons without a container menu being open.
            // (Vanilla's InventoryMenu broadcastChanges sync only runs while a menu is
            // open; without this, the client-side SatchelInventory stays empty/stale.)
            if (packet.enabled) {
                satchelData.sendInventoryToClient();
            }
        }

        // Bugfix: this handler was written assuming it's only ever called in response to a real
        // C2S packet from a client that already predicted the state change locally (the keybind
        // toggle in SatchelsClient does exactly that before sending the packet), so it never
        // bothered confirming the `active` flag back. But it's also called synthetically,
        // server-side-only, from contexts where the client never predicted anything — e.g.
        // ServerGamePacketListenerImplMixin#satchels$checkSatchelFirst auto-opens the satchel
        // when pick-block resolves to an item that only exists in a currently-hidden satchel.
        // There, the held-slot packet arrived and the selection changed, but the satchel stayed
        // visually hidden because the client's `active` flag was never told it flipped. Always
        // syncing here fixes that path and is a no-op for the normal predicted-toggle path
        // (the client just gets a redundant confirmation of what it already set, silently —
        // SatchelStatusPacketS2C's handler passes audible=false, so no double sound).
        satchelData.sendData();
    }

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
