package io.laplace.sprite.adapters.descriptor;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.DependencySemantics;
import io.laplace.sprite.core.model.PlatformFamily;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * plugin.json 解析（14.2 Allay）。
 *
 * <p>版本字段允许 NPM / CocoaPods / IVY 风格的区间写法，这里只做原样保留，不做区间求解。</p>
 */
public final class AllayPluginJsonParser implements DescriptorParser {

    @Override
    public boolean supports(PlatformFamily family) {
        return family == PlatformFamily.ALLAY;
    }

    @Override
    public PluginDescriptor parse(String content) {
        JsonNode node = JsonFields.json(content);
        String name = JsonFields.text(node, "name");
        List<ArtifactDependency> dependencies = new ArrayList<>();
        JsonNode declared = node.path("dependencies");
        if (declared.isArray()) {
            for (JsonNode item : declared) {
                if (item.isValueNode()) {
                    dependencies.add(ArtifactDependency.hard(item.asText()));
                    continue;
                }
                String id = JsonFields.text(item, "name");
                if (id == null) {
                    continue;
                }
                String range = JsonFields.text(item, "version");
                boolean optional = JsonFields.bool(item, "optional", false);
                dependencies.add(new ArtifactDependency(id,
                        optional ? DependencySemantics.SOFT : DependencySemantics.HARD, range, optional));
            }
        }
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("name", JsonFields.raw(node, "name"));
        raw.put("entrance", JsonFields.raw(node, "entrance"));
        raw.put("version", JsonFields.raw(node, "version"));
        raw.put("authors", JsonFields.raw(node, "authors"));

        return new PluginDescriptor(name, name, JsonFields.text(node, "version"),
                JsonFields.text(node, "entrance"), List.of(), null, dependencies, false, null, "server", raw);
    }

    @Override
    public String format() {
        return "plugin.json";
    }
}
