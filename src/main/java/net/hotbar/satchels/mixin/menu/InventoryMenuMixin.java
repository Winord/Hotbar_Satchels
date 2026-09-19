package net.hotbar.satchels.mixin.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.hotbar.satchels.api.MenuWithSatchel;
import net.hotbar.satchels.compat.SatchelsCompat;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelEquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/**
 * priority = 500 (lower than Mixin's default 1000): forces this mixin's TAIL inject on
 * InventoryMenu's constructor to be woven in BEFORE other mods' TAIL injects on the same
 * constructor (notably Trinkets Updated, which also appends its own slots at TAIL and whose
 * own slot count can vary at runtime). Without this, the relative application order between
 * us and Trinkets is undefined (both default to 1000), so our satchel slots' absolute index
 * in InventoryMenu#slots depends on how many slots Trinkets happened to insert first — and if
 * that count ever differs between client and server, every satchel slot's functional index
 * silently shifts by that same delta relative to what the client rendered, misdirecting
 * clicks to a neighboring slot. Going first pins our slots to a fixed offset right after
 * vanilla's own, independent of whatever Trinkets (or any other mod) does afterward.
 * Confirmed fixing this exact bug on the 26.2 branch; ported here as the same class of issue.
 */
@Mixin(value = InventoryMenu.class, priority = 500)
public abstract class InventoryMenuMixin extends RecipeBookMenu {
    public InventoryMenuMixin(MenuType<?> p_40115_, int p_40116_) {
        super(p_40115_, p_40116_);
    }

    @SuppressWarnings("Convert2MethodRef")
    @Inject(method = "<init>", at = @At("TAIL"))
    public void satchels$addMoreSlots(Inventory inventory, boolean bl, Player player, CallbackInfo ci) {
        SatchelData satchelData = SatchelData.get(player);

        if (SatchelsCompat.VANILLA.isLoaded()) this.addSlot(
                new SatchelEquipmentSlot(player, 170 + 10, 142)
        );
        MenuWithSatchel.addInventorySlots(satchelData, s -> this.addSlot(s), 8, 170, 18);
    }
}
