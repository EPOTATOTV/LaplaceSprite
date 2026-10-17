package io.laplace.sprite.cheats.report;

import io.laplace.sprite.core.model.CheatTierName;

/**
 * 单个档位的命中 / 样本统计。
 *
 * <p>样本数为 0 时 {@link #rate()} 返回 0 而不是 NaN、{@link #text()} 返回「无样本」，
 * 避免空桶被读成 0% 检出。</p>
 *
 * @param tier    档位
 * @param hits    命中数
 * @param samples 样本数
 */
public record TierRate(CheatTierName tier, int hits, int samples) {

    public boolean hasSamples() {
        return samples > 0;
    }

    public double rate() {
        return samples <= 0 ? 0.0 : (double) hits / samples;
    }

    /** 表格单元格文本：{@code 命中/样本 · 百分比}；无样本时为「无样本」。 */
    public String text() {
        return hasSamples() ? hits + "/" + samples + " · " + RateFormat.percent(rate()) : "无样本";
    }
}
