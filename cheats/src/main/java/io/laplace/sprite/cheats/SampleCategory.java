package io.laplace.sprite.cheats;

/**
 * 样本类别（4.2 异常样本矩阵）。
 *
 * <p>CHEAT 只是检测类场景下的一个子类；其余类别验证被测目标对无效输入、越界参数、
 * 并发竞争、资源引用缺失与故障注入的鲁棒性。分类决定报告的分组方式：检出率与绕过清单
 * 只对 CHEAT 有意义，别的类别不参与检出率口径。</p>
 */
public enum SampleCategory {
    CONTENT,
    TECH,
    PERF,
    NETWORK,
    ANOMALY,
    CHEAT,
    /** 数据包轨：pack_format、函数与标签的加载期行为（升级实施文档 L-02）。 */
    DATAPACK
}
