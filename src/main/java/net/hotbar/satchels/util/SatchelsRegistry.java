package net.hotbar.satchels.util;

/**
 * Thin holder wrapper so registered objects can still be accessed as
 * {@code ModSounds.SATCHEL_EQUIP.get()} instead of a plain field. Registration already
 * happens synchronously by the time a {@link Held} is constructed, so {@link Held#get()}
 * always returns a ready object.
 */
public final class SatchelsRegistry {
    private SatchelsRegistry() {
    }

    public static final class Held<T> {
        private final T value;

        public Held(T value) {
            this.value = value;
        }

        public T get() {
            return value;
        }
    }
}
