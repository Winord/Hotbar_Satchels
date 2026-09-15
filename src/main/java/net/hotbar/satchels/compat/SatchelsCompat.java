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
 * <b>26.x port note:</b> {@code ACCESSORIES}/{@code AccessoriesCompat} was replaced by
 * {@code TRINKETS}/{@link TrinketsCompat} — Accessories has no Fabric build on 26.x (see
 * {@code satchels-port-decisions-26_1.md} §3). The mod id checked against
 * {@link FabricLoader#isModLoaded(String)} is {@code trinkets_updated}.
 * <p>
 * <b>26.1.x — Trinkets archived (see {@code docs/accessory-compat-roadmap.md}):</b> Trinkets
 * Updated's {@code 4.0.x+26.1} line has an upstream slot-id/visual-position desync bug (fixed
 * upstream only via the architecture rework that shipped alongside their own port to 26.2, in
 * {@code 4.1.0-rc.1+26.2} — not backported to 26.1). Symptom: items placed into satchel storage
 * slots land in the wrong slot or become unremovable. Until either Trinkets backports the fix
 * to 26.1 or this mod ports to 26.2, {@code TRINKETS.shouldLoad} is hardcoded {@code false} —
 * the entry (and {@link TrinketsCompat}) stay in the codebase, dormant, rather than being
 * deleted, so re-enabling on a future 26.1.x patch (if Trinkets ever backports) or on the 26.2
 * port is a one-line flip back to the mod-presence check. {@link OhmegaCompat} is the active
 * replacement for 26.1.x in the meantime.
 * <p>
 * When {@code trinkets_updated} is present but archived, a startup warning is logged so server
 * admins aren't left wondering why their Trinkets satchel slot isn't showing up.
 */
public enum SatchelsCompat {
    VANILLA("minecraft", VanillaCompat::new, () -> !FabricLoader.getInstance().isModLoaded("ohmega")),
    // Archived for 26.1.x — see the class javadoc above and docs/accessory-compat-roadmap.md.
    // Flip back to `() -> !FabricLoader.getInstance().isModLoaded("ohmega")` (matching VANILLA's
    // condition, i.e. "active whenever Ohmega isn't the one in charge") once re-enabled.
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
                    + "26.1.x pending an upstream slot-desync fix (see "
                    + "docs/accessory-compat-roadmap.md) — install Ohmega for satchel "
                    + "accessory-slot support instead.");
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