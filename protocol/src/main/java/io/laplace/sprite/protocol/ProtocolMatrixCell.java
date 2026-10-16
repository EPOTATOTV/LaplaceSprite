package io.laplace.sprite.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * 协议矩阵的一格（15.6）：被测服务端版本 V_s × 客户端版本 V_c。
 *
 * @param serverVersion 被测服务端版本
 * @param clientVersion 客户端版本；基岩格固定为 bedrock
 * @param viaProxy      是否经 ViaProxy 入口接入
 * @param bedrock       是否基岩客户端格
 * @param passedStages  按顺序通过的阶段
 * @param failedStage   首个失败阶段；全通过时为 null
 * @param ok            是否整格通过
 */
public record ProtocolMatrixCell(String serverVersion, String clientVersion, boolean viaProxy, boolean bedrock,
                                 List<ProtocolStage> passedStages, ProtocolStage failedStage, boolean ok) {

    public ProtocolMatrixCell {
        passedStages = passedStages == null ? List.of() : List.copyOf(passedStages);
    }

    public String id() {
        return "%s<-%s%s".formatted(serverVersion, clientVersion, bedrock ? "(bedrock)" : "");
    }

    /** 尚未探测的格子。 */
    public static ProtocolMatrixCell seed(String serverVersion, String clientVersion, boolean viaProxy,
                                          boolean bedrock) {
        return new ProtocolMatrixCell(serverVersion, clientVersion, viaProxy, bedrock, List.of(), null, false);
    }

    /**
     * 按闭环顺序折叠探测结果。
     *
     * <p>某个阶段没有结果也算失败——没验证过就不算通过。</p>
     */
    public static ProtocolMatrixCell of(ProtocolMatrixCell seed, List<ProtocolStageResult> results) {
        List<ProtocolStage> passed = new ArrayList<>();
        ProtocolStage failed = null;
        for (ProtocolStage stage : ProtocolStage.ordered()) {
            ProtocolStageResult result = results.stream()
                    .filter(candidate -> candidate.stage() == stage)
                    .findFirst()
                    .orElse(null);
            if (result != null && result.passed()) {
                passed.add(stage);
            } else {
                failed = stage;
                break;
            }
        }
        boolean ok = failed == null && passed.size() == ProtocolStage.ordered().size();
        return new ProtocolMatrixCell(seed.serverVersion(), seed.clientVersion(), seed.viaProxy(), seed.bedrock(),
                passed, failed, ok);
    }
}
