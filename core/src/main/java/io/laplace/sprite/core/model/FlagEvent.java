package io.laplace.sprite.core.model;

import java.util.List;

/**
 * 检测 flag 明细（4.6 报告与证据链 / 11.4 关联警告）。
 *
 * @param id            flag id
 * @param runId         所属运行
 * @param playerId      触发玩家（SimID）
 * @param playerType    玩家类型
 * @param checkName     检测项名
 * @param cheatType     关联作弊类型；误报样本为空
 * @param tier          触发时使用的配置档位（检出率 × 档位矩阵用）
 * @param confidence    置信度（0-1）
 * @param tick          触发 tick
 * @param trajectoryRef 触发轨迹引用（可回放）
 * @param evidenceRef   证据引用
 * @param falsePositive 是否为误报
 */
public record FlagEvent(
        String id,
        String runId,
        String playerId,
        PlayerType playerType,
        String checkName,
        CheatType cheatType,
        CheatTierName tier,
        double confidence,
        long tick,
        String trajectoryRef,
        String evidenceRef,
        boolean falsePositive) {

    public static FlagEvent truePositive(String runId, String playerId, String checkName,
                                         CheatType cheatType, CheatTierName tier, double confidence,
                                         long tick, String trajectoryRef, String evidenceRef) {
        return new FlagEvent("flag-" + runId + "-" + tick + "-" + checkName, runId, playerId,
                PlayerType.CHEAT, checkName, cheatType, tier, confidence, tick, trajectoryRef, evidenceRef, false);
    }

    public static FlagEvent falsePositive(String runId, String playerId, String checkName, long tick,
                                          String trajectoryRef, String evidenceRef) {
        return new FlagEvent("fp-" + runId + "-" + tick + "-" + checkName, runId, playerId,
                PlayerType.LEGIT, checkName, null, null, 1.0, tick, trajectoryRef, evidenceRef, true);
    }
}
