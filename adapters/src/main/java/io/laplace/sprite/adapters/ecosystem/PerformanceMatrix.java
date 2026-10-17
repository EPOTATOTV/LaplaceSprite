package io.laplace.sprite.adapters.ecosystem;

import java.util.List;

/**
 * 性能模组兼容矩阵（14.9）。
 *
 * <p>客户端行五格固定；服务端行单独验证 Lithium 的 tick 行为，不重复跑渲染断言。
 * 同时提供不构造格子的红线检查，供上层在生成矩阵时提前剪枝。</p>
 */
public final class PerformanceMatrix {

    /** 红格校验结论。 */
    public enum Verdict {
        VALID,
        INVALID
    }

    /**
     * 一条红格校验结论。
     *
     * @param verdict 结论
     * @param reason  判红的原因；合法时为空串
     */
    public record CellVerdict(Verdict verdict, String reason) {

        public boolean valid() {
            return verdict == Verdict.VALID;
        }
    }

    private PerformanceMatrix() {
    }

    /** 客户端五格，顺序与文档一致。 */
    public static List<RenderPipelineCell> clientCells() {
        return List.of(
                RenderPipelineCell.of(PerformanceVariant.VANILLA),
                RenderPipelineCell.of(PerformanceVariant.LITHIUM),
                RenderPipelineCell.of(PerformanceVariant.SODIUM),
                RenderPipelineCell.of(PerformanceVariant.SODIUM_IRIS),
                RenderPipelineCell.of(PerformanceVariant.OPTIFINE));
    }

    /** 服务端行：裸原版与 +Lithium 两格，只跑 tick 断言。 */
    public static List<RenderPipelineCell> serverCells() {
        return List.of(
                RenderPipelineCell.server(PerformanceVariant.VANILLA),
                RenderPipelineCell.server(PerformanceVariant.LITHIUM));
    }

    /** 不构造格子的红线检查：Sodium × OptiFine 互斥，Iris 单独出现非法。 */
    public static CellVerdict validate(List<String> mods) {
        if (mods.contains(RenderPipelineCell.SODIUM) && mods.contains(RenderPipelineCell.OPTIFINE)) {
            return new CellVerdict(Verdict.INVALID, RenderPipelineCell.SODIUM_OPTIFINE_RULE);
        }
        if (mods.contains(RenderPipelineCell.IRIS) && !mods.contains(RenderPipelineCell.SODIUM)) {
            return new CellVerdict(Verdict.INVALID, RenderPipelineCell.IRIS_SODIUM_RULE);
        }
        return new CellVerdict(Verdict.VALID, "");
    }
}
