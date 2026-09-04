package net.hotbar.satchels.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.hotbar.satchels.Satchels;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Generates the "Satchels" advancement tab:
 *
 * <pre>
 *   root (tab, silent)
 *   └── satchel_golden  (task)    – parent: root
 *       └── satchel_diamond (goal)  – parent: golden
 *           └── satchel_netherite (challenge) – parent: diamond
 *               └── satchel_full (goal) – parent: netherite
 * </pre>
 *
 * The chain controls display order in the GUI tree. Obtaining each advancement is still
 * independent — each fires on its own {@code inventory_changed} / custom criterion, with
 * no prerequisite on its parent being awarded first.
 *
 * <p>{@code satchel_full} fires via the custom {@link net.hotbar.satchels.ModCriteria#SATCHEL_FULL}
 * trigger, which is called from {@link net.hotbar.satchels.content.satchel.SatchelInventory#setChanged()}
 * whenever a Netherite Satchel's inventory changes and all 9 slots hold a full stack
 * (count == maxStackSize, or count == 1 for non-stackable items).
 */
public class SatchelsAdvancementProvider implements DataProvider {
    private final FabricDataOutput output;

    public SatchelsAdvancementProvider(FabricDataOutput output) {
        this.output = output;
    }

    @Override
    public @NotNull CompletableFuture<?> run(@NotNull CachedOutput cachedOutput) {
        Path base = output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(Satchels.ID)
                .resolve("advancement");

        CompletableFuture<?> root = DataProvider.saveStable(
                cachedOutput, buildRoot(), base.resolve("root.json"));

        CompletableFuture<?> golden = DataProvider.saveStable(
                cachedOutput,
                buildTierAdvancement(
                        "satchels:root",
                        "satchels:satchel_golden",
                        "task",
                        "satchels.advancements.golden.title",
                        "satchels.advancements.golden.description",
                        "satchels:satchel_golden"
                ),
                base.resolve("satchel_golden.json"));

        CompletableFuture<?> diamond = DataProvider.saveStable(
                cachedOutput,
                buildTierAdvancement(
                        "satchels:satchel_golden",   // ← parent is golden, not root
                        "satchels:satchel_diamond",
                        "goal",
                        "satchels.advancements.diamond.title",
                        "satchels.advancements.diamond.description",
                        "satchels:satchel_diamond"
                ),
                base.resolve("satchel_diamond.json"));

        CompletableFuture<?> netherite = DataProvider.saveStable(
                cachedOutput,
                buildTierAdvancement(
                        "satchels:satchel_diamond",  // ← parent is diamond
                        "satchels:satchel_netherite",
                        "challenge",
                        "satchels.advancements.netherite.title",
                        "satchels.advancements.netherite.description",
                        "satchels:satchel_netherite"
                ),
                base.resolve("satchel_netherite.json"));

        CompletableFuture<?> full = DataProvider.saveStable(
                cachedOutput, buildSatchelFullAdvancement(), base.resolve("satchel_full.json"));

        return CompletableFuture.allOf(root, golden, diamond, netherite, full);
    }

    private static JsonObject buildRoot() {
        JsonObject display = new JsonObject();

        JsonObject icon = new JsonObject();
        icon.addProperty("id", "satchels:satchel_golden");
        display.add("icon", icon);

        JsonObject title = new JsonObject();
        title.addProperty("translate", "satchels.advancements.root.title");
        display.add("title", title);

        JsonObject description = new JsonObject();
        description.addProperty("translate", "satchels.advancements.root.description");
        display.add("description", description);

        display.addProperty("background", "minecraft:textures/gui/advancements/backgrounds/adventure.png");
        display.addProperty("frame", "task");
        display.addProperty("show_toast", false);
        display.addProperty("announce_to_chat", false);
        display.addProperty("hidden", false);

        // Three separate criteria, OR-ed together via requirements:
        // requirements = [["has_golden_satchel"], ["has_diamond_satchel"], ["has_netherite_satchel"]]
        // means the advancement fires when ANY ONE of them is met.
        JsonObject criteria = new JsonObject();
        criteria.add("has_golden_satchel",   inventoryChangedCriterion("satchels:satchel_golden"));
        criteria.add("has_diamond_satchel",  inventoryChangedCriterion("satchels:satchel_diamond"));
        criteria.add("has_netherite_satchel", inventoryChangedCriterion("satchels:satchel_netherite"));

        // Each inner array is one AND-group; outer array OR-s them together.
        JsonArray requirements = new JsonArray();
        for (String name : new String[]{"has_golden_satchel", "has_diamond_satchel", "has_netherite_satchel"}) {
            JsonArray group = new JsonArray();
            group.add(name);
            requirements.add(group);
        }

        JsonObject root = new JsonObject();
        root.add("display", display);
        root.add("criteria", criteria);
        root.add("requirements", requirements);
        return root;
    }

    /**
     * @param parent   resource-location of the parent advancement (controls GUI tree position)
     * @param iconId   item registry id for the icon (e.g. {@code "satchels:satchel_golden"})
     * @param frame    {@code "task"}, {@code "goal"}, or {@code "challenge"}
     * @param titleKey lang key for the title
     * @param descKey  lang key for the description
     * @param itemId   item id for the {@code inventory_changed} criterion
     */
    private static JsonObject buildTierAdvancement(
            String parent, String iconId, String frame,
            String titleKey, String descKey, String itemId
    ) {
        JsonObject display = new JsonObject();

        JsonObject icon = new JsonObject();
        icon.addProperty("id", iconId);
        display.add("icon", icon);

        JsonObject title = new JsonObject();
        title.addProperty("translate", titleKey);
        display.add("title", title);

        JsonObject description = new JsonObject();
        description.addProperty("translate", descKey);
        display.add("description", description);

        display.addProperty("frame", frame);
        display.addProperty("show_toast", true);
        display.addProperty("announce_to_chat", true);
        display.addProperty("hidden", false);

        String criterionName = "has_" + itemId.replace("satchels:", "");
        JsonObject criteria = new JsonObject();
        criteria.add(criterionName, inventoryChangedCriterion(itemId));

        JsonArray req0 = new JsonArray();
        req0.add(criterionName);
        JsonArray requirements = new JsonArray();
        requirements.add(req0);

        JsonObject advancement = new JsonObject();
        advancement.addProperty("parent", parent);
        advancement.add("display", display);
        advancement.add("criteria", criteria);
        advancement.add("requirements", requirements);
        return advancement;
    }

    /**
     * Builds the "Packed to the Brim" advancement — fires when all 9 slots of a Netherite
     * Satchel hold full stacks. Uses the custom {@code satchels:satchel_full} trigger
     * (see {@link net.hotbar.satchels.ModCriteria}).
     */
    private static JsonObject buildSatchelFullAdvancement() {
        JsonObject display = new JsonObject();

        JsonObject icon = new JsonObject();
        icon.addProperty("id", "satchels:satchel_netherite");
        display.add("icon", icon);

        JsonObject title = new JsonObject();
        title.addProperty("translate", "satchels.advancements.satchel_full.title");
        display.add("title", title);

        JsonObject description = new JsonObject();
        description.addProperty("translate", "satchels.advancements.satchel_full.description");
        display.add("description", description);

        display.addProperty("frame", "goal");
        display.addProperty("show_toast", true);
        display.addProperty("announce_to_chat", true);
        display.addProperty("hidden", false);

        // This advancement is granted programmatically via PlayerAdvancements.award() in
        // ModCriteria.triggerSatchelFull() — called from SatchelInventory.setChanged()
        // whenever all 9 netherite satchel slots hold a full stack. Using
        // "minecraft:impossible" means the criterion can never fire on its own, so the
        // advancement is only awarded by our explicit award() call.
        JsonObject triggerObj = new JsonObject();
        triggerObj.addProperty("trigger", "minecraft:impossible");
        JsonObject criteria = new JsonObject();
        criteria.add("satchel_is_full", triggerObj);

        JsonArray req0 = new JsonArray();
        req0.add("satchel_is_full");
        JsonArray requirements = new JsonArray();
        requirements.add(req0);

        JsonObject advancement = new JsonObject();
        advancement.addProperty("parent", "satchels:satchel_netherite");
        advancement.add("display", display);
        advancement.add("criteria", criteria);
        advancement.add("requirements", requirements);
        return advancement;
    }

    /**
     * Builds an {@code inventory_changed} criterion that fires when the player has the given
     * item in their inventory.
     *
     * <p>In MC 1.21.1 the inner item-condition field is {@code "items"} (the item id), NOT
     * {@code "id"} — using {@code "id"} silently ignores the condition and fires on any
     * inventory change.
     */
    private static JsonObject inventoryChangedCriterion(String itemId) {
        JsonObject itemCondition = new JsonObject();
        itemCondition.addProperty("items", itemId);

        JsonArray items = new JsonArray();
        items.add(itemCondition);

        JsonObject predicate = new JsonObject();
        predicate.add("items", items);

        JsonObject criterion = new JsonObject();
        criterion.addProperty("trigger", "minecraft:inventory_changed");
        criterion.add("conditions", predicate);
        return criterion;
    }

    @Override
    public @NotNull String getName() {
        return "Satchel Advancements";
    }
}
