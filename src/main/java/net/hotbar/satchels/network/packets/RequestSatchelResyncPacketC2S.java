package net.hotbar.satchels.network.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.hotbar.satchels.Satchels;
import net.hotbar.satchels.content.satchel.SatchelData;
import org.jetbrains.annotations.NotNull;

/**
 * Empty-payload request from the client asking the server to re-send everything needed to
 * rebuild an accurate satchel picture right now — the requester's own state plus a fresh
 * equipped-satchel packet for every other online player.
 * <p>
 * <b>Why this exists (Flashback compat):</b> {@code SatchelSlotUpdatePacketS2C} is a one-shot
 * delta, only ever sent when something actually changes (equip/unequip, or the
 * join/respawn/dimension-change {@code resyncToClient()}). Flashback records the raw packets
 * that cross the connection <i>after</i> recording starts — it does not retroactively capture
 * whatever already arrived earlier in the session. So if a satchel was equipped before
 * recording began, the one packet that would have shown it never lands in the recording, and
 * the render layer has nothing to draw on playback. Toggling the satchel off and back on
 * "fixes" it only because that manually re-fires the packet.
 * <p>
 * {@code FlashbackCompat} sends this packet the moment it detects a recording has just started
 * (the {@code RECORDER} null → non-null transition), so the resulting fresh packets land
 * squarely inside the recording window instead of being missed entirely. This only helps for
 * live recording — during pure replay/seek there is no server to ask, which is why those two
 * scenarios are instead handled by {@code FlashbackCompat}'s deferred retry queue.
 */
public record RequestSatchelResyncPacketC2S() implements CustomPacketPayload {
    public static final ResourceLocation ID = Satchels.at("request_satchel_resync");
    public static final CustomPacketPayload.Type<RequestSatchelResyncPacketC2S> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<ByteBuf, RequestSatchelResyncPacketC2S> STREAM_CODEC =
            StreamCodec.unit(new RequestSatchelResyncPacketC2S());

    /**
     * Resyncs the requester's own satchel state to themselves, then nudges every other online
     * player's equipped-satchel packet back out. {@link SatchelData#syncSatchelSlotToObservers()}
     * already targets the owner plus {@code PlayerLookup.tracking(...)} of that owner, so this
     * naturally reaches {@code player} too — but only for the other players {@code player} is
     * actually tracking, which is exactly the set of other satchel-wearers that could show up
     * in {@code player}'s newly-started recording.
     */
    public static void handle(RequestSatchelResyncPacketC2S packet, ServerPlayer player) {
        SatchelData.get(player).resyncToClient();

        MinecraftServer server = player.getServer();
        if (server == null) return;
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == player) continue;
            SatchelData.get(other).syncSatchelSlotToObservers();
        }
    }

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
