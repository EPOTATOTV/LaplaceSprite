package io.laplace.sprite.protocol;

/**
 * 单个阶段的探测结果。
 *
 * @param stage  阶段
 * @param passed 是否通过
 * @param detail 说明（通过原因或失败原因）
 */
public record ProtocolStageResult(ProtocolStage stage, boolean passed, String detail) {

    public static ProtocolStageResult pass(ProtocolStage stage, String detail) {
        return new ProtocolStageResult(stage, true, detail);
    }

    public static ProtocolStageResult fail(ProtocolStage stage, String detail) {
        return new ProtocolStageResult(stage, false, detail);
    }
}
