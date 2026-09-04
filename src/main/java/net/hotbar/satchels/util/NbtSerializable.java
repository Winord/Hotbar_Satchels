package net.hotbar.satchels.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

/**
 * Simple NBT save/load contract, implemented by {@code SatchelData} and
 * {@code SatchelInventory}.
 */
public interface NbtSerializable<T extends Tag> {
    @NotNull
    T serializeNBT(@NotNull HolderLookup.Provider provider);

    void deserializeNBT(@NotNull HolderLookup.Provider provider, @NotNull T tag);
}
