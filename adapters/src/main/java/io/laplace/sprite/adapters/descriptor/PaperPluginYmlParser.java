package io.laplace.sprite.adapters.descriptor;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.DependencySemantics;
import io.laplace.sprite.core.model.PlatformFamily;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * paper-plugin.yml 解析（14.2 Paper）。
 *
 * <p>与 plugin.yml 的差异：显式声明 {@code folia-supported}，依赖按
 * {@code dependencies.<section>.<pluginId>} 的嵌套结构给出，{@code required} 决定硬/软。</p>
 */
public final class PaperPluginYmlParser extends PluginYmlParser {

    @Override
    public boolean supports(PlatformFamily family) {
        return family == PlatformFamily.PAPER;
    }

    @Override
    public PluginDescriptor parse(String content) {
        PluginDescriptor base = super.parse(content);
        JsonNode dependencies = JsonFields.yaml(content).path("dependencies");
        if (!dependencies.isObject()) {
            return base;
        }
        List<ArtifactDependency> declared = new ArrayList<>(base.dependencies());
        Iterator<Map.Entry<String, JsonNode>> sections = dependencies.fields();
        while (sections.hasNext()) {
            JsonNode section = sections.next().getValue();
            if (!section.isObject()) {
                continue;
            }
            Iterator<Map.Entry<String, JsonNode>> plugins = section.fields();
            while (plugins.hasNext()) {
                Map.Entry<String, JsonNode> plugin = plugins.next();
                boolean required = plugin.getValue().path("required").asBoolean(true);
                String range = JsonFields.text(plugin.getValue(), "version");
                declared.add(new ArtifactDependency(plugin.getKey(),
                        required ? DependencySemantics.HARD : DependencySemantics.SOFT, range, !required));
            }
        }
        return new PluginDescriptor(base.id(), base.name(), base.version(), base.mainClass(),
                base.apiVersions(), base.loadPhase(), declared, base.foliaSupported(), base.environment(),
                base.side(), base.rawFields());
    }

    @Override
    public String format() {
        return "paper-plugin.yml";
    }
}
