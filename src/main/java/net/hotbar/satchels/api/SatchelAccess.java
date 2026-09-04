package net.hotbar.satchels.api;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.hotbar.satchels.content.satchel.SatchelItem;
import net.hotbar.satchels.content.satchel.SatchelTier;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.BiConsumer;


/**
 * Hooks/callbacks for other mods (or compat modules) to plug into satchel access, visibility,
 * and rendering — e.g. Accessories registers predicates and getters here.
 */
public class SatchelAccess {
    public static Set<SatchelEquipCallback> SATCHEL_EQUIP_CALLBACKS = new HashSet<>();
    public static Set<Predicate<Player>> CAN_ACCESS_PREDICATES = new HashSet<>();
    public static Set<Predicate<Player>> IS_VISIBLE_PREDICATES = new HashSet<>();
    public static Set<Function<Player, ItemStack>> SATCHEL_STACK_GETTERS = new HashSet<>();
    public static Set<Function<Player, ItemStack>> SATCHEL_VISUAL_STACK_GETTERS = new HashSet<>();
    public static Set<Function<Player, Integer>> SATCHEL_TINT_GETTERS = new HashSet<>();

    public static Set<BiConsumer<ServerPlayer, ServerPlayer>> PLAYER_RESPAWN_CALLBACKS = new HashSet<>();

    public static void notifyPlayerRespawned(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        PLAYER_RESPAWN_CALLBACKS.forEach(c -> c.accept(oldPlayer, newPlayer));
    }

    public static boolean equipSatchelTo(Player player, InteractionHand hand) {
        return SATCHEL_EQUIP_CALLBACKS.stream().anyMatch(p -> p.equip(player, hand));
    }

    public static boolean canAccessSatchel(Player player) {
        return (
                CAN_ACCESS_PREDICATES.isEmpty() ||
                        CAN_ACCESS_PREDICATES.stream().anyMatch(p -> p.test(player))
        );
    }

    /**
     * Whether the player's satchel model should render — a purely cosmetic concern, decoupled
     * from {@link #canAccessSatchel} (functional storage-access gating). This lets a satchel
     * worn only in Accessories' cosmetic slot render without granting storage access. See
     * {@code AccessoriesCompat#playerSatchelIsVisible}.
     */
    public static boolean satchelIsVisible(Player player) {
        return IS_VISIBLE_PREDICATES.isEmpty() ||
                IS_VISIBLE_PREDICATES.stream().allMatch(p -> p.test(player));
    }

    public static ItemStack getSatchelStack(Player player) {
        for (Function<Player, ItemStack> getter : SATCHEL_STACK_GETTERS) {
            ItemStack stack = getter.apply(player);
            if (!stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * The stack {@code SatchelLayer} should render on the player's back — prefers a cosmetic
     * override over the functional satchel when a compat module distinguishes the two (see
     * {@code AccessoriesCompat#getSatchelVisualStack}). Separate from
     * {@link #SATCHEL_STACK_GETTERS}/{@link #getSatchelStack}: {@link #getSatchelTier}/
     * {@link #getSatchelSlotCount} are built on {@link #getSatchelStack} and must keep
     * reflecting real storage capacity regardless of cosmetic skin. Falls back to
     * {@link #getSatchelStack} when nothing here matches.
     */
    public static ItemStack getSatchelVisualStack(Player player) {
        for (Function<Player, ItemStack> getter : SATCHEL_VISUAL_STACK_GETTERS) {
            ItemStack stack = getter.apply(player);
            if (!stack.isEmpty()) return stack;
        }
        return getSatchelStack(player);
    }

    public static int getSatchelTint(Player player) {
        for (Function<Player, Integer> getter : SATCHEL_TINT_GETTERS) {
            int tint = getter.apply(player);
            if (tint != -1) return tint;
        }
        return -1;
    }

    /**
     * The tier of the player's currently equipped satchel, if any. Reads it off the equipped
     * {@link SatchelItem} itself (via {@link #getSatchelStack}) rather than a separate
     * per-player field, so it's always in sync with what's actually equipped.
     */
    public static Optional<SatchelTier> getSatchelTier(Player player) {
        ItemStack stack = getSatchelStack(player);
        if (stack.isEmpty() || !(stack.getItem() instanceof SatchelItem satchelItem)) {
            return Optional.empty();
        }
        return Optional.of(satchelItem.getTier());
    }

    /**
     * Storage slot count of the player's currently equipped satchel — {@code 0} if none is
     * equipped. Thin wrapper over {@link #getSatchelTier}/{@link SatchelTier#getSlotCount()}.
     */
    public static int getSatchelSlotCount(Player player) {
        return getSatchelTier(player).map(SatchelTier::getSlotCount).orElse(0);
    }

    @FunctionalInterface
    public interface SatchelEquipCallback {
        boolean equip(Player player, InteractionHand hand);
    }
}