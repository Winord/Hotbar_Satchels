package net.hotbar.satchels.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Entry point for the Fabric Data Generation API (the NeoForge equivalent was
 * {@code SatchelsDataGeneration} + {@code GatherDataEvent}, which is where the static
 * {@code data/*} files originally came from).
 * <p>
 * Registered in {@code fabric.mod.json} under the {@code "fabric-datagen"} key — a separate
 * entrypoint from the usual {@code "main"}/{@code "client"} ones, only invoked by
 * {@code ./gradlew runDatagen}, not during a normal mod run.
 * <p>
 * <b>Generated here:</b> {@link SatchelsRecipeProvider} (the recipe plus its automatic
 * unlock advancement), {@link SatchelsItemTagProvider} (the {@code minecraft:dyeable} and
 * {@code satchels:satchels} tags, plus the cross-mod {@code accessories:satchel} tag),
 * {@link SatchelsLanguageProvider} ({@code assets/satchels/lang/en_us.json} — only the source
 * language is generated; {@code fr_ca.json}/{@code fr_fr.json} stay as hand-written community
 * translations, see the {@link SatchelsLanguageProvider} javadoc for details),
 * {@link SatchelsSoundDefinitionsProvider} ({@code assets/satchels/sounds.json}, a
 * {@link net.minecraft.data.DataProvider} written from scratch since Fabric API has no
 * equivalent of NeoForge's {@code SoundDefinitionsProvider} — see its javadoc for details), and
 * {@link SatchelsModelProvider} ({@code assets/satchels/models/item/satchel.json}, a two-layer
 * {@code minecraft:item/generated} model; {@code satchel_worn.json} stays static since it's a
 * custom Blockbench geometry outside what {@code ItemModelGenerators} can produce — see its
 * javadoc for details).
 * <p>
 * <b>Deliberately left as static json instead:</b>
 * <ul>
 *   <li>{@code data/satchels/accessories/{slot,entity}/satchel.json} and
 *       {@code data/accessories/accessories/group/chest.json} — Accessories does have its own
 *       Fabric datagen API ({@code SlotDataProvider}/{@code EntityBindingProvider}/
 *       {@code GroupDataProvider} in {@code io.wispforest.accessories.api.data.providers.*}),
 *       but as of {@code 1.1.0-beta.53+1.21.1} its own official usage example
 *       ({@code AccessoriesDataGenEntrypoint} in the library itself) contains an
 *       {@code if (true) return;} — i.e. even the library's own authors don't consider this
 *       path production-ready yet. Our static json has already been checked against the real
 *       schema ({@code SlotTypeLoader}/{@code EntitySlotLoader}) during the port and confirmed
 *       working in a real build, so swapping it for an unstable beta API isn't worth the risk
 *       right now. Worth revisiting once Accessories' datagen providers stabilize.</li>
 *   <li>{@code satchel_worn.json} — see {@link SatchelsModelProvider}'s javadoc.</li>
 * </ul>
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
