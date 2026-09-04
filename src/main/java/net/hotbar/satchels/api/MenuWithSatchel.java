package net.hotbar.satchels.api;

import net.minecraft.world.inventory.Slot;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelInventorySlot;
import net.hotbar.satchels.content.satchel.SatchelTier;

import java.util.function.Consumer;
public class MenuWithSatchel {
    /**
     * Always reserves {@link SatchelTier#MAX_SLOT_COUNT} slots, not just the current
     * {@code getContainerSize()} — see that constant's javadoc for why a live tier change
     * needs these slots to already exist rather than being added on demand.
     */
    public static void addInventorySlots(SatchelData satchelData, Consumer<Slot> consumer, int x, int y, int padding) {
        for (int i = 0; i < SatchelTier.MAX_SLOT_COUNT; i++) {
            int xPos = x + i * padding;
            SatchelInventorySlot slot = new SatchelInventorySlot(satchelData.getSatchelInventory(), i, xPos, y);
            slot.updateX();
            consumer.accept(slot);
        }
    }
}
