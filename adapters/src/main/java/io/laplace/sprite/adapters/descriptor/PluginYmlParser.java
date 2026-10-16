package io.laplace.sprite.adapters.descriptor;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.PlatformFamily;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * plugin.yml 解析（14.2）：Bukkit 家族与 Nukkit 共用同一份字段集。
 *
 * <p>差异点只有 API 版本：Bukkit 用标量 {@code api-version}，Nukkit 用列表 {@code api}，
 * 这里两处都读，合并进 {@link PluginDescriptor#apiVersions()}。</p>
 */
public class PluginYmlParser implements DescriptorParser {

    @Override
    public boolean supports(PlatformFamily family) {
        return switch (family) {
            case BUKKIT, SPIGOT, PURPUR, PUFFERFISH, FOLIA, NUKKIT, POWER_NUKKIT_X, GEYSER -> true;
            default -> false;
        };
    }

    @Override
    public PluginDescriptor parse(String content) {
        JsonNode node = JsonFields.yaml(content);
        String name = JsonFields.text(node, "name");
        String id = JsonFields.text(node, "id");
        List<String> apiVersions = new ArrayList<>(JsonFields.strings(node, "api-version"));
        apiVersions.addAll(JsonFields.strings(node, "api"));

        List<ArtifactDependency> dependencies = new ArrayList<>();
        JsonFields.strings(node, "depend").forEach(dep -> dependencies.add(ArtifactDependency.hard(dep)));
        JsonFields.strings(node, "softdepend").forEach(dep -> dependencies.add(ArtifactDependency.soft(dep)));
        JsonFields.strings(node, "loadbefore").forEach(dep -> dependencies.add(ArtifactDependency.reverse(dep)));

        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("name", JsonFields.raw(node, "name"));
        raw.put("main", JsonFields.raw(node, "main"));
        raw.put("version", JsonFields.raw(node, "version"));
        raw.put("api-version", JsonFields.raw(node, "api-version"));
        raw.put("load", JsonFields.raw(node, "load"));

        return new PluginDescriptor(id == null ? name : id, name, JsonFields.text(node, "version"),
                JsonFields.text(node, "main"), apiVersions, JsonFields.text(node, "load"), dependencies,
                JsonFields.bool(node, "folia-supported", false), null, "server", raw);
    }

    @Override
    public String format() {
        return "plugin.yml";
    }
}
