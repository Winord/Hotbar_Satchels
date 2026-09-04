package net.hotbar.satchels.compat.raised;

import dev.yurisuika.raised.api.RaisedApi;
import net.hotbar.satchels.Satchels;
import net.hotbar.satchels.client.satchel.SatchelHotbarOverlay;
import net.hotbar.satchels.compat.CompatEntrypoint;

/**
 * Registers the satchel hotbar overlay as a Raised layer.
 * <p>
 * {@code RaisedApi.getY(name)}/{@code getX(name)} throw a {@code NullPointerException} for a
 * layer that was never registered via {@code RaisedApi.register(...)} — Raised's
 * {@code Configure.getLayer(name)} returns {@code null} for an unknown name, and the
 * displacement lookup on that fails. Since {@link SatchelHotbarOverlay#render} calls
 * {@link #getSatchelYOffset()} unconditionally whenever Raised is loaded, {@link #initialize()}
 * must register the layer up front, with a default (0,0) offset. The public {@code RaisedApi}
 * doesn't expose a way to auto-sync a layer's position to the vanilla hotbar; the player can
 * do that themselves from Raised's own options screen, or position it independently.
 */
public class RaisedCompat implements CompatEntrypoint {
    @Override
    public void initialize() {
        RaisedApi.register(Satchels.at(SatchelHotbarOverlay.ID));
    }

    public static int getSatchelXOffset() {
        return RaisedApi.getX(Satchels.at(SatchelHotbarOverlay.ID));
    }
    public static int getSatchelYOffset() {
        return RaisedApi.getY(Satchels.at(SatchelHotbarOverlay.ID));
    }
}

