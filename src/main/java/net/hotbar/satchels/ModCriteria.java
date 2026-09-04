package net.hotbar.satchels;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

/**
 * Utility for granting custom Satchels advancements that have no vanilla criterion trigger.
 *
 * <p>Instead of registering a custom {@code CriterionTrigger} (whose API differs across
 * MC versions), we resolve the advancement by ID and call
 * {@link PlayerAdvancements#award} directly — the same mechanism vanilla uses internally
 * after a trigger fires. This is stable across MC versions and requires no registration.
 */
public class ModCriteria {

    private static final ResourceLocation SATCHEL_FULL_ID = Satchels.at("satchel_full");

    /**
     * Awards the {@code satchels:satchel_full} advancement to {@code player} if they
     * haven't already earned it. Safe to call every tick — {@link PlayerAdvancements#award}
     * is a no-op when the advancement is already done.
     */
    public static void triggerSatchelFull(ServerPlayer player) {
        AdvancementHolder holder = player.server
                .getAdvancements()
                .get(SATCHEL_FULL_ID);
        if (holder == null) return; // advancement not loaded (datagen not run yet, etc.)
        player.getAdvancements().award(holder, "satchel_is_full");
    }
}