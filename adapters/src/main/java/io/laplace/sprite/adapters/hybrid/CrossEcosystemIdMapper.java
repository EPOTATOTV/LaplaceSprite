package io.laplace.sprite.adapters.hybrid;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.io.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 跨生态 ID 映射校验（14.3 组合适配层第三件事）：模组注册物品 ↔ Bukkit {@code Material} / {@code EntityType}。
 *
 * <p>混合端里模组注册的物品/实体最终要能被 Bukkit 侧按名字拿到，映射缺失或一 id 对多目标都会在
 * 混装时暴露成运行期异常，这里在装配阶段先查出来。</p>
 */
public final class CrossEcosystemIdMapper {

    private static final String DEFAULT_RESOURCE = "/hybrid/item-material-map.json";

    /** 映射目标类型。 */
    public enum TargetKind {
        ITEM,
        ENTITY
    }

    /** 一条映射：模组注册 id → Bukkit 名称。 */
    public record IdMapping(String registryId, String bukkitName, TargetKind kind) {
    }

    /** 校验问题。 */
    public enum Code {
        /** 注册 id 在映射表里没有对应项。 */
        UNMAPPED,
        /** 同一注册 id 对到多个 Bukkit 目标，无法确定用哪个。 */
        AMBIGUOUS
    }

    /** 单条问题。 */
    public record MappingViolation(String registryId, Code code, String detail) {
    }

    /** 校验报告。 */
    public record MappingReport(int total, int mapped, List<MappingViolation> violations) {

        public MappingReport {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }

        public boolean clean() {
            return violations.isEmpty();
        }
    }

    private final List<IdMapping> mappings;

    public CrossEcosystemIdMapper(List<IdMapping> mappings) {
        this.mappings = List.copyOf(mappings);
    }

    /** 装载内置映射表。 */
    public static CrossEcosystemIdMapper defaultMapper() {
        return new CrossEcosystemIdMapper(loadDefaultMappings());
    }

    public List<IdMapping> mappings() {
        return mappings;
    }

    /** 表内自检：找出「一 id 对多目标」的歧义登记。 */
    public List<MappingViolation> audit() {
        Map<String, Set<String>> targets = new LinkedHashMap<>();
        for (IdMapping mapping : mappings) {
            targets.computeIfAbsent(mapping.registryId(), key -> new LinkedHashSet<>())
                    .add(mapping.bukkitName());
        }
        List<MappingViolation> violations = new ArrayList<>();
        targets.forEach((registryId, names) -> {
            if (names.size() > 1) {
                violations.add(new MappingViolation(registryId, Code.AMBIGUOUS,
                        "映射表内对到多个 Bukkit 目标: " + String.join(", ", names)));
            }
        });
        return List.copyOf(violations);
    }

    /** 校验给定的注册 id 列表。 */
    public List<MappingViolation> validate(List<String> registryIds) {
        Map<String, Set<String>> targets = targets();
        List<MappingViolation> violations = new ArrayList<>();
        for (String registryId : registryIds) {
            Set<String> names = targets.get(registryId);
            if (names == null) {
                violations.add(new MappingViolation(registryId, Code.UNMAPPED, "映射表内没有该注册 id"));
            } else if (names.size() > 1) {
                violations.add(new MappingViolation(registryId, Code.AMBIGUOUS,
                        "对到多个 Bukkit 目标: " + String.join(", ", names)));
            }
        }
        return List.copyOf(violations);
    }

    /** 校验并汇总。 */
    public MappingReport report(List<String> registryIds) {
        Map<String, Set<String>> targets = targets();
        List<MappingViolation> violations = validate(registryIds);
        int mapped = (int) registryIds.stream()
                .filter(targets::containsKey)
                .filter(id -> targets.get(id).size() == 1)
                .count();
        return new MappingReport(registryIds.size(), mapped, violations);
    }

    private Map<String, Set<String>> targets() {
        Map<String, Set<String>> targets = new LinkedHashMap<>();
        for (IdMapping mapping : mappings) {
            targets.computeIfAbsent(mapping.registryId(), key -> new LinkedHashSet<>())
                    .add(mapping.bukkitName());
        }
        return targets;
    }

    private static List<IdMapping> loadDefaultMappings() {
        try (InputStream stream = CrossEcosystemIdMapper.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("缺少映射资源: " + DEFAULT_RESOURCE);
            }
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode root = Json.jsonMapper().readTree(content);
            List<IdMapping> mappings = new ArrayList<>();
            collect(root.path("items"), TargetKind.ITEM, mappings);
            collect(root.path("entities"), TargetKind.ENTITY, mappings);
            return List.copyOf(mappings);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void collect(JsonNode array, TargetKind kind, List<IdMapping> out) {
        if (!array.isArray()) {
            return;
        }
        for (JsonNode node : array) {
            String registry = node.path("registry").asText(null);
            String bukkit = node.path("bukkit").asText(null);
            if (registry == null || bukkit == null) {
                continue;
            }
            out.add(new IdMapping(registry, bukkit, kind));
        }
    }

    /** 只读视图，供报告核对登记项数。 */
    public Map<TargetKind, Integer> countsByKind() {
        Map<TargetKind, Integer> counts = new LinkedHashMap<>();
        for (TargetKind kind : TargetKind.values()) {
            counts.put(kind, (int) mappings.stream().filter(mapping -> mapping.kind() == kind).count());
        }
        return Collections.unmodifiableMap(counts);
    }
}
