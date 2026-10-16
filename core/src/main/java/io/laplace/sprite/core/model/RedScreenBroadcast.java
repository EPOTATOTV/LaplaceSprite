package io.laplace.sprite.core.model;

/**
 * 红屏公示（11.4 红屏公示横幅，沿用 平台红屏交互）。
 *
 * @param playerId    作弊对象
 * @param playerName  展示名
 * @param type        作弊类型
 * @param riskScore   风险分
 * @param actionLabel 确认按钮文案
 */
public record RedScreenBroadcast(String playerId, String playerName, CheatType type, double riskScore,
                                 String actionLabel) {
}
