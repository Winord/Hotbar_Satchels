package net.hotbar.satchels.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.hotbar.satchels.SatchelsEventHooks;
import net.hotbar.satchels.content.satchel.SatchelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "openMenu", at = @At("RETURN"))
    private void satchels$onMenuOpen(MenuProvider menuProvider, CallbackInfoReturnable<OptionalInt> cir) {
        if (cir.getReturnValue().isEmpty()) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        SatchelsEventHooks.onMenuOpen(self, self.containerMenu);
    }

    /**
     * Re-syncs satchel state to the client after a dimension change.
     *
     * 1.21.4 port note: changeDimension(TeleportTransition):Entity was removed from ServerPlayer.
     * The replacement is worldChanged(ServerLevel):void (Yarn name; Mojang mapping unknown —
     * the method is targeted by intermediary ID method_18783 with remap=false to be
     * mapping-independent). It fires once per successful cross-dimension teleport, after
     * the player is on the new level. The ServerLevel origin param is unused here, so it is
     * omitted from the handler signature (valid in Mixin when remap=false).
     */
    @Inject(method = "method_18783", remap = false, at = @At("RETURN"))
    private void satchels$onChangeDimension(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        SatchelData.get(self).resyncToClient();
    }
}