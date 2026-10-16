package io.laplace.sprite.adapters.descriptor;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.PlatformFamily;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * velocity-plugin.json 解析（14.2 Velocity）。
 *
 * <p>该文件由 {@code @Plugin} 注解处理器生成，字段固定为 id / name / version / main / dependencies，
 * 依赖项是对象数组，{@code optional} 决定硬/软。</p>
 */
public final class VelocityDescriptorParser implements DescriptorParser {

    @Override
    public boolean supports(PlatformFamily family) {
        return family == PlatformFamily.VELOCITY;
    }

    @Override
    public PluginDescriptor parse(String content) {
        JsonNode node = JsonFields.json(content);
        List<ArtifactDependency> dependencies = new ArrayList<>();
        JsonNode declared = node.path("dependencies");
        if (declared.isArray()) {
            for (JsonNode item : declared) {
                String id = JsonFields.text(item, "id");
                if (id == null) {
                    continue;
                }
                boolean optional = JsonFields.bool(item, "optional", false);
                dependencies.add(optional ? ArtifactDependency.soft(id) : ArtifactDependency.hard(id));
            }
        }
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("id", JsonFields.raw(node, "id"));
        raw.put("name", JsonFields.raw(node, "name"));
        raw.put("version", JsonFields.raw(node, "version"));
        raw.put("main", JsonFields.raw(node, "main"));

        return new PluginDescriptor(JsonFields.text(node, "id"), JsonFields.text(node, "name"),
                JsonFields.text(node, "version"), JsonFields.text(node, "main"), List.of(), null,
                dependencies, false, null, "proxy", raw);
    }

    @Override
    public String format() {
        return "velocity-plugin.json";
    }
}
