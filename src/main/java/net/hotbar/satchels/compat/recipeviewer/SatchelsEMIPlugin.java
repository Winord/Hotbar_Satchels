package net.hotbar.satchels.compat.recipeviewer;

import dev.emi.emi.api.*;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;

import java.util.List;
import java.util.function.Consumer;

/**
 * EMI plugin: registers an exclusion area so EMI's recipe grid doesn't render over the
 * satchel equipment slot.
 * <p>
 * Registered via {@link EmiRegistry#addGenericExclusionArea}, NOT
 * {@link EmiRegistry#addExclusionArea(Class, EmiExclusionArea)}. Unlike JEI's
 * {@code IGuiContainerHandler} registration (which matches the registered class against the
 * open screen via an {@code isInstance}-style check, so a base-class registration covers every
 * subclass), EMI's {@code addExclusionArea(Class, ...)} stores handlers in a
 * {@code Map<Class<?>, List<EmiExclusionArea<?>>>} keyed by the exact class passed in, and looks
 * them up at render time with {@code fromClass.containsKey(screen.getClass())} — an exact-class
 * match, no walk up the hierarchy. Registering against {@code AbstractContainerScreen.class}
 * there only fires if the open screen's runtime class is literally
 * {@code AbstractContainerScreen} itself, which no real screen is ({@code InventoryScreen} and
 * every modded container screen are subclasses with their own {@code getClass()}) — so it never
 * matched in Survival either once switched from the old {@code InventoryScreen.class}
 * registration, let alone in other Allowed Menus. This was confirmed by decompiling
 * {@code dev.emi.emi.registry.EmiExclusionAreas} / {@code EmiRegistryImpl} from the EMI jar.
 * <p>
 * {@code addGenericExclusionArea} instead adds to a separate {@code generic} list that EMI
 * checks for every open screen regardless of class, which is what we actually want here —
 * combined with {@link SatchelSlotExclusionArea#getGuiExtraAreas} already safely no-oping
 * ({@code List.of()}) for any screen whose menu has no {@code SatchelEquipmentSlot}, so nothing
 * further needs to be gated.
 * <p>
 * Like JEI, EMI on Fabric finds its plugins through a {@code fabric.mod.json} entrypoint list
 * ({@code "emi"}, see EMI's own {@code fabric.mod.json}: {@code VanillaPlugin} is registered the
 * same way) rather than by scanning the classpath for {@link EmiEntrypoint} at runtime — there's
 * no annotation processor bundled with the EMI API jar to generate that automatically from the
 * annotation (no {@code META-INF/services/javax.annotation.processing.Processor} in it, and
 * {@code build.gradle} doesn't register one either). Without the explicit {@code "emi"} entry in
 * our {@code fabric.mod.json}, this class is never instantiated, so {@link #register} is never
 * called and the exclusion area silently does nothing. The {@link EmiEntrypoint} annotation
 * itself is harmless to keep (it's how NeoForge's classpath-scanning discovers it there) but on
 * Fabric it does nothing on its own.
 */
@EmiEntrypoint
public class SatchelsEMIPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addGenericExclusionArea(new SatchelsEMIExclusionArea());
    }

    private static class SatchelsEMIExclusionArea implements EmiExclusionArea<Screen> {
        @Override
        public void addExclusionArea(Screen screen, Consumer<Bounds> consumer) {
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
                return;
            }
            List<Rect2i> rects = SatchelSlotExclusionArea.getGuiExtraAreas(containerScreen);
            for (Rect2i r : rects) {
                consumer.accept(
                        new Bounds(r.getX(), r.getY(), r.getWidth(), r.getHeight())
                );
            }
        }
    }
}
