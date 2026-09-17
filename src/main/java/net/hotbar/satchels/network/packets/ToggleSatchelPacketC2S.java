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
            // When toggling ON, push a full inventory snapshot so the hotbar overlay can render
            // item icons without a container menu open (vanilla's own sync only runs while a
            // menu is open).
            if (packet.enabled) {
                satchelData.sendInventoryToClient();
            }
        }

        // Always confirms the active flag back to the client — not just a no-op ack of a
        // client-predicted toggle. This is also called synthetically, server-side-only (e.g.
        // pick-block auto-opening a hidden satchel), where the client never predicted the
        // change and would otherwise stay visually out of sync.
        satchelData.sendData();
    }

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
