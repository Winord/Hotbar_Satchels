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
 * Ohmega integration — verified against the real {@code ohmega-1.5.21+26.1.2} jar (javap +
 * CFR decompilation of {@code AccessoryHelper}, {@code AccessoryContainer}, {@code
 * AccessorySlot}, {@code IAccessory}, {@code OhmegaCommon}, {@code AngelRing}), not guessed
 * from search snippets — same standard as every other 26.1 port fix in this codebase.
 * <p>
 * <b>Class-loading note — this class deliberately does NOT reference {@code IAccessory}
 * anywhere in its own bytecode, not even as a call-site argument type (see
 * {@link SatchelAccessory} below).</b> {@code SatchelsCompat}'s enum constructor calls
 * {@code entrypoint.get()} — i.e. {@code new OhmegaCompat()} — for every {@code SatchelsCompat}
 * entry unconditionally, before checking {@code isLoaded}/{@code shouldLoad} (same as {@code
 * TrinketsCompat}/{@code RaisedCompat}). Merely constructing that method reference
 * ({@code OhmegaCompat::new}) forces the JVM to verify {@code OhmegaCompat}'s bytecode as a
 * whole (the JVM verifies a class's methods together at link time, not lazily per method
 * actually invoked) — and bytecode verification needs to resolve (load) any class whose
 * assignability to another, differently-named reference type it must confirm, e.g. passing a
 * value of one declared type where a different declared type is expected. Two things trip
 * this, independently, and both had to be fixed:
 * <ol>
 *   <li>An earlier version of this file had {@code OhmegaCompat implements IAccessory}
 *   directly. Fixed by declaring {@code IAccessory} only on the nested {@link
 *   SatchelAccessory} class instead.</li>
 *   <li><b>That alone wasn't enough.</b> {@code OhmegaCompat#initialize()} still called
 *   {@code AccessoryHelper.bindAccessory(Item, IAccessory)} directly, passing a value declared
 *   as {@code SatchelAccessory} where the parameter type is {@code IAccessory} — a different
 *   named type, still forcing the verifier to resolve {@code IAccessory} to confirm the
 *   assignment, right there inside {@code OhmegaCompat}'s own bytecode. Fixed by moving the
 *   {@code bindAccessory} call itself into {@link SatchelAccessory#registerAll}, so {@code
 *   OhmegaCompat} never mentions {@code IAccessory} in any method body, parameter, or return
 *   type — confirmed from a real crash log, {@code NoClassDefFoundError:
 *   com/swacky/ohmega/api/IAccessory} thrown from exactly {@code SatchelsCompat}'s
 *   {@code <clinit>} at the {@code OHMEGA(...)} enum constant, even after fix #1 alone.</li>
 * </ol>
 * With Ohmega absent/disabled, {@code OhmegaCompat} now loads and verifies cleanly; {@link
 * SatchelAccessory} — the only class that actually mentions {@code IAccessory} anywhere —
 * only ever loads when {@link #initialize()} runs, which only happens once {@code
 * SatchelsCompat} has confirmed Ohmega is loaded.
 * <p>
 * <b>Ohmega's accessory model is fundamentally different from Trinkets', not just a rename:</b>
 * <ul>
 *   <li>There is no data-driven "give this mod a dedicated named slot" mechanism like
 *   Trinkets' {@code data/trinkets/entities/player.json}. Instead, a mod calls
 *   {@link AccessoryHelper#bindAccessory(net.minecraft.world.item.Item, IAccessory)} once per
 *   item to associate behaviour, and the bound item then competes for one of the player's
 *   generic accessory slots. Slot *count* and each slot's {@code AccessoryType} (generic/
 *   normal/special/utility) are server-config-driven ({@code OhmegaConfig.Server.slotTypes()}),
 *   not something this mod defines. We don't tag the satchel items into a custom
 *   {@code AccessoryType} here, so they fall back to {@code NORMAL} (the default when no tag
 *   matches — see {@code AccessoryHelper.getType}) and need at least one normal-type slot to
 *   exist server-side. That's a real, player-facing difference from Trinkets worth knowing
 *   about — it isn't something a compat module can paper over.</li>
 *   <li>{@code IAccessory} is implemented directly (here, by {@link SatchelAccessory}, bound
 *   to all three satchel tiers) rather than driven by a global equip-changed event with old/
 *   new stacks — {@code AccessoryContainer#doEquip}/{@code #doUnequip} call {@code onEquip}/
 *   {@code onUnequip} on the bound instance directly, and {@code AccessorySlot#mayPickup}
 *   calls {@code canUnequip} — both confirmed by decompiling those classes.</li>
 *   <li>{@code IAccessory#compatibleWith(ItemStack)}'s default implementation already returns
 *   {@code false} when the other stack is bound to the *same* {@code IAccessory} instance
 *   (see {@code IAccessory}'s decompiled default body) — since all three satchel tiers are
 *   bound to the one {@link SatchelAccessory} instance, that default alone already prevents
 *   wearing two satchels at once. No override needed.</li>
 *   <li><b>Login gap (found only by decompiling {@code AccessoryContainer#onAttach}):</b> on
 *   attach (player join), Ohmega restores each slot's {@code ItemStack} silently and only
 *   re-fires {@code onEquip} for stacks that are Ohmega-"active" (its separate toggle-on-use
 *   flag, e.g. Angel Ring's flight toggle — irrelevant to the satchel, which never sets it).
 *   A satchel that was equipped before logout therefore does NOT get {@code onEquip} called
 *   again on the next login, so {@link SatchelData#setSatchelSlotStack} would silently stay
 *   stale (0-slot inventory) until the player touched the slot. {@link #onPlayerJoin} exists
 *   solely to close that gap — see {@link SatchelData#resyncToClient()}'s own javadoc, which
 *   independently describes exactly this class of bug for the join/respawn/dimension-change
 *   path in general.</li>
 * </ul>
 * <p>
 * <b>Build wiring:</b> needs {@code compileOnly}/{@code localRuntime} on
 * {@code maven.modrinth:ohmega:1.5.21} (this is the jar this class was verified against) plus
 * Ohmega's own hard dependency, Forge Config API Port ({@code forgeconfigapiport}, required
 * {@code >=26.1} per Ohmega's {@code fabric.mod.json}) for dev-run testing — see
 * {@code build.gradle}/{@code gradle.properties}. Not needed in our own {@code fabric.mod.json}
 * {@code depends}/{@code suggests}: a player who has Ohmega installed already has
 * forgeconfigapiport too, since Fabric Loader won't load Ohmega without it.
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
     * Delegates to {@link AccessoryHelper#tryEquip}, which already does exactly what we need
     * for a right-click-to-equip flow: finds the first open slot whose {@code AccessoryType}
     * matches the item's, moves a single copy in, shrinks the hand stack by one, and plays
     * {@code getEquipSound()} if non-null (ours returns the {@code IAccessory} default of
     * {@code null} — the satchel plays its own sound from {@link SatchelAccessory#onEquip}
     * instead, matching the old Trinkets behaviour of sounding on *any* equip path, not just
     * from-hand).
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
        // Ohmega's own ServerPlayerEvents.COPY_FROM handler (CommonCallbacks#onClonePlayer)
        // already copies the accessory container itself across death/respawn when keepInventory
        // applies; we only need SatchelData.satchelSlotStack to stay mirrored to whatever ends
        // up equipped afterwards.
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
     * Unlike Trinkets, Ohmega has no separate cosmetic-slot concept for us to distinguish here
     * — every equipped stack is "functional" — so we deliberately don't register a
     * {@code SATCHEL_VISUAL_STACK_GETTERS} entry; {@link SatchelAccess#getSatchelVisualStack}
     * already falls back to {@link #getSatchelStack} when nothing overrides it. Likewise no
     * {@code IS_VISIBLE_PREDICATES} entry — {@link SatchelAccess#satchelIsVisible} already
     * defaults to {@code true} on an empty set, which is the correct behaviour here since
     * there's nothing Ohmega-specific to gate on.
     */
    public boolean playerCanAccessSatchel(Player player) {
        return !getSatchelStack(player).isEmpty();
    }

    // -------------------------------------------------------------------------
    // IAccessory — see the class javadoc's "Class-loading note" for why this lives in its
    // own nested class instead of on OhmegaCompat itself. Called directly by
    // AccessoryContainer#doEquip/doUnequip and AccessorySlot#mayPickup (confirmed by
    // decompiling both).
    // -------------------------------------------------------------------------
    private static final class SatchelAccessory implements IAccessory {
        private final OhmegaCompat compat;

        private SatchelAccessory(OhmegaCompat compat) {
            this.compat = compat;
        }

        /**
         * Deliberately the ONLY place {@link AccessoryHelper#bindAccessory} is called — see
         * the class javadoc's "Class-loading note". It's not enough for {@code implements
         * IAccessory} to live here instead of on {@link OhmegaCompat}: if {@code
         * OhmegaCompat#initialize()} itself called {@code bindAccessory(Item, IAccessory)}
         * directly, passing a {@code SatchelAccessory}-typed value where an {@code IAccessory}
         * parameter is expected, the bytecode verifier would still need to resolve {@code
         * IAccessory} to confirm that assignment is type-safe — and per the JVM spec,
         * verification happens for a class's methods as a whole at link time, not lazily per
         * method actually invoked. That would force {@code IAccessory} to load the moment
         * {@code OhmegaCompat} itself is verified (i.e. right when {@code SatchelsCompat}
         * creates the {@code OhmegaCompat::new} method reference in its enum {@code <clinit>}
         * — confirmed from a real crash log, {@code NoClassDefFoundError:
         * com/swacky/ohmega/api/IAccessory} at exactly that line, even after moving the
         * {@code implements IAccessory} declaration down to this nested class on its own).
         * Keeping the call itself here too means no method on {@link OhmegaCompat} ever
         * mentions {@code IAccessory} in any parameter, return type, or argument-type
         * mismatch — the only cross-class reference is passing {@code compat} (statically
         * typed exactly {@code OhmegaCompat}, an exact match, no hierarchy check needed).
         */
        static void registerAll(OhmegaCompat compat) {
            SatchelAccessory accessory = new SatchelAccessory(compat);
            for (SatchelItem satchel : ModItems.ALL_SATCHELS) {
                AccessoryHelper.bindAccessory(satchel, accessory);
            }
        }

        /**
         * Server-only guard — see the "vanishes on first click" bugfix note below. Verified via
         * {@code AccessoryInventoryMenu}'s decompiled constructor: unlike Trinkets (whose
         * {@code TrinketEquipmentChangedCallback} only ever fires server-side), Ohmega builds a
         * real, interactive {@code AccessorySlot} — wired to the same shared
         * {@code AccessoryContainer} — on the LOGICAL CLIENT too (the ctor's
         * {@code if (!player.level().isClientSide()) {...} else {...}} branches both call
         * {@code addSlot(new AccessorySlot(...))}, just with different x/y layout for rendering
         * columns). Vanilla's own {@code AbstractContainerMenu#clicked} runs on both sides for
         * the open menu — client-side purely for instant local prediction, corrected by the
         * server's authoritative result — so {@code Slot#remove()}/{@code AccessorySlot#remove()}
         * → {@code AccessoryContainer#doUnequip} → this {@code onUnequip} genuinely fires on the
         * client during that local prediction, not just on the server.
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
         * BUGFIX ("сумка зникає, забирається лише з другого кліку" — satchel vanishes, only
         * comes out on the second click): before this guard, {@code onUnequip} ran unconditionally
         * — including on the client during {@code AbstractContainerMenu#clicked}'s local
         * prediction (see {@link #onEquip}'s javadoc for why Ohmega, unlike Trinkets, reaches
         * this callback client-side at all). On the client, {@code player} is a
         * {@code LocalPlayer}, not a {@code ServerPlayer}, so
         * {@code SatchelData#sendData()} threw its own {@code AssertionError("sendData should
         * only be called on the server")} right in the middle of the predicted click — i.e.
         * AFTER {@code AccessorySlot#remove()} had already mutated the client's local
         * {@code AccessoryContainer} stack list (removing the satchel from the slot) but BEFORE
         * vanilla's click code reached {@code setCarried(...)}. Net effect: the satchel
         * disappeared from the slot in the client's local prediction without ever landing in the
         * cursor, and the thrown exception aborted that predicted click before the actual
         * {@code ServerboundContainerClickPacket} could be sent — nothing reached the server
         * either. A second click (now against a locally-empty-but-server-still-equipped slot)
         * eventually forces a real round trip that resolves correctly. Guarding this method
         * (and {@link #onEquip}, symmetrically) to no-op off the logical server removes the
         * broken client-side execution entirely and lets the server's own authoritative click
         * handling — which never had this bug, since {@code player} really is a
         * {@code ServerPlayer} there — drive the client via its normal container-sync packets,
         * same as every other compat module in this codebase already (implicitly) relies on.
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
