package net.hotbar.satchels.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.hotbar.satchels.compat.ohmega.OhmegaCompat;
import net.hotbar.satchels.compat.raised.RaisedCompat;
import net.hotbar.satchels.compat.trinkets.TrinketsCompat;
import net.hotbar.satchels.compat.vanilla.VanillaCompat;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Registry of companion-mod compat modules. Each entry is checked against
 * {@link FabricLoader#isModLoaded(String)} (plus an optional extra {@code shouldLoad}
 * condition) at {@link #initialize()} time, and its {@link CompatEntrypoint} runs only if the
 * companion mod is actually present.
 * <p>
 * <b>Trinkets is the primary slot-compat on 26.1.x, Ohmega is the fallback.</b> Trinkets was
 * previously archived here for the whole 26.1.x cycle due to an upstream slot-id/visual-position
 * desync bug (items placed into satchel storage slots landing in the wrong slot). Re-enabled
 * after finding the real root cause was NOT Trinkets itself but a mod-load-order-dependent
 * collision in our own {@code InventoryMenuMixin}: it appended satchel slots at {@code TAIL} of
 * {@code InventoryMenu}'s constructor with no explicit mixin priority, same as Trinkets' own
 * TAIL inject on that constructor — so whenever Trinkets' own dynamically-sized slot insertion
 * differed between client and server, our satchel slots silently inherited the same index
 * offset. Fixed by pinning {@code InventoryMenuMixin} to {@code priority = 500} (see that
 * class), which is independent of and unrelated to which accessory-slot compat is active.
 * {@code OHMEGA.shouldLoad} is conditioned on Trinkets' absence (mirrors {@code VANILLA}'s
 * condition on Ohmega) so that when both are installed, Trinkets wins outright:
 * {@link OhmegaCompat#initialize()} never runs, so {@code AccessoryHelper.bindAccessory} is
 * never called and satchels simply aren't a bindable Ohmega accessory — no Ohmega slot to equip
 * into, no double-equip path to guard elsewhere.
 * <p>
 * When both {@code trinkets_updated} and {@code ohmega} are present, a startup notice is logged
 * so server admins aren't left wondering why their Ohmega satchel slot isn't showing up.
 * <p>
 * <b>{@code VANILLA.shouldLoad} must exclude every accessory-slot compat's mod id, not just
 * Ohmega's.</b> {@link net.hotbar.satchels.compat.vanilla.VanillaCompat} is its own mutually
 * exclusive equip path (a dedicated {@code SatchelEquipmentSlot}) and conflicts with either
 * Trinkets or Ohmega if both register equip callbacks at once. This was a one-mod check while
 * Trinkets was hardcoded off; re-enabling Trinkets required adding its mod id here too — keep
 * that in mind if a future accessory-slot compat is added.
 */
public enum SatchelsCompat {
    VANILLA("minecraft", VanillaCompat::new, () ->
            !FabricLoader.getInstance().isModLoaded("ohmega")
                    && !FabricLoader.getInstance().isModLoaded("trinkets_updated")),
    TRINKETS("trinkets_updated", TrinketsCompat::new),
    // Fallback for when Trinkets isn't installed — see the class javadoc above.
    OHMEGA("ohmega", OhmegaCompat::new, () -> !FabricLoader.getInstance().isModLoaded("trinkets_updated")),
    RAISED("raised", RaisedCompat::new);

    private static final Logger LOGGER = LoggerFactory.getLogger("Satchels/Compat");

    final String id;
    boolean isLoaded;

    @Nullable
    final CompatEntrypoint entrypoint;
    final BooleanSupplier shouldLoad;

    SatchelsCompat(String id, Supplier<CompatEntrypoint> entrypoint, BooleanSupplier shouldLoad) {
        this.id = id;

        this.entrypoint = entrypoint.get();
        this.isLoaded = SatchelsCompat.isLoaded(id);
        this.shouldLoad = shouldLoad;
    }

    SatchelsCompat(String id, Supplier<CompatEntrypoint> entrypoint) {
        this(id, entrypoint, () -> true);
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    private void shouldNotLoad() {
        isLoaded = false;
    }

    public static void initialize() {
        if (TRINKETS.isLoaded && OHMEGA.isLoaded) {
            LOGGER.info("Both Trinkets Updated and Ohmega detected — Trinkets takes priority for "
                    + "the satchel accessory slot, Ohmega's satchel slot integration stays inactive.");
        }

        for (SatchelsCompat compat : values()) {
            if (!compat.shouldLoad.getAsBoolean()) {
                compat.shouldNotLoad();
                continue;
            }

            if (compat.entrypoint != null && compat.isLoaded) compat.entrypoint.initialize();
        }
    }

    public static boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
