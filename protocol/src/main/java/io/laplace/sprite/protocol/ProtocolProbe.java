package io.laplace.sprite.protocol;

import java.util.List;

/**
 * 协议格探测（15.6）：真实实现走客户端自动化，离线实现见 {@link SimulatedProtocolProbe}。
 */
public interface ProtocolProbe {

    /** 跑一格的最小闭环，返回各阶段结果。 */
    List<ProtocolStageResult> run(ProtocolMatrixCell cell);
}
