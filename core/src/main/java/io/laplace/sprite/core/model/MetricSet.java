package io.laplace.sprite.core.model;

/**
 * 一次运行的指标集（4.5 指标 / 第五章 TestRun.metrics）。
 *
 * <p>前八项是检测类专项指标；后七项是 4.5 新增的功能类指标。功能冒烟用例与检测套件是两条独立的
 * 测试线，因此没有功能冒烟用例的运行把 {@code functionalSmokeTotal} 记为 0，表示「不适用」。</p>
 *
 * @param legitPlayers              模拟合法玩家数
 * @param falsePositives            误报 flag 数
 * @param cheatSamples              注入作弊样本数
 * @param detectedSamples           命中数
 * @param boundarySamples           阈值边界样本数
 * @param boundaryPassed            符合预期的边界样本数
 * @param concurrencyOk             并发灌入是否通过
 * @param coverage                  核心模块行覆盖率
 * @param functionalSmokePassed     功能冒烟通过数
 * @param functionalSmokeTotal      功能冒烟总数
 * @param performanceBaselineOk     性能基线对比是否可判定（未采集基线时为 true，表示不参与门禁）
 * @param performanceBaselineDelta  相对上一版本基线的 TPS 变化率，正值为改善
 * @param dependencyResolved        依赖解析无缺失
 * @param versionMatrixDegraded     版本矩阵出现降级告警
 * @param resourcePackLoadOk        资源包 / 数据包加载通过
 */
public record MetricSet(
        int legitPlayers,
        int falsePositives,
        int cheatSamples,
        int detectedSamples,
        int boundarySamples,
        int boundaryPassed,
        boolean concurrencyOk,
        double coverage,
        int functionalSmokePassed,
        int functionalSmokeTotal,
        boolean performanceBaselineOk,
        double performanceBaselineDelta,
        boolean dependencyResolved,
        boolean versionMatrixDegraded,
        boolean resourcePackLoadOk) {

    /**
     * 兼容构造：只描述检测类指标。
     *
     * <p>功能冒烟总数记 0（不适用），性能与资源装配按「未发现问题」处理，避免旧调用方被新增门禁拦下。</p>
     */
    public MetricSet(int legitPlayers, int falsePositives, int cheatSamples, int detectedSamples,
                     int boundarySamples, int boundaryPassed, boolean concurrencyOk, double coverage) {
        this(legitPlayers, falsePositives, cheatSamples, detectedSamples, boundarySamples,
                boundaryPassed, concurrencyOk, coverage, 0, 0, true, 0.0, true, false, true);
    }

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

    /**
     * 功能冒烟通过率。
     *
     * <p>总数为 0 表示本轮没有功能冒烟用例，按「不适用」返回 1.0；门禁侧还会先判总数再决定是否拦截，
     * 两道保险避免只跑检测套件的运行被误判为冒烟失败。</p>
     */
    public double functionalSmokePassRate() {
        return functionalSmokeTotal <= 0 ? 1.0 : (double) functionalSmokePassed / functionalSmokeTotal;
    }

    /** 资源与装配：依赖无缺失、版本矩阵无降级告警、资源包 / 数据包加载通过，三项同时成立才算通过。 */
    public boolean resourceAndAssemblyOk() {
        return dependencyResolved && !versionMatrixDegraded && resourcePackLoadOk;
    }

    /** 性能是否不劣化：基线可判定且相对变化不为负。 */
    public boolean performanceNotDegraded() {
        return performanceBaselineOk && performanceBaselineDelta >= 0.0;
    }
}
