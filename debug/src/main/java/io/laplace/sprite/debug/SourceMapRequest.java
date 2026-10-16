package io.laplace.sprite.debug;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 源码映射请求（15.1）：对齐 Fabric genSources 思路，装配阶段先生成可读的 MC 源码——
 * 没有源码映射，断点打不进 MC 类。
 *
 * @param instanceId 调试实例 id
 * @param projectDir Loom 工程目录
 * @param sourceDir  生成源码的预期落点
 * @param gradleTask 可配置的 gradle 任务名
 */
public record SourceMapRequest(String instanceId, Path projectDir, Path sourceDir, String gradleTask) {

    public SourceMapRequest {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("实例 id 不能为空");
        }
        projectDir = Objects.requireNonNull(projectDir, "projectDir");
        sourceDir = Objects.requireNonNull(sourceDir, "sourceDir");
        if (gradleTask == null || gradleTask.isBlank()) {
            throw new IllegalArgumentException("gradle 任务名不能为空");
        }
    }

    public static SourceMapRequest of(String instanceId, Path projectDir) {
        return new SourceMapRequest(instanceId, projectDir,
                projectDir.resolve("build").resolve("laplace-mc-sources"), SourceMapGenerator.DEFAULT_TASK);
    }
}
