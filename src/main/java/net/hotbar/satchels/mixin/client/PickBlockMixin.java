package net.hotbar.satchels.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.hotbar.satchels.network.packets.ToggleSatchelPacketC2S;
import net.hotbar.satchels.content.satchel.SatchelData;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code Minecraft.class} only exists client-side, so this mixin must be registered in the
 * client mixin config ({@code satchels.client.mixins.json}) — applying it on a dedicated
 * server (where {@code Minecraft} doesn't exist) would crash. No client-side guards are
 * needed in the injected methods since the whole class is already client-only.
 */
@Mixin(Minecraft.class)
public class PickBlockMixin {
    @Inject(method = "pickBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;findSlotMatchingItem(Lnet/minecraft/world/item/ItemStack;)I"), cancellable = true)
    public void satchels$checkSatchelAfter(CallbackInfo ci, @Local ItemStack stack, @Local Inventory inventory, @Local boolean creative) {
        SatchelData data = SatchelData.get(inventory.player);
        int invSlot = inventory.findSlotMatchingItem(stack);
        if (!creative && (data.isActive() || invSlot == -1 || invSlot > 8)) {
            int slot = data.getSatchelInventory().findSlotMatchingItem(stack);

            if (slot == -1) return;
            if (invSlot != -1 && invSlot < 9 && slot + data.getHotbarOffset() > invSlot) return;
            inventory.selected = slot + data.getHotbarOffset();

            ci.cancel();

            if (data.isActive()) return;
            data.setActive(true, true);
            ClientPlayNetworking.send(new ToggleSatchelPacketC2S(true));
        }
    }

    @Inject(method = "pickBlock", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Inventory;selected:I", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    public void satchels$deselectSatchelIfNeeded(CallbackInfo ci, @Local Inventory inventory) {
        SatchelData data = SatchelData.get(inventory.player);
        if (!data.isSlotInSatchel(inventory.selected)) return;

        if (!data.isActive()) return;
        data.setActive(false, true);
        ClientPlayNetworking.send(new ToggleSatchelPacketC2S(false));
    }
}
