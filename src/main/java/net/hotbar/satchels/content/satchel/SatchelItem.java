package net.hotbar.satchels.content.satchel;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.hotbar.satchels.ModSounds;
import net.hotbar.satchels.api.SatchelAccess;
import org.jetbrains.annotations.NotNull;
public class SatchelItem extends Item {
    public static final int DEFAULT_COLOR = 0xffaf5d2e;

    private final SatchelTier tier;

    public SatchelItem(SatchelTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public SatchelTier getTier() {
        return tier;
    }

    // TODO(26.1 port): ItemInteractionResult was folded into InteractionResult back around
    // 1.21.3 (Item#use / Block#useItemOn now return plain InteractionResult; see e.g.
    // PumpkinBlock#useItemOn's signature change). PASS_TO_DEFAULT_BLOCK_INTERACTION doesn't have
    // a literal 1:1 constant on InteractionResult — PASS is the closest equivalent for "let
    // vanilla handle it", but double-check against generated sources that this Item#use
    // override signature (InteractionResult vs some other overload) still matches what
    // vanilla's Item class declares in 26.1.
    @Override
    @NotNull
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        boolean equipped = SatchelAccess.equipSatchelTo(player, hand);
        if (equipped) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public static void playEquipSound(Player player) {
        float pitch = 0.9f + (player.getRandom().nextFloat() / 5);
        player.level().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                ModSounds.SATCHEL_EQUIP.get(), SoundSource.PLAYERS,
                1, pitch
        );
    }
}
