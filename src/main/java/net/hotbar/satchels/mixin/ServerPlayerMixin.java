package net.hotbar.satchels.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.portal.TeleportTransition;
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
 * <p>
 * 26.1 port note: {@code DimensionTransition} moved from {@code net.minecraft.server.level}
 * to {@code net.minecraft.world.level.portal} in 26.1.
 * <p>
 * 26.1 port note: {@code ServerPlayer#changeDimension(TeleportTransition)} is gone —
 * confirmed via {@code javap} on the real merged jar. {@code Entity} declares
 * {@code teleport(TeleportTransition): Entity}, and {@code ServerPlayer} now overrides it with
 * a covariant return, {@code teleport(TeleportTransition): ServerPlayer} — the
 * Entity-returning descriptor only exists as a synthetic {@code ACC_BRIDGE} method that just
 * delegates to the real one, same situation as the {@code ShapedRecipe#getSerializer()}
 * covariant-return case documented in the fix log. Injecting into {@code teleport} (not the
 * bridge) with {@code CallbackInfoReturnable<ServerPlayer>} targets the real method body.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "openMenu", at = @At("RETURN"))
    private void satchels$onMenuOpen(MenuProvider menuProvider, CallbackInfoReturnable<OptionalInt> cir) {
        if (cir.getReturnValue().isEmpty()) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        SatchelsEventHooks.onMenuOpen(self, self.containerMenu);
    }

    @Inject(method = "teleport", at = @At("RETURN"))
    private void satchels$onChangeDimension(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
        if (cir.getReturnValue() == null) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        SatchelData.get(self).resyncToClient();
    }
}
