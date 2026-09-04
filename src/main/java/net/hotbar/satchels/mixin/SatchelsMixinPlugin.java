package net.hotbar.satchels.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Conditionally enables compat mixins based on whether the target companion mod is loaded.
 * <p>
 * Compat mixins must live under the package convention
 * {@code net.hotbar.satchels.mixin.compat.<modid>.*}; the mod id is parsed out via
 * {@code mixinClassName.split("\\.")[5]}. If this package's nesting depth ever changes
 * (e.g. an extra sub-package is inserted), that index needs to be recalculated.
 */
public class SatchelsMixinPlugin implements IMixinConfigPlugin {
    private final Map<String, Boolean> modLoadedCache = new ConcurrentHashMap<>();

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return "";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith("net.hotbar.satchels.mixin.compat.")) {
            final String[] parts = mixinClassName.split("\\.");
            if (parts.length < 6) {
                return true;
            }

            final String modId = parts[5];
            return this.modLoadedCache.computeIfAbsent(modId, FabricLoader.getInstance()::isModLoaded);
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
