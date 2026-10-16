package io.laplace.sprite.adapters.descriptor;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.DependencySemantics;
import io.laplace.sprite.core.model.PlatformFamily;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code META-INF/sponge_plugins.json} 解析（14.3 Sponge 系）。
 *
 * <p>API 8 起的形态：顶层 {@code loader} 描述 Sponge 加载器，{@code plugins} 数组里每个插件带
 * {@code id / entrypoint / dependencies}；{@code @Plugin} 注解元数据已移除，所有声明都在这份文件里。</p>
 *
 * <p>{@code dependencies[].load-order} 映射成依赖三语义：{@code BEFORE} 表示本插件要先于依赖装载，
 * 记成反向边；缺省与 {@code AFTER} 表示依赖先装载，按 {@code optional} 落硬或软。</p>
 */
public final class SpongePluginsJsonParser implements DescriptorParser {

    private static final String BEFORE = "BEFORE";

    @Override
    public boolean supports(PlatformFamily family) {
        return family.isSponge();
    }

    @Override
    public PluginDescriptor parse(String content) {
        JsonNode root = JsonFields.json(content);
        JsonNode plugin = firstPlugin(root);
        JsonNode loader = root.path("loader");

        List<ArtifactDependency> dependencies = new ArrayList<>();
        JsonNode declared = plugin.path("dependencies");
        if (declared.isArray()) {
            for (JsonNode item : declared) {
                String id = JsonFields.text(item, "id");
                if (id == null) {
                    continue;
                }
                boolean optional = JsonFields.bool(item, "optional", false);
                DependencySemantics semantics = isBefore(item)
                        ? DependencySemantics.REVERSE
                        : (optional ? DependencySemantics.SOFT : DependencySemantics.HARD);
                dependencies.add(new ArtifactDependency(id, semantics, JsonFields.text(item, "version"),
                        optional));
            }
        }

        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("loaderName", JsonFields.raw(loader, "name"));
        raw.put("loaderVersion", JsonFields.raw(loader, "version"));
        raw.put("pluginId", JsonFields.raw(plugin, "id"));
        raw.put("description", JsonFields.raw(plugin, "description"));
        raw.put("loadOrder", firstLoadOrder(declared));
        raw.put("dependencyCount", Integer.toString(dependencies.size()));

        String id = JsonFields.text(plugin, "id");
        return new PluginDescriptor(id, JsonFields.text(plugin, "name"), JsonFields.text(plugin, "version"),
                JsonFields.text(plugin, "entrypoint"), List.of(), null, dependencies, false, null,
                "both", raw);
    }

    /** 取第一个插件；顶层没有 plugins 数组时把根节点当作单个插件。 */
    private static JsonNode firstPlugin(JsonNode root) {
        JsonNode plugins = root.path("plugins");
        if (plugins.isArray() && !plugins.isEmpty()) {
            return plugins.get(0);
        }
        return root;
    }

    private static boolean isBefore(JsonNode dependency) {
        String loadOrder = JsonFields.text(dependency, "load-order");
        return loadOrder != null && BEFORE.equals(loadOrder.toUpperCase(Locale.ROOT));
    }

    private static String firstLoadOrder(JsonNode dependencies) {
        if (!dependencies.isArray()) {
            return "";
        }
        for (JsonNode item : dependencies) {
            String loadOrder = JsonFields.text(item, "load-order");
            if (loadOrder != null && !loadOrder.isBlank()) {
                return loadOrder;
            }
        }
        return "";
    }

    @Override
    public String format() {
        return "META-INF/sponge_plugins.json";
    }
}
