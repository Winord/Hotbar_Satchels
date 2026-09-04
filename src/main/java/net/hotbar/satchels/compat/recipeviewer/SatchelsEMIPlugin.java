package net.hotbar.satchels.compat.recipeviewer;

import dev.emi.emi.api.*;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.List;
import java.util.function.Consumer;

/**
 * EMI plugin: registers an exclusion area so EMI's recipe grid doesn't render over the
 * satchel equipment slot.
 * <p>
 * Like JEI, EMI on Fabric finds its plugins through a {@code fabric.mod.json} entrypoint list
 * ({@code "emi"}, see EMI's own {@code fabric.mod.json}: {@code VanillaPlugin} is registered
 * the same way) rather than by scanning the classpath for {@link EmiEntrypoint} at runtime —
 * there's no annotation processor bundled with the EMI API jar to generate that automatically
 * from the annotation (no {@code META-INF/services/javax.annotation.processing.Processor} in
 * it, and {@code build.gradle} doesn't register one either). Without the explicit
 * {@code "emi"} entry in our {@code fabric.mod.json}, this class is never instantiated, so
 * {@link #register} is never called and the exclusion area silently does nothing — which is
 * what was happening. The {@link EmiEntrypoint} annotation itself is harmless to keep (it's
 * how NeoForge's classpath-scanning discovers it there) but on Fabric it does nothing on its
 * own.
 */
@EmiEntrypoint
public class SatchelsEMIPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addExclusionArea(InventoryScreen.class, new SatchelsEMIExclusionArea<>());
    }

    private static class SatchelsEMIExclusionArea<T extends AbstractContainerScreen<InventoryMenu>> implements EmiExclusionArea<T> {
        @Override
        public void addExclusionArea(T screen, Consumer<Bounds> consumer) {
            List<Rect2i> rects = SatchelSlotExclusionArea.getGuiExtraAreas(screen);
            for (Rect2i r : rects) {
                consumer.accept(
                        new Bounds(r.getX(), r.getY(), r.getWidth(), r.getHeight())
                );
            }
        }
    }
}
