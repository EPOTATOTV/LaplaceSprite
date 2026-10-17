package io.laplace.sprite.adapters.ecosystem;

import java.util.List;

/**
 * 基线四件套的三条冒烟（14.9）。
 *
 * <p>三条断言合起来才算一格通过：启动干净、JEI 与注册表没被扰动、渲染管线接管且帧率没掉出容差。</p>
 */
public final class BaselineSmoke {

    /**
     * 帧率相对空包基线的允许波动。
     *
     * <p>文档写死 ±20%：低于下限说明基线包拖慢了渲染，高于上限通常是基线包没真正加载，
     * 两种情况都要判失败，而不是只卡下限。</p>
     */
    public static final double FPS_TOLERANCE = 0.20;

    /**
     * 物品注册数相对基线的允许偏差。
     *
     * <p>文档只说「在阈值内」没给数字，这里取 5%：基线包固定后注册数应当稳定，
     * 超过 5% 更可能是模组注册表串入或基线包版本不对。</p>
     */
    public static final double ITEM_COUNT_TOLERANCE = 0.05;

    /** 三条断言的类型。 */
    public enum Check {
        /** 启动到 Done 无崩溃、无 Mixin apply failed。 */
        STARTUP_CLEAN,
        /** JEI 配方页可打开，物品注册数在阈值内。 */
        JEI_RECIPE_AND_REGISTRY,
        /** Sodium / Iris 接管渲染管线，帧率在 ±20% 内。 */
        RENDER_PIPELINE_AND_FPS
    }

    /**
     * 一条基线断言。
     *
     * @param check     断言类型
     * @param statement 断言内容
     * @param threshold 判定阈值的人话描述
     */
    public record BaselineAssertion(Check check, String statement, String threshold) {
    }

    private static final List<BaselineAssertion> ASSERTIONS = List.of(
            new BaselineAssertion(Check.STARTUP_CLEAN,
                    "启动到 Done 无崩溃，日志无 Mixin apply failed",
                    "进程正常到达 Done，日志不含 mixin apply failed"),
            new BaselineAssertion(Check.JEI_RECIPE_AND_REGISTRY,
                    "客户端连接后 JEI 配方页可打开，物品注册数与基线 diff 在阈值内",
                    "配方页可打开；物品数偏差 ≤ " + percent(ITEM_COUNT_TOLERANCE)),
            new BaselineAssertion(Check.RENDER_PIPELINE_AND_FPS,
                    "F3 屏确认 Sodium / Iris 接管渲染管线，帧率相对空包波动在 ±" + percent(FPS_TOLERANCE) + " 内",
                    "管线接管；FPS 偏差 ≤ " + percent(FPS_TOLERANCE)));

    private BaselineSmoke() {
    }

    /** 恰好三条，顺序与文档一致。 */
    public static List<BaselineAssertion> assertions() {
        return ASSERTIONS;
    }

    /** 帧率双向容差：高了也可能是基线包没生效。 */
    public static boolean fpsWithinTolerance(double emptyPackFps, double measuredFps) {
        if (emptyPackFps <= 0 || measuredFps <= 0) {
            return false;
        }
        double ratio = measuredFps / emptyPackFps;
        return ratio >= 1 - FPS_TOLERANCE && ratio <= 1 + FPS_TOLERANCE;
    }

    /** 物品注册数按相对偏差判定；基线为 0 时只接受实测为 0。 */
    public static boolean itemCountWithinTolerance(int baselineItems, int measuredItems) {
        if (baselineItems <= 0) {
            return measuredItems == 0;
        }
        double deviation = Math.abs((double) measuredItems - baselineItems) / baselineItems;
        return deviation <= ITEM_COUNT_TOLERANCE;
    }

    private static String percent(double ratio) {
        return "%.0f%%".formatted(ratio * 100);
    }
}
