package net.hotbar.satchels.compat.quark;

import net.hotbar.satchels.compat.CompatEntrypoint;

/**
 * Permanent stub: Quark has no Fabric port, so {@code SatchelsCompat.isLoaded("quark")} is
 * always {@code false} and {@link #initialize()} is never actually called. This class exists
 * only so the {@code QUARK} enum constant in {@code SatchelsCompat} compiles. If Quark ever
 * gets a Fabric port, this is where its compat logic would go.
 */
public class QuarkCompat implements CompatEntrypoint {
    @Override
    public void initialize() {
    }
}
