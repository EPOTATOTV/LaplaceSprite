package io.laplace.sprite.report.console;

/**
 * 11.2 指标卡片：前端控制台顶部六张卡。
 */
public record MetricCards(int todayRuns, int simulatedPlayers, int cheatSpecCount,
                          double falsePositiveRate, double detectionRate, int gatePassed) {
}
