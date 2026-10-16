package io.laplace.sprite.core.spi;

import io.laplace.sprite.core.model.MetricSample;
import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;

/**
 * 每 tick 上下文（14.4 ③ LifecycleHook.onTick，仅真实环境轨）。
 */
public record TickContext(PlatformKey platform, long tick, Instant now, MetricSample lastSample) {
}
