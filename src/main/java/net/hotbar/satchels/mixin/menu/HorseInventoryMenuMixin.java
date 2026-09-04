package net.hotbar.satchels.mixin.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.hotbar.satchels.api.MenuWithSatchel;
import net.hotbar.satchels.content.satchel.SatchelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HorseInventoryMenu.class)
public abstract class HorseInventoryMenuMixin extends AbstractContainerMenu {
    public HorseInventoryMenuMixin(MenuType<?> p_40115_, int p_40116_) {
        super(p_40115_, p_40116_);
    }

    @SuppressWarnings("Convert2MethodRef")
    @Inject(method = "<init>", at = @At("TAIL"))
    public void satchels$addMoreSlots(int p_39656_, Inventory inventory, Container p_39658_, AbstractHorse p_39659_, int p_352384_, CallbackInfo ci) {
        SatchelData satchelData = SatchelData.get(inventory.player);

        MenuWithSatchel.addInventorySlots(satchelData, s -> this.addSlot(s), 8, 170, 18);
    }
}
