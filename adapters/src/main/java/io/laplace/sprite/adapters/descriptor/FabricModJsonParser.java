package io.laplace.sprite.adapters.descriptor;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.DependencySemantics;
import io.laplace.sprite.core.model.PlatformFamily;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * fabric.mod.json 解析（14.3 Fabric）。
 *
 * <p>schemaVersion 1 的五个依赖层级映射到三语义：depends→HARD，recommends/suggests→SOFT，
 * breaks/conflicts→REVERSE。层级名写进 rawFields，报告里能看出原始声明。</p>
 */
public final class FabricModJsonParser implements DescriptorParser {

    @Override
    public boolean supports(PlatformFamily family) {
        return family == PlatformFamily.FABRIC;
    }

    @Override
    public PluginDescriptor parse(String content) {
        JsonNode node = JsonFields.json(content);
        int schema = node.path("schemaVersion").asInt(1);
        List<ArtifactDependency> dependencies = new ArrayList<>();
        collect(node, "depends", DependencySemantics.HARD, dependencies);
        collect(node, "recommends", DependencySemantics.SOFT, dependencies);
        collect(node, "suggests", DependencySemantics.SOFT, dependencies);
        collect(node, "breaks", DependencySemantics.REVERSE, dependencies);
        collect(node, "conflicts", DependencySemantics.REVERSE, dependencies);

        String environment = JsonFields.text(node, "environment");
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("schemaVersion", Integer.toString(schema));
        raw.put("id", JsonFields.raw(node, "id"));
        raw.put("environment", JsonFields.raw(node, "environment"));
        raw.put("entrypoints", JsonFields.raw(node, "entrypoints"));

        return new PluginDescriptor(JsonFields.text(node, "id"), JsonFields.text(node, "name"),
                JsonFields.text(node, "version"), mainEntrypoint(node), List.of(), null, dependencies,
                false, environment, side(environment), raw);
    }

    /** 五个层级都是「modId → 版本区间」的对象。 */
    private static void collect(JsonNode node, String level, DependencySemantics semantics,
                                List<ArtifactDependency> out) {
        JsonNode block = node.path(level);
        if (!block.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = block.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String range = entry.getValue().isValueNode() ? entry.getValue().asText() : null;
            out.add(new ArtifactDependency(entry.getKey(), semantics, range,
                    semantics == DependencySemantics.SOFT));
        }
    }

    private static String mainEntrypoint(JsonNode node) {
        JsonNode main = node.path("entrypoints").path("main");
        if (main.isArray() && !main.isEmpty()) {
            return main.get(0).asText();
        }
        return null;
    }

    private static String side(String environment) {
        if (environment == null) {
            return "both";
        }
        return switch (environment) {
            case "client" -> "client";
            case "server" -> "server";
            default -> "both";
        };
    }

    @Override
    public String format() {
        return "fabric.mod.json";
    }
}
