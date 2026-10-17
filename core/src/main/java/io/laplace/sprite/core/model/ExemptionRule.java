package io.laplace.sprite.core.model;

/**
 * 误报豁免规则（4.1「创造 / 旁观 / 允许飞行 / 开局缓冲期 / 其它插件传送与击退不计误报」，可配置）。
 *
 * <p>豁免只影响「是否计入误报」，不影响 flag 本身被记录：被豁免的 flag 仍然进证据链，
 * 只是不计进 {@code MetricSet.falsePositives}，因此不拉高误报率。</p>
 */
public enum ExemptionRule {
    /** 创造模式。 */
    CREATIVE,
    /** 旁观模式。 */
    SPECTATOR,
    /** 服务端允许飞行（含飞行插件授予）。 */
    ALLOW_FLIGHT,
    /** 开局缓冲期：玩家进服后的前若干个 tick。 */
    JOIN_GRACE,
    /** 其它插件传送造成的位移。 */
    PLUGIN_TELEPORT,
    /** 其它插件击退造成的位移。 */
    PLUGIN_KNOCKBACK;

    /**
     * 从配置里的字符串解析；未知取值返回 {@code null} 由调用方忽略。
     */
    public static ExemptionRule parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        for (ExemptionRule rule : values()) {
            if (rule.name().equals(normalized)) {
                return rule;
            }
        }
        return null;
    }
}
