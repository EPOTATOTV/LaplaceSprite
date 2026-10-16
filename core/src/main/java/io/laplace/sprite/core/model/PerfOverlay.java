package io.laplace.sprite.core.model;

/**
 * 性能悬浮窗（11.8 / 12.5）：该实例所在服务器实时 TPS / MSPT / 延迟；
 * 压测拐点自动告警并触发录制归档。
 */
public record PerfOverlay(String instanceId, double tps, double mspt, int latencyMs, boolean kneeAlert,
                          boolean recordingTriggered) {

    public static PerfOverlay from(String instanceId, MetricSample sample, int latencyMs) {
        boolean knee = sample.knee();
        return new PerfOverlay(instanceId, sample.tps(), sample.mspt(), latencyMs, knee, knee);
    }
}
