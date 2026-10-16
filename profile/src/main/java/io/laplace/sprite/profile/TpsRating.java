package io.laplace.sprite.profile;

/**
 * TPS 评级（15.3）：中文社区通用的档位口径，20 为满分，逐级递减。
 *
 * <p>区间左闭右开（除最后一档）：{@code tps >= minTps} 命中该档。</p>
 */
public enum TpsRating {

    /** 满 TPS。 */
    PERFECT(20.0, "完美无卡顿"),
    /** 20 以下到 19.5，绝大多数场景无感。 */
    SMOOTH(19.5, "流畅"),
    /** 19.5 以下到 18，偶有卡顿感。与性能回归阈值同为 19.5。 */
    SLIGHT_LAG(18.0, "略有卡顿"),
    /** 18 以下到 15。 */
    NOTICEABLE_LAG(15.0, "明显卡顿"),
    /** 15 以下到 10。 */
    SEVERE_LAG(10.0, "严重卡顿"),
    /** 10 以下，基本不可玩。 */
    UNUSABLE(0.0, "不可用");

    private final double minTps;
    private final String label;

    TpsRating(double minTps, String label) {
        this.minTps = minTps;
        this.label = label;
    }

    public double minTps() {
        return minTps;
    }

    public String label() {
        return label;
    }

    /** 按 TPS 取档；负数与 10 以下都落到「不可用」。 */
    public static TpsRating of(double tps) {
        if (tps < UNUSABLE.minTps) {
            return UNUSABLE;
        }
        for (TpsRating rating : values()) {
            if (tps >= rating.minTps) {
                return rating;
            }
        }
        return UNUSABLE;
    }

    /** 报告里的一行：档位 + 中文标签，如「流畅（19.8 TPS）」。 */
    public static String describe(double tps) {
        return "%s（%.1f TPS）".formatted(of(tps).label(), tps);
    }
}
