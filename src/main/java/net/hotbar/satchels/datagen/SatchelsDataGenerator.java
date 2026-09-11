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
 * {@code satchels:satchels} tags, plus the cross-mod {@code trinkets:chest/satchel} slot tag —
 * {@code accessories:satchel} on the 1.21.1 branch, replaced on 26.x since Trinkets Updated
 * replaces Accessories, see {@code satchels-port-decisions-26_1.md} §3),
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
 * <b>Deliberately left as static json instead (26.x branch):</b>
 * <ul>
 *   <li>{@code data/trinkets/entities/player.json} and {@code data/trinkets/slots/chest/satchel.json}
 *       — Trinkets Updated has no known stable Fabric datagen provider API (unlike its
 *       {@code tags/items/*} slot-tag files, which are just ordinary item tags and so are
 *       generated normally via {@link SatchelsItemTagProvider}). These two files are small,
 *       rarely change, and were checked against the real
 *       ({@code data/trinkets/entities/[id].json} / {@code data/trinkets/slots/[group]/[slot].json})
 *       schema documented at
 *       {@code github.com/emilyploszaj/trinkets/wiki/Trinkets-Data-Formats} — the format
 *       Trinkets Updated inherited from upstream Trinkets. Worth revisiting if Patbox's fork
 *       ever ships its own datagen provider.</li>
 *   <li>{@code satchel_worn.json} — see {@link SatchelsModelProvider}'s javadoc.</li>
 * </ul>
 * <p>
 * <b>1.21.1 branch note:</b> that branch instead ships static
 * {@code data/satchels/accessories/{slot,entity}/satchel.json} and
 * {@code data/accessories/accessories/group/chest.json} for Accessories (not present on 26.x —
 * see {@code satchels-port-decisions-26_1.md} §3 for why Accessories was dropped for this port).
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