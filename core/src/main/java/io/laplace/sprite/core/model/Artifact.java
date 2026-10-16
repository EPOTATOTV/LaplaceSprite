package io.laplace.sprite.core.model;

import java.util.List;
import java.util.Map;

/**
 * 已解析产物（依赖闭包中的一员）。
 *
 * @param id          唯一 id
 * @param name        展示名
 * @param version     解析后的具体版本
 * @param source      来源
 * @param location    Maven 坐标或下载 URL
 * @param sha512      内容哈希（装配时校验）
 * @param role        角色
 * @param dependencies 依赖声明
 * @param metadata    源特定元数据
 */
public record Artifact(
        String id,
        String name,
        String version,
        ArtifactSource source,
        String location,
        String sha512,
        ArtifactRole role,
        List<ArtifactDependency> dependencies,
        Map<String, String> metadata) {

    public Artifact {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public List<ArtifactDependency> dependencies(DependencySemantics semantics) {
        return dependencies.stream().filter(d -> d.semantics() == semantics).toList();
    }
}
