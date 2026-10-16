package io.laplace.sprite.adapters.descriptor;

import io.laplace.sprite.adapters.support.SimpleToml;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.DependencySemantics;
import io.laplace.sprite.core.model.PlatformFamily;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code META-INF/mods.toml} 解析（14.3 Forge 传统分支）。
 *
 * <p>mod 元数据在 {@code [[mods]]}，依赖在 {@code [[dependencies.<modId>]]}，
 * 每条依赖带 mandatory / versionRange / ordering / side。</p>
 */
public class ForgeModsTomlParser implements DescriptorParser {

    @Override
    public boolean supports(PlatformFamily family) {
        return family == PlatformFamily.FORGE;
    }

    @Override
    public PluginDescriptor parse(String content) {
        SimpleToml toml = SimpleToml.parse(content);
        Map<String, String> mod = toml.array("mods").isEmpty() ? Map.of() : toml.array("mods").get(0);
        String modId = mod.getOrDefault("modId", "");
        String loaderVersion = toml.scalar("loaderVersion");
        String modLoader = toml.scalar("modLoader");

        List<ArtifactDependency> dependencies = new ArrayList<>();
        for (Map<String, String> declared : toml.array("dependencies." + modId)) {
            String id = declared.get("modId");
            if (id == null) {
                continue;
            }
            boolean mandatory = !"false".equalsIgnoreCase(declared.getOrDefault("mandatory", "true"));
            dependencies.add(new ArtifactDependency(id,
                    mandatory ? DependencySemantics.HARD : DependencySemantics.SOFT,
                    declared.get("versionRange"), !mandatory));
        }

        List<Map<String, String>> declaredDependencies = toml.array("dependencies." + modId);
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("modLoader", orEmpty(modLoader));
        raw.put("loaderVersion", orEmpty(loaderVersion));
        raw.put("modId", modId);
        raw.put("version", orEmpty(mod.get("version")));
        raw.put("ordering", orderingOf(declaredDependencies));
        raw.put("side", sideOf(declaredDependencies));

        return new PluginDescriptor(modId, mod.getOrDefault("displayName", modId), mod.get("version"), null,
                loaderVersion == null ? List.of() : List.of(loaderVersion), null, dependencies, false, null,
                sideOf(declaredDependencies), raw);
    }

    static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 依赖里第一条 ordering；没有就返回 NONE。 */
    static String orderingOf(List<Map<String, String>> dependencies) {
        for (Map<String, String> dependency : dependencies) {
            String ordering = dependency.get("ordering");
            if (ordering != null && !ordering.isBlank()) {
                return ordering;
            }
        }
        return "NONE";
    }

    /** 依赖里第一条 side；没有就返回 BOTH。 */
    static String sideOf(List<Map<String, String>> dependencies) {
        for (Map<String, String> dependency : dependencies) {
            String side = dependency.get("side");
            if (side != null && !side.isBlank()) {
                return side.toLowerCase(Locale.ROOT);
            }
        }
        return "both";
    }

    @Override
    public String format() {
        return "META-INF/mods.toml";
    }
}
