package io.laplace.sprite.profile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * /spark tps 的解析结果（15.3）：多窗口 TPS 与 MSPT。取不到的窗口留 null，不用 0 冒充。
 *
 * @param tps5s  5 秒窗口 TPS
 * @param tps10s 10 秒窗口 TPS
 * @param tps1m  1 分钟窗口 TPS
 * @param tps5m  5 分钟窗口 TPS
 * @param tps15m 15 分钟窗口 TPS
 * @param msptAvg tick 耗时中位数均值（ms）
 * @param msptMin tick 耗时最小值（ms）
 * @param msptMax tick 耗时最大值（ms）
 */
public record SparkTps(Double tps5s, Double tps10s, Double tps1m, Double tps5m, Double tps15m,
                       Double msptAvg, Double msptMin, Double msptMax) {

    public static SparkTps empty() {
        return new SparkTps(null, null, null, null, null, null, null, null);
    }

    /** 是否解析到任何 TPS 窗口。 */
    public boolean present() {
        return tps5s != null || tps10s != null || tps1m != null || tps5m != null || tps15m != null;
    }

    /** 报告口径的主窗口 TPS：1m 优先，依次回退 5m / 15m / 5s / 10s。 */
    public Double primaryTps() {
        if (tps1m != null) {
            return tps1m;
        }
        if (tps5m != null) {
            return tps5m;
        }
        if (tps15m != null) {
            return tps15m;
        }
        if (tps5s != null) {
            return tps5s;
        }
        return tps10s;
    }

    /** 窗口名到值的视图，便于报告渲染。 */
    public Map<String, Double> windows() {
        Map<String, Double> windows = new LinkedHashMap<>();
        windows.put("5s", tps5s);
        windows.put("10s", tps10s);
        windows.put("1m", tps1m);
        windows.put("5m", tps5m);
        windows.put("15m", tps15m);
        return windows;
    }
}
