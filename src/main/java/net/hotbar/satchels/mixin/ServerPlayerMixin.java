package net.hotbar.satchels.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.hotbar.satchels.SatchelsEventHooks;
import net.hotbar.satchels.content.satchel.SatchelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

/**
 * Server-side "container opened" hook, injected into {@code ServerPlayer#openMenu(MenuProvider)}.
 * <p>
 * The inject is on {@code RETURN}: if the returned {@code OptionalInt} is non-empty,
 * {@link ServerPlayer#containerMenu} is guaranteed to already point at the newly opened menu
 * (it never points at {@code inventoryMenu}, since {@code openMenu} isn't called for the
 * player's own inventory screen).
 * <p>
 * {@code InventoryMenu} and {@code HorseInventoryMenu} are not handled here — they get their
 * satchel slots directly in their constructors ({@code InventoryMenuMixin}/
 * {@code HorseInventoryMenuMixin}). The duplicate-slot guard in
 * {@link SatchelsEventHooks#onMenuOpen} (checking for an existing {@code SatchelInventorySlot})
 * covers both paths.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "openMenu", at = @At("RETURN"))
    private void satchels$onMenuOpen(MenuProvider menuProvider, CallbackInfoReturnable<OptionalInt> cir) {
        if (cir.getReturnValue().isEmpty()) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        SatchelsEventHooks.onMenuOpen(self, self.containerMenu);
    }

    /**
     * Re-syncs satchel state to the client after a dimension change (Nether/End portal,
     * End gateway, {@code /execute in}, ...).
     * <p>
     * {@link ServerPlayer#changeDimension(DimensionTransition)} does not recreate the
     * {@code ServerPlayer}, so server-side {@link SatchelData#isActive()} stays correct on its
     * own. The client is a different story: vanilla's {@code ClientboundRespawnPacket} is used
     * both for death respawns and dimension changes, and in both cases
     * {@code ClientPacketListener#handleRespawn} recreates {@code LocalPlayer} — a fresh
     * {@code Player} instance, and with it a fresh client-side {@code SatchelData}
     * ({@code @Unique} field in {@code PlayerMixin}, defaulting to {@code active = false})
     * until the server explicitly sends the real state again.
     * <p>
     * {@link SatchelsEventHooks#playerJoin} only sent {@code SatchelStatusPacketS2C} on
     * {@code ServerPlayConnectionEvents.JOIN} (login) until the {@code resyncToClient} fix,
     * and that event doesn't fire on a portal-triggered dimension change either way, so that
     * resync path alone isn't enough here. This inject calls
     * {@link SatchelData#resyncToClient()} right after {@code changeDimension} completes; since
     * that method is the single entry point for any dimension change, one inject covers all
     * cases.
     * <p>
     * The {@code cir.getReturnValue() == null} guard handles a cancelled transition (e.g. by
     * another mod), where no new entity is actually returned.
     */
    @Inject(method = "changeDimension", at = @At("RETURN"))
    private void satchels$onChangeDimension(DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() == null) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        SatchelData.get(self).resyncToClient();
    }
}
