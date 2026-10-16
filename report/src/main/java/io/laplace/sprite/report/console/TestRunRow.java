package io.laplace.sprite.report.console;

import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.RunStatus;

import java.time.Instant;

/**
 * 11.2 测试运行表的一行。
 */
public record TestRunRow(String id, String target, String suite, int legitSamples,
                         double falsePositiveRate, double detectionRate, int boundarySamples,
                         RunStatus status, PlatformKey platform, Instant startedAt, long durationMs) {
}
