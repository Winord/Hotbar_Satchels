package net.hotbar.satchels;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.hotbar.satchels.util.SatchelsRegistry;

/**
 * Sound event registration via {@link Registry#register}. Fields are wrapped as
 * {@link SatchelsRegistry.Held} (a {@code Supplier<SoundEvent>}) so call sites keep
 * using {@code ModSounds.SATCHEL_EQUIP.get()}.
 */
public class ModSounds {
    public static final SatchelsRegistry.Held<SoundEvent> SATCHEL_EQUIP = dynamicRange("satchel_equip");
    public static final SatchelsRegistry.Held<SoundEvent> SATCHEL_OPEN = dynamicRange("satchel_open");
    public static final SatchelsRegistry.Held<SoundEvent> SATCHEL_CLOSE = dynamicRange("satchel_close");

    private static SatchelsRegistry.Held<SoundEvent> dynamicRange(String path) {
        SoundEvent event = SoundEvent.createVariableRangeEvent(Satchels.at(path));
        SoundEvent registered = Registry.register(BuiltInRegistries.SOUND_EVENT, Satchels.at(path), event);
        return new SatchelsRegistry.Held<>(registered);
    }

    /** Called from {@link Satchels#onInitialize()} to force this class to load. */
    public static void register() {
    }
}
