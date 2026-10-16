package io.laplace.sprite.adapters.descriptor;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.DependencySemantics;
import io.laplace.sprite.core.model.PlatformFamily;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * quilt.mod.json 解析（14.3 Quilt）。
 *
 * <p>元数据全部挂在 {@code quilt_loader} 块下：group/id/version/entrypoints/depends，
 * 顶层另有 mixin 清单。依赖项是对象数组，{@code optional} 决定硬/软。</p>
 */
public final class QuiltModJsonParser implements DescriptorParser {

    @Override
    public boolean supports(PlatformFamily family) {
        return family == PlatformFamily.QUILT;
    }

    @Override
    public PluginDescriptor parse(String content) {
        JsonNode node = JsonFields.json(content);
        JsonNode loader = node.path("quilt_loader");

        List<ArtifactDependency> dependencies = new ArrayList<>();
        JsonNode declared = loader.path("depends");
        if (declared.isArray()) {
            for (JsonNode item : declared) {
                String id = JsonFields.text(item, "id");
                if (id == null) {
                    continue;
                }
                boolean optional = JsonFields.bool(item, "optional", false);
                String range = JsonFields.text(item, "versions");
                dependencies.add(new ArtifactDependency(id,
                        optional ? DependencySemantics.SOFT : DependencySemantics.HARD, range, optional));
            }
        }

        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("schema_version", JsonFields.raw(node, "schema_version"));
        raw.put("group", JsonFields.raw(loader, "group"));
        raw.put("id", JsonFields.raw(loader, "id"));
        raw.put("mixin", String.join(",", JsonFields.strings(node, "mixin")));
        raw.put("entrypoints", JsonFields.raw(loader, "entrypoints"));

        String group = JsonFields.text(loader, "group");
        String id = JsonFields.text(loader, "id");
        String qualified = group == null ? id : group + ":" + id;

        return new PluginDescriptor(qualified, displayName(loader, id), JsonFields.text(loader, "version"),
                mainEntrypoint(loader), List.of(), null, dependencies, false, null, "both", raw);
    }

    private static String displayName(JsonNode loader, String fallback) {
        String name = JsonFields.text(loader.path("metadata"), "name");
        return name == null ? fallback : name;
    }

    private static String mainEntrypoint(JsonNode loader) {
        JsonNode entrypoints = loader.path("entrypoints");
        Iterator<Map.Entry<String, JsonNode>> fields = entrypoints.fields();
        while (fields.hasNext()) {
            JsonNode value = fields.next().getValue();
            if (value.isArray() && !value.isEmpty()) {
                // Quilt 入口点形如 "com.example.Mod::init"，只取类名部分。
                String raw = value.get(0).asText();
                int marker = raw.indexOf("::");
                return marker < 0 ? raw : raw.substring(0, marker);
            }
        }
        return null;
    }

    @Override
    public String format() {
        return "quilt.mod.json";
    }
}
