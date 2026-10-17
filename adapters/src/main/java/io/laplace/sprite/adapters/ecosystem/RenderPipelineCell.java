package io.laplace.sprite.adapters.ecosystem;

import java.util.List;
import java.util.Objects;

/**
 * 性能矩阵的一格（14.9）。
 *
 * <p>红线是硬规则，构造时就拒绝，不留到跑完再看报告：
 * Sodium 与 OptiFine 互斥（两者都重写渲染管线，OptiFine 闭源），Iris 必须与 Sodium 同装。</p>
 */
public final class RenderPipelineCell {

    public static final String SODIUM = "sodium";
    public static final String IRIS = "iris";
    public static final String OPTIFINE = "optifine";

    public static final String SODIUM_OPTIFINE_RULE =
            "红格：Sodium 与 OptiFine 互斥，两者都重写渲染管线且 OptiFine 闭源，不允许同装";
    public static final String IRIS_SODIUM_RULE =
            "红格：Iris 强依赖 Sodium，单独装 Iris 不构成合法渲染管线";

    /** 客户端格断言顺序与文档一致：XVFB → 标题 → 世界快照 → 等区块 → 截图 → 日志 → 帧率。 */
    public static final List<String> CLIENT_ASSERTIONS = List.of(
            "XVFB 下启动到标题界面",
            "进入预设世界快照",
            "等待区块渲染完成",
            "截图作为证据",
            "日志无 mixin / render error",
            "FPS 基线不低于阈值");

    /** 服务端行只验证 Lithium 的 tick 行为。 */
    public static final List<String> SERVER_ASSERTIONS = List.of(
            "启动后 MSPT 与 TPS 稳定在基线内",
            "Lithium tick 行为无异常，无区块加载抖动");

    private final PerformanceVariant variant;
    private final List<String> mods;
    private final List<String> assertions;

    public RenderPipelineCell(PerformanceVariant variant, List<String> mods) {
        this(variant, mods, CLIENT_ASSERTIONS);
    }

    public RenderPipelineCell(PerformanceVariant variant, List<String> mods, List<String> assertions) {
        this.variant = Objects.requireNonNull(variant, "variant");
        this.mods = List.copyOf(Objects.requireNonNull(mods, "mods"));
        this.assertions = List.copyOf(Objects.requireNonNull(assertions, "assertions"));
        validate(this.mods);
    }

    /** 按变体声明的模组集合建格。 */
    public static RenderPipelineCell of(PerformanceVariant variant) {
        return new RenderPipelineCell(variant, variant.mods());
    }

    /** 服务端格：不装客户端渲染模组，只走 Lithium tick 断言。 */
    public static RenderPipelineCell server(PerformanceVariant variant) {
        if (variant.clientOnly()) {
            throw new IllegalArgumentException("服务端行不能使用客户端渲染管线: " + variant.displayName());
        }
        return new RenderPipelineCell(variant, variant.mods(), SERVER_ASSERTIONS);
    }

    private static void validate(List<String> mods) {
        if (mods.contains(SODIUM) && mods.contains(OPTIFINE)) {
            throw new IllegalArgumentException(SODIUM_OPTIFINE_RULE);
        }
        if (mods.contains(IRIS) && !mods.contains(SODIUM)) {
            throw new IllegalArgumentException(IRIS_SODIUM_RULE);
        }
    }

    public PerformanceVariant variant() {
        return variant;
    }

    public List<String> mods() {
        return mods;
    }

    public List<String> assertions() {
        return assertions;
    }

    public String displayName() {
        return variant.displayName();
    }
}
