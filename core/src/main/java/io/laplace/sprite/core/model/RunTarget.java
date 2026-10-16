package io.laplace.sprite.core.model;

import java.util.Map;

/**
 * 检测目标描述（4.4）：检测引擎 / 客户端扫描器 / 平台插件 / 模组加载器。
 *
 * @param id         目标 id（如 {@code detection-engine}）
 * @param name       展示名
 * @param kind       目标类型
 * @param platform   运行平台；进程内目标可为空
 * @param attributes 目标特定属性（如 {@code scanner=ProcessScanner}）
 */
public record RunTarget(String id, String name, TargetKind kind, PlatformKey platform,
                        Map<String, String> attributes) {

    public RunTarget {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static RunTarget detectionEngine() {
        return new RunTarget("detection-engine", "独立检测引擎", TargetKind.DETECTION_ENGINE,
                null, Map.of());
    }

    public static RunTarget clientScanner() {
        return new RunTarget("client-scanner", "客户端扫描器（探针桩）", TargetKind.CLIENT_SCANNER,
                null, Map.of("scanner", "ProcessScanner"));
    }
}
