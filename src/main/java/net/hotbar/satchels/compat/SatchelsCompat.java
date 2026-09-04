package net.hotbar.satchels.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.hotbar.satchels.compat.accessories.AccessoriesCompat;
import net.hotbar.satchels.compat.quark.QuarkCompat;
import net.hotbar.satchels.compat.raised.RaisedCompat;
import net.hotbar.satchels.compat.vanilla.VanillaCompat;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Registry of companion-mod compat modules. Each entry is checked against
 * {@link FabricLoader#isModLoaded(String)} (plus an optional extra {@code shouldLoad}
 * condition) at {@link #initialize()} time, and its {@link CompatEntrypoint} runs only if the
 * companion mod is actually present.
 * <p>
 * {@code VANILLA} only initializes when Accessories is absent, since Accessories replaces the
 * vanilla equipment-slot handling. {@link QuarkCompat}/{@code QUARK} stays in the enum but
 * never actually activates — Quark has no Fabric port, so {@code isLoaded("quark")} is always
 * {@code false}.
 */
public enum SatchelsCompat {
    VANILLA("minecraft", VanillaCompat::new, () -> !FabricLoader.getInstance().isModLoaded("accessories")),
    // for vanilla slots    ACCESSORIES("accessories", AccessoriesCompat::new),
    ACCESSORIES("accessories", AccessoriesCompat::new),
    RAISED("raised", RaisedCompat::new),
    QUARK("quark", QuarkCompat::new);

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
