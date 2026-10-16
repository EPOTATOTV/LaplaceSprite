package io.laplace.sprite.hotreload;

import java.util.List;

/**
 * 重载能力判定报告（15.2）。
 *
 * <p>{@code marker} 非空即「禁忌标记」：该轮结果不进入发布门禁。</p>
 *
 * @param changeId           改动 id
 * @param kind               改动类型
 * @param jvm                JVM 类型
 * @param hotSwappable       本次改动能否热生效
 * @param recommendation     人话建议
 * @param requiresRestart    是否必须重启
 * @param agentArgs          需要的 agent / JVM 参数
 * @param marker             禁忌标记，无则为空串
 * @param releaseGateBlocked 是否被挡在发布门禁之外
 */
public record HotReloadReport(String changeId, ChangeKind kind, JvmKind jvm, boolean hotSwappable,
                              String recommendation, boolean requiresRestart, List<String> agentArgs,
                              String marker, boolean releaseGateBlocked) {

    public HotReloadReport {
        agentArgs = agentArgs == null ? List.of() : List.copyOf(agentArgs);
        marker = marker == null ? "" : marker;
    }

    /** 挂上禁忌标记：结果不进发布门禁。 */
    public HotReloadReport withMisuse(String misuseMarker) {
        if (misuseMarker == null || misuseMarker.isBlank()) {
            throw new IllegalArgumentException("禁忌标记不能为空");
        }
        return new HotReloadReport(changeId, kind, jvm, hotSwappable, recommendation, requiresRestart,
                agentArgs, misuseMarker, true);
    }

    public boolean releaseGateAllowed() {
        return !releaseGateBlocked;
    }

    public boolean marked() {
        return !marker.isEmpty();
    }
}
