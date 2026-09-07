package net.hotbar.satchels.datagen;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.hotbar.satchels.ModItems;
import net.hotbar.satchels.content.satchel.SatchelItem;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Generates one two-layer {@code minecraft:item/generated} model per satchel tier:
 * layer0 = shared dyeable body ({@code satchels:item/satchel}),
 * layer1 = tier clip ({@link net.hotbar.satchels.content.satchel.SatchelTier#getClipTexture()}).
 * <p>
 * Implemented as a plain {@link DataProvider} (no {@code FabricModelProvider} superclass)
 * because in 1.21.4 {@code FabricModelProvider} and the Mojang datagen model classes it
 * depends on ({@code BlockModelGenerators}, {@code ItemModelGenerators},
 * {@code ModelLocationUtils}) moved to the client environment
 * ({@code @Environment(EnvType.CLIENT)}). They are therefore absent from the server-side
 * compile classpath used by {@code compileJava}, even with
 * {@code configureDataGeneration { client = true }} in build.gradle — that flag only affects
 * the {@code runDatagen} run configuration, not the main compile task.
 * <p>
 * Writing the JSON directly avoids the dependency entirely. The output is byte-for-byte
 * identical to what {@code ItemModelGenerators#generateLayeredItem} would have produced:
 * a {@code minecraft:item/generated} model with two texture layers.
 * <p>
 * {@code satchel_worn_<tier>.json} are left static — they are custom Blockbench geometry
 * (arbitrary elements/rotations/UVs), not flat templated models. See
 * {@code SatchelsClient#registerExtraModels} and {@code SatchelLayer} for how these are used.
 */
public class SatchelsModelProvider implements DataProvider {
    private static final ResourceLocation GENERATED_PARENT = ResourceLocation.withDefaultNamespace("item/generated");
    private static final ResourceLocation LAYER0 = ResourceLocation.fromNamespaceAndPath("satchels", "item/satchel");

    private final FabricDataOutput output;

    public SatchelsModelProvider(FabricDataOutput output) {
        this.output = output;
    }

    @Override
    public @NotNull CompletableFuture<?> run(@NotNull CachedOutput cachedOutput) {
        List<CompletableFuture<?>> futures = new ArrayList<>();

        Path modelsDir = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                .resolve(output.getModId())
                .resolve("models")
                .resolve("item");

        for (SatchelItem satchel : ModItems.ALL_SATCHELS) {
            ResourceLocation layer1 = satchel.getTier().getClipTexture();

            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", LAYER0.toString());
            textures.addProperty("layer1", layer1.toString());

            JsonObject model = new JsonObject();
            model.addProperty("parent", GENERATED_PARENT.toString());
            model.add("textures", textures);

            Path path = modelsDir.resolve(satchel.getTier().getItemPath() + ".json");
            futures.add(DataProvider.saveStable(cachedOutput, model, path));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public @NotNull String getName() {
        return "Item Models";
    }
}
