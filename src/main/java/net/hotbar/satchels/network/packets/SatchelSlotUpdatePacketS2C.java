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
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
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
 * <p>
 * <b>Flashback compat:</b> when Flashback is active (recording or replaying), the target
 * entity may not yet be in {@code clientLevel} at packet-delivery time — either because the
 * replay is still loading entities, or because the viewer sought to a position where the
 * snapshot was processed before the entity was spawned. In that case we park the update in
 * {@link FlashbackCompat}'s deferred queue and retry every client tick until the entity
 * appears (or the entry times out). See {@link FlashbackCompat} for the full explanation.
 */
public record SatchelSlotUpdatePacketS2C(int entityId, ItemStack stack) implements CustomPacketPayload {
    public static final ResourceLocation ID = Satchels.at("satchel_slot_update");
    public static final CustomPacketPayload.Type<SatchelSlotUpdatePacketS2C> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SatchelSlotUpdatePacketS2C> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SatchelSlotUpdatePacketS2C::entityId,
            ItemStack.OPTIONAL_STREAM_CODEC, SatchelSlotUpdatePacketS2C::stack,
            SatchelSlotUpdatePacketS2C::new
    );

    /**
     * Client-side handler — the level comes from {@code Minecraft.getInstance().level}.
     * <p>
     * If the entity is not yet present and Flashback is active, defers the update via
     * {@link FlashbackCompat#enqueueSlotUpdate} rather than silently dropping it.
     */
    public static void handle(SatchelSlotUpdatePacketS2C packet, Level clientLevel) {
        Entity entity = clientLevel.getEntity(packet.entityId());
        if (entity instanceof Player player && player instanceof IHaveSatchelData data) {
            data.satchels$getSatchelData().setSatchelSlotStack(packet.stack());
        } else if (entity == null
                && FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
                && FabricLoader.getInstance().isModLoaded("flashback")) {
            // Flashback is active and the entity isn't in the client world yet (replay load
            // or seek). Delegate to FlashbackCompat via a string class reference so this
            // shared-side class never directly loads the @Environment(CLIENT) compat class
            // on the dedicated server.
            try {
                Class<?> cls = Class.forName("net.hotbar.satchels.compat.flashback.FlashbackCompat");
                boolean active = (boolean) cls.getMethod("isFlashbackActive").invoke(null);
                if (active) {
                    cls.getMethod("enqueueSlotUpdate", int.class, net.minecraft.world.item.ItemStack.class)
                       .invoke(null, packet.entityId(), packet.stack());
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
