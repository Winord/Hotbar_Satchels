package net.hotbar.satchels.mixin.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.hotbar.satchels.api.ScreenWithSatchel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 26.1: `renderBg` doesn't exist anymore anywhere (confirmed — see AbstractContainerScreenMixin
// for the full pipeline trace). For horse-style screens specifically, the panel-texture drawing
// that used to be HorseInventoryScreen's own `renderBg` override now lives on the shared
// `AbstractMountInventoryScreen.extractBackground(...)` — HorseInventoryScreen itself doesn't
// override it at all anymore (confirmed via javap -p: no extractBackground in
// HorseInventoryScreen's own class file, only inherited). Retargeted the whole mixin from
// HorseInventoryScreen to AbstractMountInventoryScreen so Mixin can actually find the method in
// the target class's own bytecode. Side effect worth flagging: 26.1 added a second
// AbstractMountInventoryScreen subclass, NautilusInventoryScreen (confirmed via javap across the
// jar) — this mixin now covers it too, for free, since it shares the same background-rendering
// method. Not previously possible on 1.21.1's per-subclass renderBg model.
@Mixin(AbstractMountInventoryScreen.class)
public abstract class HorseInventoryScreenMixin<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    public HorseInventoryScreenMixin(T abstractContainerMenu, Inventory inventory, Component component) { super(abstractContainerMenu, inventory, component); }

    @Unique
    private final ScreenWithSatchel satchels$screenWithSatchel = new ScreenWithSatchel();

    // extractBackground's param order is (guiGraphics, mouseX, mouseY, partialTick) — confirmed
    // via javap -c, different order from the old renderBg(guiGraphics, partialTick, mouseX, mouseY).
    @Inject(method = "extractBackground", at = @At("HEAD"))
    public void satchels$renderSatchelInventory(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        satchels$screenWithSatchel.renderSatchelInventory(guiGraphics, this.leftPos, this.topPos, this.imageHeight);
    }
}
