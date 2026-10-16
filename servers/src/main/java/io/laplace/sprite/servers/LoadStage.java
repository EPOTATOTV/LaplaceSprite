package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.MetricSample;

/**
 * 阶梯负载的一个台阶（12.3 ②）。
 *
 * @param simulatedPlayers 该台阶的模拟玩家数
 * @param tps              实测 TPS
 * @param mspt             实测 MSPT
 * @param heapUsedMb       实测堆占用
 * @param heapMaxMb        堆上限
 */
public record LoadStage(int simulatedPlayers, double tps, double mspt, long heapUsedMb, long heapMaxMb) {

    /** 拐点判定：TPS 跌破 20.0 或 MSPT 超过 50ms。 */
    public boolean breached() {
        return tps < 20.0 || mspt > 50.0;
    }

    /** 尚未跑到的台阶：标称值满 TPS、零 MSPT，不参与拐点判定。 */
    public static LoadStage planned(int simulatedPlayers) {
        return new LoadStage(simulatedPlayers, 20.0, 0.0, 0L, 0L);
    }

    public static LoadStage of(int simulatedPlayers, MetricSample sample) {
        return new LoadStage(simulatedPlayers, sample.tps(), sample.mspt(), sample.heapUsedMb(),
                sample.heapMaxMb());
    }

    public boolean measured() {
        return mspt > 0.0;
    }
}
