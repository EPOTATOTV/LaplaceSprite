package io.laplace.sprite.report;

import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.RegressionCase;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 回归库健康度（升级实施文档 L-03）：把「绕过报告有没有进库、误报有没有复现用例」量化成门禁指标。
 *
 * <p>四个口径：回归用例规模、绕过报告转化率、误报复现覆盖率、超期未转化的绕过报告数。
 * 没有绕过报告或误报 flag 时不拿空集判 0%——没有数据就不下结论。</p>
 */
public final class RegressionHealth {

    /**
     * 一条绕过报告。
     *
     * @param id         报告 id
     * @param cheatType  作弊类型
     * @param reportedAt 报告时间
     * @param converted  是否已转成回归用例
     */
    public record BypassReport(String id, String cheatType, Instant reportedAt, boolean converted) {

        public BypassReport {
            Objects.requireNonNull(id, "id");
            cheatType = cheatType == null ? "" : cheatType;
            Objects.requireNonNull(reportedAt, "reportedAt");
        }
    }

    /**
     * 健康度阈值。
     *
     * @param minCases                回归用例规模下限
     * @param minBypassConversion     绕过报告转化率下限
     * @param minFalsePositiveCoverage 误报复现覆盖率下限
     * @param bypassWindow            绕过报告入库时限
     */
    public record Thresholds(int minCases, double minBypassConversion, double minFalsePositiveCoverage,
                             Duration bypassWindow) {

        public Thresholds {
            if (minCases < 0 || minBypassConversion < 0 || minBypassConversion > 1
                    || minFalsePositiveCoverage < 0 || minFalsePositiveCoverage > 1) {
                throw new IllegalArgumentException("回归库阈值超出范围");
            }
            Objects.requireNonNull(bypassWindow, "bypassWindow");
        }

        /** 升级实施文档的口径：绕过报告 30 天内进库、误报复现 100% 覆盖。 */
        public static Thresholds defaults() {
            return new Thresholds(10, 0.8, 1.0, Duration.ofDays(30));
        }
    }

    /**
     * 一次健康度评估结果。
     *
     * @param caseCount               回归用例数
     * @param bypassReports           绕过报告数
     * @param convertedBypasses       已转化数
     * @param bypassConversionRate    转化率；没有报告时为 0
     * @param falsePositiveFlags      误报 flag 数
     * @param coveredFalsePositives   已复现的误报数
     * @param falsePositiveCoverage   误报复现覆盖率；没有误报时为 1
     * @param overdueBypasses         超期未转化数
     * @param healthy                 是否全部达标
     * @param violations              未达标项
     */
    public record Health(int caseCount, int bypassReports, int convertedBypasses, double bypassConversionRate,
                         int falsePositiveFlags, int coveredFalsePositives, double falsePositiveCoverage,
                         int overdueBypasses, boolean healthy, List<String> violations) {

        public Health {
            violations = violations == null ? List.of() : List.copyOf(violations);
        }
    }

    private RegressionHealth() {
    }

    public static Health evaluate(List<RegressionCase> cases, List<FlagEvent> flags,
                                  List<BypassReport> bypassReports, Thresholds thresholds, Instant now) {
        Objects.requireNonNull(thresholds, "thresholds");
        Objects.requireNonNull(now, "now");
        List<RegressionCase> regressionCases = cases == null ? List.of() : List.copyOf(cases);
        List<BypassReport> reports = bypassReports == null ? List.of() : List.copyOf(bypassReports);
        List<FlagEvent> falsePositives = flags == null ? List.of() : flags.stream()
                .filter(FlagEvent::falsePositive)
                .toList();

        int converted = (int) reports.stream().filter(BypassReport::converted).count();
        double conversionRate = reports.isEmpty() ? 0 : (double) converted / reports.size();
        int covered = (int) falsePositives.stream()
                .filter(flag -> regressionCases.stream().anyMatch(c -> c.flagId().equals(flag.id())))
                .count();
        double coverage = falsePositives.isEmpty() ? 1.0 : (double) covered / falsePositives.size();
        int overdue = (int) reports.stream()
                .filter(report -> !report.converted())
                .filter(report -> report.reportedAt().plus(thresholds.bypassWindow()).isBefore(now))
                .count();

        List<String> violations = new ArrayList<>();
        if (regressionCases.size() < thresholds.minCases()) {
            violations.add("回归用例 %d 条，低于下限 %d".formatted(regressionCases.size(), thresholds.minCases()));
        }
        if (!reports.isEmpty() && conversionRate < thresholds.minBypassConversion()) {
            violations.add("绕过报告转化率 %.2f%%，低于下限 %.2f%%".formatted(
                    conversionRate * 100, thresholds.minBypassConversion() * 100));
        }
        if (!falsePositives.isEmpty() && coverage < thresholds.minFalsePositiveCoverage()) {
            violations.add("误报复现覆盖率 %.2f%%，低于下限 %.2f%%".formatted(
                    coverage * 100, thresholds.minFalsePositiveCoverage() * 100));
        }
        if (overdue > 0) {
            violations.add("%d 条绕过报告超过 %d 天未入库".formatted(
                    overdue, thresholds.bypassWindow().toDays()));
        }

        return new Health(regressionCases.size(), reports.size(), converted, conversionRate,
                falsePositives.size(), covered, coverage, overdue, violations.isEmpty(), violations);
    }
}
