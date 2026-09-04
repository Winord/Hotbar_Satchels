package net.hotbar.satchels.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.hotbar.satchels.Satchels;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Fabric datagen replacement for the static {@code assets/satchels/sounds.json}.
 * <p>
 * The Fabric Data Generation API has no equivalent of NeoForge's
 * {@code SoundDefinitionsProvider} (documented in {@link SatchelsDataGenerator}'s javadoc), so
 * this class implements {@link DataProvider} directly, with no intermediate Fabric helper —
 * the same way it would have to be done on plain vanilla datagen.
 * <p>
 * <b>Why not the {@link net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider}-style
 * "createPathProvider(Target, kind).json(id)" pattern:</b> that pattern is built for one file
 * *per ResourceLocation* (e.g. {@code lang/<code>.json}, {@code tags/item/<path>.json}).
 * {@code sounds.json} is a single file for the whole mod directly under
 * {@code assets/<modid>/}, with no intermediate subdirectory and no per-id association, so the
 * path is built directly via {@link PackOutput#getOutputFolder(PackOutput.Target)} (a public
 * method on {@link PackOutput} itself, which {@link FabricDataOutput} extends).
 * <p>
 * <b>Entry schema (the "type" field in vanilla sounds.json):</b>
 * <ul>
 *   <li>a bare string (e.g. {@code "satchels:satchel_open"}) — defaults to type {@code "sound"},
 *       a direct reference to the audio asset at
 *       {@code assets/<namespace>/sounds/<path>.ogg};</li>
 *   <li>an object with {@code "type": "event"} — reuses an already-registered
 *       {@code SoundEvent} as the sound source, applying its own {@code volume}/{@code pitch}
 *       (this is how {@code satchel_equip}/{@code satchel_close} are voiced through vanilla's
 *       {@code item.armor.equip_leather}/{@code item.bundle.drop_contents} instead of dedicated
 *       audio files).</li>
 * </ul>
 * Reproduces the current static file's contents 1:1 — checked byte-for-byte before the
 * duplicate was removed.
 */
public class SatchelsSoundDefinitionsProvider implements DataProvider {
    private final FabricDataOutput output;

    public SatchelsSoundDefinitionsProvider(FabricDataOutput output) {
        this.output = output;
    }

    @Override
    public @NotNull CompletableFuture<?> run(@NotNull CachedOutput cachedOutput) {
        JsonObject root = new JsonObject();

        root.add("satchel_close", eventSoundDefinition(
                "item.bundle.drop_contents", null, 1.2f, "sound.satchels.satchel_rustle"));
        root.add("satchel_equip", eventSoundDefinition(
                "item.armor.equip_leather", 0.8f, null, "sound.satchels.satchel_rustle"));
        root.add("satchel_open", fileSoundDefinition(
                Satchels.at("satchel_open"), "sound.satchels.satchel_rustle"));

        Path path = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                .resolve(output.getModId())
                .resolve("sounds.json");

        return DataProvider.saveStable(cachedOutput, root, path);
    }

    /**
     * Builds an entry of the form {@code {"sounds": [{"type": "event", "name": ...,
     * "volume"/"pitch": ...}], "subtitle": ...}} — re-voices an already-existing
     * vanilla/mod {@code SoundEvent}. Exactly one of {@code volume}/{@code pitch} is expected
     * to be non-{@code null} (our current set never needs both at once; extend the parameters
     * later without changing the signature for existing callers if that's ever needed).
     */
    private static JsonObject eventSoundDefinition(String eventName, Float volume, Float pitch, String subtitleKey) {
        JsonObject sound = new JsonObject();
        sound.addProperty("type", "event");
        sound.addProperty("name", eventName);
        if (volume != null) {
            sound.addProperty("volume", volume);
        }
        if (pitch != null) {
            sound.addProperty("pitch", pitch);
        }

        JsonArray sounds = new JsonArray();
        sounds.add(sound);

        JsonObject definition = new JsonObject();
        definition.add("sounds", sounds);
        definition.addProperty("subtitle", subtitleKey);
        return definition;
    }

    /**
     * Builds an entry of the form {@code {"sounds": ["<namespace>:<path>"], "subtitle": ...}}
     * — a direct reference to the audio file at
     * {@code assets/<namespace>/sounds/<path>.ogg} (defaults to type {@code "sound"}, no
     * wrapper object needed).
     */
    private static JsonObject fileSoundDefinition(ResourceLocation soundId, String subtitleKey) {
        JsonArray sounds = new JsonArray();
        sounds.add(soundId.toString());

        JsonObject definition = new JsonObject();
        definition.add("sounds", sounds);
        definition.addProperty("subtitle", subtitleKey);
        return definition;
    }

    @Override
    public @NotNull String getName() {
        return "Sound Definitions";
    }
}
