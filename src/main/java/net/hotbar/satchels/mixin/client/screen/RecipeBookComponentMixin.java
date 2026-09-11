package net.hotbar.satchels.mixin.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.entity.player.StackedItemContents;
import net.hotbar.satchels.content.satchel.SatchelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(RecipeBookComponent.class)
public class RecipeBookComponentMixin {
    @Shadow
    protected Minecraft minecraft;

    // 26.1: StackedContents was split into StackedItemContents (item-availability tracking,
    // used here) — confirmed via javap that both the shadowed field's real type on
    // RecipeBookComponent and RecipeBookMenu#fillCraftSlotsStackedContents's parameter type
    // are StackedItemContents now.
    @Shadow
    @Final
    private StackedItemContents stackedContents;

    @Inject(method = "initVisuals", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/RecipeBookMenu;fillCraftSlotsStackedContents(Lnet/minecraft/world/entity/player/StackedItemContents;)V", shift = At.Shift.AFTER))
    private void satchels$fillInitContentsWithSatchel(CallbackInfo ci) {
        SatchelData satchelData = SatchelData.get(minecraft.player);
        if (satchelData.canAccess()) satchelData.getSatchelInventory().fillStackedContents(stackedContents);
    }

    @Inject(method = "updateStackedContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/RecipeBookMenu;fillCraftSlotsStackedContents(Lnet/minecraft/world/entity/player/StackedItemContents;)V", shift = At.Shift.AFTER))
    private void satchels$fillContentsWithSatchel(CallbackInfo ci) {
        SatchelData satchelData = SatchelData.get(minecraft.player);
        if (satchelData.canAccess()) satchelData.getSatchelInventory().fillStackedContents(stackedContents);
    }
}
