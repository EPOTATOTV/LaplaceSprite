package io.laplace.sprite.core.model;

import java.time.Instant;
import java.util.List;

/**
 * 指标历史（11.2 误报/检出趋势图 / 12.4 基线入库）。口径冻结，越界自动告警。
 */
public record MetricHistory(String metric, double threshold, List<Point> points) {

    public MetricHistory {
        points = points == null ? List.of() : List.copyOf(points);
    }

    /**
     * @param version   版本号
     * @param at        采样时间
     * @param value     实测值
     * @param breached  是否越界（用于自动告警点标记）
     */
    public record Point(String version, Instant at, double value, boolean breached) {
    }

    public List<Point> breachedPoints() {
        return points.stream().filter(Point::breached).toList();
    }
}
