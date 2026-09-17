package net.hotbar.satchels.mixin;

import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.network.packets.ToggleSatchelPacketC2S;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pick-block (middle-click) satchel priority. As of 26.1, pick-block is fully
 * server-authoritative: {@code Minecraft#pickBlockOrEntity} just sends a packet, and all the
 * actual slot-selection logic lives in {@code ServerGamePacketListenerImpl#tryPickItem}, which
 * this mixin hooks — replacing the old client-side {@code PickBlockMixin}/{@code Minecraft#pickBlock}
 * hook from pre-26.1 versions.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "tryPickItem", at = @At("HEAD"), cancellable = true)
    private void satchels$checkSatchelFirst(ItemStack itemStack, CallbackInfo ci) {
        Inventory inventory = this.player.getInventory();
        SatchelData data = SatchelData.get(this.player);

        int invSlot = inventory.findSlotMatchingItem(itemStack);
        boolean creative = this.player.hasInfiniteMaterials();

        // Only look in the satchel when there isn't already an obvious hotbar match vanilla
        // would pick on its own, or the satchel is already being displayed.
        if (creative || (!data.isActive() && invSlot != -1 && invSlot <= 8)) return;

        int slot = data.getSatchelInventory().findSlotMatchingItem(itemStack);
        if (slot == -1) return;

        int satchelSelected = slot + data.getHotbarOffset();
        if (invSlot != -1 && invSlot < 9 && satchelSelected > invSlot) return;

        inventory.setSelectedSlot(satchelSelected);
        // Same tail vanilla's own tryPickItem runs after a successful redirect — keeps the
        // client's held-slot highlight and other players' view of the held item in sync.
        // send(Packet<?>) is declared on the ServerCommonPacketListenerImpl superclass, not on
        // ServerGamePacketListenerImpl itself, so it can't be @Shadow'd here (@Shadow only
        // resolves members declared directly on the @Mixin target class); it's public, so a
        // plain cast-and-call reaches it instead.
        ((ServerGamePacketListenerImpl) (Object) this).send(new ClientboundSetHeldSlotPacket(inventory.getSelectedSlot()));
        this.player.inventoryMenu.broadcastChanges();
        ci.cancel();

        if (!data.isActive()) {
            // Same handler the client's toggle-satchel keybind uses.
            ToggleSatchelPacketC2S.handle(new ToggleSatchelPacketC2S(true), this.player);
        }
    }

    @Inject(
            method = "tryPickItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V",
                    shift = At.Shift.AFTER
            )
    )
    private void satchels$deselectSatchelIfNeeded(ItemStack itemStack, CallbackInfo ci) {
        // Reached only when vanilla found and selected a real hotbar slot on its own. If that
        // slot falls within the satchel's virtual slot range, close the satchel display.
        SatchelData data = SatchelData.get(this.player);
        int selected = this.player.getInventory().getSelectedSlot();
        if (!data.isSlotInSatchel(selected)) return;
        if (!data.isActive()) return;

        ToggleSatchelPacketC2S.handle(new ToggleSatchelPacketC2S(false), this.player);
    }
}