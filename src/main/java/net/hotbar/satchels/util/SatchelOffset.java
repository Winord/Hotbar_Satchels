package net.hotbar.satchels.util;

/**
 * Simple (x, y) pixel offset pair. Replaces {@code net.minecraft.util.Tuple}, which 26.2
 * removed outright (confirmed absent from the decompiled 26.2 jar — not a rename, gone
 * entirely). Used for the per-menu GUI/overlay slot offsets in {@code SatchelsCommonConfig}.
 */
public record SatchelOffset(int x, int y) {
    public static final SatchelOffset ZERO = new SatchelOffset(0, 0);
}
