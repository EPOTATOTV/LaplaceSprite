package io.laplace.sprite.core.model;

/**
 * 一次运行的指标集（4.5 指标 / 第五章 TestRun.metrics）。
 *
 * @param legitPlayers      模拟合法玩家数
 * @param falsePositives    误报 flag 数
 * @param cheatSamples      注入作弊样本数
 * @param detectedSamples   命中数
 * @param boundarySamples   阈值边界样本数
 * @param boundaryPassed    符合预期的边界样本数
 * @param concurrencyOk     并发灌入是否通过
 * @param coverage          核心模块行覆盖率
 */
public record MetricSet(
        int legitPlayers,
        int falsePositives,
        int cheatSamples,
        int detectedSamples,
        int boundarySamples,
        int boundaryPassed,
        boolean concurrencyOk,
        double coverage) {

    public static MetricSet empty() {
        return new MetricSet(0, 0, 0, 0, 0, 0, true, 0.0);
    }

    /** 合法误报率 = 误报 flag 数 / 模拟合法玩家数。 */
    public double falsePositiveRate() {
        return legitPlayers <= 0 ? 0.0 : (double) falsePositives / legitPlayers;
    }

    /** 作弊检出率 = 命中数 / 注入样本数。 */
    public double detectionRate() {
        return cheatSamples <= 0 ? 1.0 : (double) detectedSamples / cheatSamples;
    }

    /** 阈值边界通过率。 */
    public double boundaryPassRate() {
        return boundarySamples <= 0 ? 1.0 : (double) boundaryPassed / boundarySamples;
    }
}
