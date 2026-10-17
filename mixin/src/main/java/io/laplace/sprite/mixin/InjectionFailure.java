package io.laplace.sprite.mixin;

import java.util.Objects;

/**
 * 一次注入失败（15.11）：可能来自启动日志，也可能来自声明计数比对。
 *
 * @param modId       模组 id；日志里解析不出时为 {@code unknown}
 * @param targetClass 目标类；解析不出时为 {@code unknown}
 * @param kind        注入方式
 * @param line        原始日志行；计数比对场景为空串
 * @param reason      中文原因，直接进 CI 门禁报告
 */
public record InjectionFailure(String modId, String targetClass, MixinInjectionKind kind, String line,
                               String reason) {

    public static final String UNKNOWN = "unknown";

    public InjectionFailure {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(targetClass, "targetClass");
        Objects.requireNonNull(kind, "kind");
        line = line == null ? "" : line;
        Objects.requireNonNull(reason, "reason");
    }
}
