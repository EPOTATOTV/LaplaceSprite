package io.laplace.sprite.cheats.report;

import java.util.List;

/**
 * 一个维度的检出率行：按检测项或按客户端。
 *
 * <p>{@code tiers} 恒含四个档位（含样本数为 0 的档位），报表按固定列输出。</p>
 *
 * @param label   行标签（check 名或客户端出处标签）
 * @param tiers   分档位统计，顺序与覆盖范围同 {@link io.laplace.sprite.core.model.CheatTierName}
 * @param hits    合计命中数
 * @param samples 合计样本数
 */
public record RateRow(String label, List<TierRate> tiers, int hits, int samples) {

    public RateRow {
        tiers = List.copyOf(tiers);
    }

    public boolean hasSamples() {
        return samples > 0;
    }

    public double rate() {
        return samples <= 0 ? 0.0 : (double) hits / samples;
    }

    /** 合计单元格文本：{@code 命中/样本}；无样本时为「无样本」。 */
    public String counts() {
        return hasSamples() ? hits + "/" + samples : "无样本";
    }
}
