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
 * Replaces the old client-side {@code PickBlockMixin} hook on {@code Minecraft#pickBlock}.
 * <p>
 * 26.1: pick-block became fully server-authoritative — decompiled the real classes to confirm
 * this rather than guess at a rename. {@code Minecraft#pickBlock} was renamed to
 * {@code pickBlockOrEntity()}, but more importantly its body no longer contains any inventory
 * logic at all: it just resolves the hit result and calls
 * {@code MultiPlayerGameMode#handlePickItemFromBlock}/{@code handlePickItemFromEntity}, both of
 * which now just send a packet to the server
 * ({@code ServerboundPickItemFromBlockPacket}/{@code ServerboundPickItemFromEntityPacket}) with
 * no client-side inventory mutation whatsoever.
 * <p>
 * The logic our old client mixin depended on — {@code Inventory#findSlotMatchingItem} and
 * writing the selected hotbar slot — now lives entirely in
 * {@code ServerGamePacketListenerImpl#tryPickItem(ItemStack)}, which both packet handlers above
 * funnel into server-side. That's a deliberate architecture change (this kind of client-decided,
 * server-trusted action is exactly what get moved server-side over time), not something with a
 * client-side equivalent to hook instead.
 * <p>
 * {@code inventory.selected} also isn't written directly anymore — {@code Inventory} now has
 * proper {@code setSelectedSlot(int)}/{@code getSelectedSlot()} methods, and the server tells the
 * client about the change via a {@code ClientboundSetHeldSlotPacket} it sends itself at the end
 * of {@code tryPickItem}. When we cancel and redirect to a satchel slot instead, we replicate
 * that same tail (packet + {@code inventoryMenu.broadcastChanges()}) so the client stays in sync
 * exactly the way vanilla's own path does.
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

        // Mirrors the old client-side condition exactly: only look in the satchel when there
        // isn't already an obvious hotbar match vanilla would pick on its own, or the satchel is
        // already the thing being displayed.
        if (creative || (!data.isActive() && invSlot != -1 && invSlot <= 8)) return;

        int slot = data.getSatchelInventory().findSlotMatchingItem(itemStack);
        if (slot == -1) return;

        int satchelSelected = slot + data.getHotbarOffset();
        if (invSlot != -1 && invSlot < 9 && satchelSelected > invSlot) return;

        inventory.setSelectedSlot(satchelSelected);
        // Same tail vanilla's own tryPickItem runs after a successful redirect — keeps the
        // client's held-slot highlight and other players' view of the held item in sync.
        //
        // 26.1: send(Packet<?>) can't be @Shadow'd here — confirmed by the actual runtime error
        // ("was not located in the target class net.minecraft.server.network.
        // ServerGamePacketListenerImpl"). It's declared on the superclass,
        // ServerCommonPacketListenerImpl, not on ServerGamePacketListenerImpl itself, and
        // @Shadow only resolves members declared directly on the @Mixin target class, not ones
        // merely inherited (same rule LivingEntityMixin's javadoc already notes for
        // setItemSlot/Player, just biting here in the other direction — a method that used to be
        // directly on the target and still IS reachable, just one class higher up). It's public,
        // so a plain cast-and-call reaches it without needing @Shadow at all.
        ((ServerGamePacketListenerImpl) (Object) this).send(new ClientboundSetHeldSlotPacket(inventory.getSelectedSlot()));
        this.player.inventoryMenu.broadcastChanges();
        ci.cancel();

        if (!data.isActive()) {
            // Same handler the client's toggle-satchel keybind uses — we're already running
            // server-side here, so no need to round-trip a packet to ourselves the way the old
            // client-side mixin had to.
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
        // Only reached when satchels$checkSatchelFirst didn't already cancel the method — i.e.
        // vanilla found and selected a real hotbar slot on its own. If that slot happens to fall
        // within the satchel's virtual slot range, close the satchel display.
        SatchelData data = SatchelData.get(this.player);
        int selected = this.player.getInventory().getSelectedSlot();
        if (!data.isSlotInSatchel(selected)) return;
        if (!data.isActive()) return;

        // Delegates to ToggleSatchelPacketC2S.handle rather than calling setActive/sendData
        // directly — that handler now always confirms the active flag back to the client (see
        // its own bugfix note), which is exactly what's needed here too.
        ToggleSatchelPacketC2S.handle(new ToggleSatchelPacketC2S(false), this.player);
    }
}