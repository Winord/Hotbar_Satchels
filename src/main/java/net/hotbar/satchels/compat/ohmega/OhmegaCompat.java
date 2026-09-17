package net.hotbar.satchels.compat.ohmega;

import com.swacky.ohmega.api.AccessoryHelper;
import com.swacky.ohmega.api.IAccessory;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.hotbar.satchels.ModItems;
import net.hotbar.satchels.ModTags;
import net.hotbar.satchels.api.SatchelAccess;
import net.hotbar.satchels.compat.CompatEntrypoint;
import net.hotbar.satchels.content.satchel.SatchelData;
import net.hotbar.satchels.content.satchel.SatchelItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Ohmega integration, verified against {@code ohmega-1.5.21+26.1.2}. Active satchel-accessory
 * compat on 26.1.x while Trinkets is archived — see {@code satchels-port-decisions-26_1.md}.
 * <p>
 * <b>Class-loading gotcha — do not add {@code implements IAccessory} to this class, and do not
 * call {@link AccessoryHelper#bindAccessory} from anywhere on it.</b> {@code SatchelsCompat}'s
 * enum constructor constructs every compat module (including this one) unconditionally, before
 * checking {@code isLoaded}. Constructing {@code OhmegaCompat::new} forces the JVM to verify
 * this class's whole bytecode at link time, which in turn forces any type it references —
 * including as an incidental method-call argument — to resolve. If {@code IAccessory} (from the
 * optional Ohmega dependency) appears anywhere in this class's bytecode, that crashes with
 * {@code NoClassDefFoundError} the moment this class loads, even with Ohmega absent. That's why
 * {@code IAccessory} is implemented only on the private nested {@link SatchelAccessory}, and
 * {@code bindAccessory} is called only from {@link SatchelAccessory#registerAll} — {@link
 * SatchelAccessory} loads lazily, only once {@link #initialize()} has confirmed Ohmega is present.
 * <p>
 * <b>Ohmega's accessory model differs from Trinkets, not just in name:</b>
 * <ul>
 *   <li>No data-driven named-slot file. A mod calls {@link AccessoryHelper#bindAccessory} once
 *   per item; the bound item then competes for one of the player's generic accessory slots.
 *   Slot count and type (generic/normal/special/utility) are server-config-driven. Satchels
 *   aren't tagged into a custom type, so they fall back to {@code NORMAL} and need at least one
 *   normal-type slot to exist server-side.</li>
 *   <li>{@code IAccessory} is implemented directly (by {@link SatchelAccessory}, bound to all
 *   three tiers) — {@code AccessoryContainer#doEquip/doUnequip} call {@code onEquip}/{@code
 *   onUnequip} on the bound instance directly, and {@code AccessorySlot#mayPickup} calls
 *   {@code canUnequip}.</li>
 *   <li>{@code IAccessory#compatibleWith}'s default already returns {@code false} for two stacks
 *   bound to the same instance, so binding all three tiers to one {@link SatchelAccessory}
 *   instance is enough to prevent wearing two satchels at once — no override needed.</li>
 *   <li><b>Login gap:</b> on attach, Ohmega restores each slot's item silently and only re-fires
 *   {@code onEquip} for stacks flagged Ohmega-"active" (its own toggle-on-use flag, which the
 *   satchel never sets). A satchel equipped before logout would stay stale until the player
 *   touched the slot — {@link #onPlayerJoin} exists solely to close that gap.</li>
 * </ul>
 * <p>
 * <b>Build wiring:</b> {@code compileOnly}/{@code localRuntime} on
 * {@code maven.modrinth:ohmega:1.5.21} plus its hard dependency Forge Config API Port — see
 * {@code build.gradle}/{@code gradle.properties}. Neither needs to appear in this mod's own
 * {@code fabric.mod.json}: a player with Ohmega already has forgeconfigapiport too.
 */
public class OhmegaCompat implements CompatEntrypoint {

    public final Map<Player, Integer> satchelTints = new WeakHashMap<>();

    @Override
    public void initialize() {
        SatchelAccessory.registerAll(this);

        SatchelAccess.SATCHEL_EQUIP_CALLBACKS.add(this::equipSatchel);
        SatchelAccess.CAN_ACCESS_PREDICATES.add(this::playerCanAccessSatchel);
        SatchelAccess.SATCHEL_STACK_GETTERS.add(this::getSatchelStack);
        SatchelAccess.SATCHEL_TINT_GETTERS.add(this::getSatchelTint);
        SatchelAccess.PLAYER_RESPAWN_CALLBACKS.add(this::onPlayerRespawn);

        // See class javadoc: closes the AccessoryContainer#onAttach login gap.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onPlayerJoin(handler.player));
    }

    // -------------------------------------------------------------------------
    // Equip from hand
    // -------------------------------------------------------------------------

    /**
     * Delegates to {@link AccessoryHelper#tryEquip} for right-click-to-equip. The satchel plays
     * its own sound from {@link SatchelAccessory#onEquip} rather than via
     * {@code getEquipSound()}, so it sounds on any equip path, not just from-hand.
     */
    public boolean equipSatchel(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || !stack.is(ModTags.SATCHEL)) return false;

        return AccessoryHelper.tryEquip(player, hand) == InteractionResult.SUCCESS;
    }

    // -------------------------------------------------------------------------
    // Login / respawn — mirror into SatchelData (see class javadoc, login gap)
    // -------------------------------------------------------------------------

    public void onPlayerJoin(ServerPlayer player) {
        ItemStack equipped = getSatchelStack(player);
        if (equipped.isEmpty()) return;

        satchelTints.put(player, DyedItemColor.getOrDefault(equipped, SatchelItem.DEFAULT_COLOR));
        SatchelData.get(player).setSatchelSlotStack(equipped.copy());
        SatchelData.get(player).resyncToClient();
    }

    public void onPlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        // Ohmega's own COPY_FROM handler already copies the accessory container itself across
        // death/respawn when keepInventory applies; only satchelSlotStack needs mirroring here.
        ItemStack equipped = getSatchelStack(newPlayer);
        if (!equipped.isEmpty()) {
            SatchelData.get(newPlayer).setSatchelSlotStack(equipped.copy());
        }
    }

    // -------------------------------------------------------------------------
    // Stack / tint getters
    // -------------------------------------------------------------------------

    public int getSatchelTint(Player player) {
        return satchelTints.getOrDefault(player, -1);
    }

    public ItemStack getSatchelStack(Player player) {
        for (ItemStack stack : AccessoryHelper.getStacks(player)) {
            if (stack.is(ModTags.SATCHEL)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Unlike Trinkets, Ohmega has no separate cosmetic slot — every equipped stack is
     * "functional" — so no {@code SATCHEL_VISUAL_STACK_GETTERS}/{@code IS_VISIBLE_PREDICATES}
     * entry is registered; the {@link SatchelAccess} defaults already do the right thing here.
     */
    public boolean playerCanAccessSatchel(Player player) {
        return !getSatchelStack(player).isEmpty();
    }

    // -------------------------------------------------------------------------
    // IAccessory — see the class javadoc's "Class-loading gotcha" for why this lives in its
    // own nested class instead of on OhmegaCompat itself.
    // -------------------------------------------------------------------------
    private static final class SatchelAccessory implements IAccessory {
        private final OhmegaCompat compat;

        private SatchelAccessory(OhmegaCompat compat) {
            this.compat = compat;
        }

        /**
         * Deliberately the ONLY place {@link AccessoryHelper#bindAccessory} is called — see the
         * class javadoc's "Class-loading gotcha". {@code OhmegaCompat} itself must never call
         * {@code bindAccessory(Item, IAccessory)}, or the verifier is forced to resolve
         * {@code IAccessory} the moment {@code OhmegaCompat} loads, regardless of whether
         * Ohmega is present.
         */
        static void registerAll(OhmegaCompat compat) {
            SatchelAccessory accessory = new SatchelAccessory(compat);
            for (SatchelItem satchel : ModItems.ALL_SATCHELS) {
                AccessoryHelper.bindAccessory(satchel, accessory);
            }
        }

        /**
         * Server-only guard. Unlike Trinkets, Ohmega builds a real, interactive
         * {@code AccessorySlot} on the logical client too (for local click prediction), so
         * {@code onUnequip} genuinely fires client-side as well — see the bugfix note below.
         */
        @Override
        public void onEquip(Player player, ItemStack stack) {
            if (!(player instanceof ServerPlayer)) return;

            compat.satchelTints.put(player, DyedItemColor.getOrDefault(stack, SatchelItem.DEFAULT_COLOR));
            if (!player.firstTick) SatchelItem.playEquipSound(player);

            // Mirror into SatchelData — drives tier tracking and inventory resizing.
            SatchelData.get(player).setSatchelSlotStack(stack.copy());
        }

        /**
         * Bugfix (satchel vanished, only came out on a second click): without this guard,
         * {@code onUnequip} also ran during the client's local click prediction, where
         * {@code player} is a {@code LocalPlayer} — {@code SatchelData#sendData()} would throw
         * mid-click, after the slot's stack was already cleared locally but before it reached
         * the cursor, aborting the predicted click before the server ever saw it. Guarding both
         * this method and {@link #onEquip} to the logical server only lets the server's
         * authoritative click handling drive the client via normal sync packets instead.
         */
        @Override
        public void onUnequip(Player player, ItemStack stack) {
            if (!(player instanceof ServerPlayer)) return;

            compat.satchelTints.remove(player);

            SatchelData satchelData = SatchelData.get(player);
            satchelData.getSatchelInventory().dropAll(false);
            if (satchelData.isActive()) satchelData.setActive(false, true);
            satchelData.setSatchelSlotStack(ItemStack.EMPTY);
            satchelData.sendData();
        }

        /** Prevents unequipping a satchel that still has items in it (mirrors old TrinketsCompat). */
        @Override
        public boolean canUnequip(Player player, ItemStack stack) {
            return SatchelData.get(player).getSatchelInventory().isEmpty();
        }
    }
}
