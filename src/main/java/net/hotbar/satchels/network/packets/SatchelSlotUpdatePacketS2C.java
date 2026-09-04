package net.hotbar.satchels.network.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.hotbar.satchels.Satchels;
import net.hotbar.satchels.content.satchel.IHaveSatchelData;
import org.jetbrains.annotations.NotNull;

/**
 * Syncs the equipped-satchel item stack to observing clients (needed to render
 * {@code SatchelLayer} on other players, not just the local player).
 * <p>
 * {@code entityId} identifies the owning entity (not necessarily the local player), so the
 * client can apply the update to the right {@code SatchelData} even when observing someone
 * else.
 */
public record SatchelSlotUpdatePacketS2C(int entityId, ItemStack stack) implements CustomPacketPayload {
    public static final ResourceLocation ID = Satchels.at("satchel_slot_update");
    public static final CustomPacketPayload.Type<SatchelSlotUpdatePacketS2C> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SatchelSlotUpdatePacketS2C> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SatchelSlotUpdatePacketS2C::entityId,
            ItemStack.OPTIONAL_STREAM_CODEC, SatchelSlotUpdatePacketS2C::stack,
            SatchelSlotUpdatePacketS2C::new
    );

    /** Client-side handler — the level comes from {@code Minecraft.getInstance().level}. */
    public static void handle(SatchelSlotUpdatePacketS2C packet, Level clientLevel) {
        Entity entity = clientLevel.getEntity(packet.entityId());
        if (entity instanceof Player player && player instanceof IHaveSatchelData data) {
            data.satchels$getSatchelData().setSatchelSlotStack(packet.stack());
        }
    }

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
