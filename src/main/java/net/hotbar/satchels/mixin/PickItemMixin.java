package net.hotbar.satchels.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.hotbar.satchels.content.satchel.SatchelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-side replacement for the old client-side {@code PickBlockMixin} (which targeted
 * {@code Minecraft#pickBlock()}).
 *
 * <h2>1.21.4 architecture change</h2>
 * In 1.21.1 the middle-click pick-block flow was entirely client-side:
 * {@code Minecraft#pickBlock()} searched the player inventory, selected the matching hotbar
 * slot, and sent a {@code ToggleSatchelPacketC2S} to inform the server about the satchel
 * activation. In 1.21.4 the entire flow moved server-side:
 * <ol>
 *   <li>The client sends {@code PickItemFromBlockC2SPacket} (or {@code …FromEntity…}).</li>
 *   <li>{@code ServerGamePacketListenerImpl#onPickItemFromBlock/Entity} resolves the pick
 *       stack and calls {@code handlePickItem(ItemStack)}.</li>
 *   <li>{@code handlePickItem} finds the matching hotbar slot in {@code player.getInventory()}
 *       and sets {@code Inventory#selected}, which the server then syncs to the client via
 *       the usual container-change packet.</li>
 * </ol>
 *
 * <p>This mixin intercepts {@code handlePickItem} before vanilla runs. If the picked item
 * exists in the satchel (and the satchel is accessible), it:
 * <ul>
 *   <li>Sets {@code Inventory#selected} to the corresponding virtual hotbar slot.</li>
 *   <li>Activates the satchel via {@link SatchelData#setActive} and immediately pushes
 *       the new state to the client via {@link SatchelData#sendData()} and
 *       {@link SatchelData#sendInventoryToClient()}.</li>
 *   <li>Cancels vanilla processing so the slot assignment is not overwritten.</li>
 * </ul>
 * If vanilla would select a slot that falls inside the satchel range (shouldn't happen
 * normally, but defensive check), it deactivates the satchel instead.
 *
 * <h2>Mapping note — "handlePickItem"</h2>
 * The Mojang-mapped name for the private {@code (ItemStack)V} method on
 * {@code ServerGamePacketListenerImpl} is {@code handlePickItem}. Its intermediary ID is
 * {@code method_65098}. If a future Loom upgrade fails to remap this name, use
 * {@code method_65098} with {@code remap = false} as a fallback.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PickItemMixin {

    @Shadow
    private ServerPlayer player;

    /**
     * Intercept pick-item slot assignment before vanilla runs.
     *
     * <p>The cancellable inject at HEAD means: if we handle the stack ourselves,
     * vanilla's inventory search is skipped entirely (no double slot-set).
     * If the item is not in the satchel we return without cancelling and vanilla
     * proceeds normally.
     */
    @Inject(method = "tryPickItem", at = @At("HEAD"), cancellable = true)
    private void satchels$interceptPickItem(ItemStack stack, CallbackInfo ci) {
        SatchelData data = SatchelData.get(this.player);
        if (!data.canAccess()) return;

        Inventory inventory = this.player.getInventory();

        // --- activate path: item is in the satchel ---
        int satchelSlot = data.getSatchelInventory().findSlotMatchingItem(stack);
        if (satchelSlot != -1) {
            int hotbarSlot = satchelSlot + data.getHotbarOffset();

            // Prefer a real hotbar slot if the item is already there and closer to
            // the current selection — mirrors the client-side tie-break from 1.21.1.
            int invSlot = inventory.findSlotMatchingItem(stack);
            if (invSlot != -1 && invSlot < 9 && hotbarSlot > invSlot) return;

            inventory.selected = hotbarSlot;
            ci.cancel();

            if (data.isActive()) return;          // already active, just reposition
            data.setActive(true, true);
            data.sendData();                      // push SatchelStatusPacketS2C → client
            data.sendInventoryToClient();         // push inventory snapshot → overlay
            return;
        }

        // --- deactivate path: vanilla will select a non-satchel slot ---
        // We let vanilla run (no cancel), but if the satchel is currently active we
        // need to turn it off.  We can't easily read which slot vanilla will choose
        // before it runs, so we attach a second RETURN inject for that.
    }

    /**
     * After vanilla has finished assigning {@code Inventory#selected}, check whether
     * the new slot is outside the satchel range. If the satchel was active, deactivate it.
     *
     * <p>This only does meaningful work when {@code satchels$interceptPickItem} did NOT
     * cancel — i.e. vanilla ran and chose its own slot.
     */
    @Inject(method = "tryPickItem", at = @At("RETURN"))
    private void satchels$deselectSatchelIfNeeded(ItemStack stack, CallbackInfo ci) {
        SatchelData data = SatchelData.get(this.player);
        if (!data.isActive()) return;

        Inventory inventory = this.player.getInventory();
        if (data.isSlotInSatchel(inventory.selected)) return; // still in satchel range, fine

        data.setActive(false, true);
        data.sendData();  // push deactivated state to client
    }
}
