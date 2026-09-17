package net.hotbar.satchels.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Entry point for the Fabric Data Generation API. Registered in {@code fabric.mod.json} under
 * the {@code "fabric-datagen"} key — only invoked by {@code ./gradlew runDatagen}, not during
 * a normal mod run.
 * <p>
 * <b>Generated here:</b> {@link SatchelsRecipeProvider} (recipes + auto unlock advancements),
 * {@link SatchelsItemTagProvider} (dyeable/satchel tags, plus the cross-mod
 * {@code trinkets:chest/satchel} slot tag), {@link SatchelsLanguageProvider} (only
 * {@code en_us.json} — other languages stay hand-written community translations),
 * {@link SatchelsSoundDefinitionsProvider} ({@code assets/satchels/sounds.json}, written from
 * scratch since Fabric API has no built-in equivalent), and {@link SatchelsModelProvider}
 * (the two-layer satchel item model; {@code satchel_worn_*.json} stays static — see its javadoc).
 * <p>
 * <b>Deliberately left as static json:</b> {@code data/trinkets/entities/player.json} and
 * {@code data/trinkets/slots/chest/satchel.json} — Trinkets Updated has no known stable Fabric
 * datagen provider API for these (its tag files are ordinary item tags and are generated
 * normally, via {@link SatchelsItemTagProvider}). Schema per
 * {@code github.com/emilyploszaj/trinkets/wiki/Trinkets-Data-Formats}.
 */
public class SatchelsDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();

        pack.addProvider(SatchelsRecipeProvider::new);
        pack.addProvider(SatchelsItemTagProvider::new);
        pack.addProvider(SatchelsLanguageProvider::new);
        pack.addProvider(SatchelsSoundDefinitionsProvider::new);
        pack.addProvider(SatchelsModelProvider::new);
        pack.addProvider(SatchelsAdvancementProvider::new);
    }
}