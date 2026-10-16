package io.laplace.sprite.adapters.descriptor;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.PlatformFamily;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * bungee.yml 解析（14.2 BungeeCord / Waterfall 的 fallback 描述符）。
 */
public final class BungeeYmlParser implements DescriptorParser {

    @Override
    public boolean supports(PlatformFamily family) {
        return family == PlatformFamily.BUNGEECORD || family == PlatformFamily.WATERFALL;
    }

    @Override
    public PluginDescriptor parse(String content) {
        JsonNode node = JsonFields.yaml(content);
        String name = JsonFields.text(node, "name");
        List<ArtifactDependency> dependencies = new ArrayList<>();
        JsonFields.strings(node, "depend").forEach(dep -> dependencies.add(ArtifactDependency.hard(dep)));
        JsonFields.strings(node, "softdepend").forEach(dep -> dependencies.add(ArtifactDependency.soft(dep)));

        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("name", JsonFields.raw(node, "name"));
        raw.put("main", JsonFields.raw(node, "main"));
        raw.put("version", JsonFields.raw(node, "version"));
        raw.put("author", JsonFields.raw(node, "author"));

        return new PluginDescriptor(name, name, JsonFields.text(node, "version"),
                JsonFields.text(node, "main"), List.of(), null, dependencies, false, null, "proxy", raw);
    }

    @Override
    public String format() {
        return "bungee.yml";
    }
}
