package io.laplace.sprite.core.model;

import java.util.Map;

/**
 * 模块健康（11.6 模块状态面板）：7 个模块的运行状态、版本、关键计数。
 */
public record ModuleHealth(String module, String version, RunStatus status, Map<String, Long> counters) {

    public ModuleHealth {
        counters = counters == null ? Map.of() : Map.copyOf(counters);
    }
}
