package net.hotbar.satchels.content.satchel;

/** Implemented by {@code PlayerMixin} to expose the mixin-injected {@link SatchelData} field. */
public interface IHaveSatchelData {
    SatchelData satchels$getSatchelData();
}
