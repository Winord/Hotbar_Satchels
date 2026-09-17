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
 * <b>Trinkets is archived on 26.1.x</b> — Trinkets Updated's {@code 4.0.x+26.1} line has an
 * upstream slot-id/visual-position desync bug (items placed into satchel storage slots land in
 * the wrong slot or become unremovable), fixed upstream only in {@code 4.1.0-rc.1+26.2}, not
 * backported to 26.1. {@code TRINKETS.shouldLoad} is hardcoded {@code false}; the entry and
 * {@link TrinketsCompat} stay in the codebase, dormant, so re-enabling is a one-line flip back
 * to the mod-presence check once Trinkets backports the fix or this mod ports to 26.2. See
 * {@code satchels-port-decisions-26_1.md}. {@link OhmegaCompat} is the active replacement.
 * <p>
 * When {@code trinkets_updated} is present but archived, a startup warning is logged so server
 * admins aren't left wondering why their Trinkets satchel slot isn't showing up.
 */
public enum SatchelsCompat {
    VANILLA("minecraft", VanillaCompat::new, () -> !FabricLoader.getInstance().isModLoaded("ohmega")),
    // Archived for 26.1.x — see the class javadoc above. Flip back to
    // `() -> !FabricLoader.getInstance().isModLoaded("ohmega")` (matching VANILLA's condition)
    // once re-enabled.
    TRINKETS("trinkets_updated", TrinketsCompat::new, () -> false),
    OHMEGA("ohmega", OhmegaCompat::new),
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
        if (TRINKETS.isLoaded) {
            LOGGER.warn("Trinkets Updated detected, but its satchel integration is archived on "
                    + "26.1.x pending an upstream slot-desync fix — "
                    + "install Ohmega for satchel accessory-slot support instead.");
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