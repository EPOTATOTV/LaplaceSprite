package io.laplace.sprite.core.model;

/**
 * 网络条件模型（4.3 / 第五章 NetworkModel）。
 *
 * @param id             模型 id
 * @param latencyMinMs   延迟段下界（默认 10ms）
 * @param latencyMaxMs   延迟段上界（默认 350ms）
 * @param jitterMs       抖动幅度
 * @param spikeMs        延迟尖峰（0 表示无）
 * @param packetLossRate 随机丢包率（0-1）
 * @param reorderRate    重排率（0-1）
 * @param batchPattern   包批处理模式
 * @param clientVersion  客户端版本（1.8-1.21 及基岩差异）
 * @param seed           采样种子，保证可复现
 */
public record NetworkModel(
        String id,
        int latencyMinMs,
        int latencyMaxMs,
        int jitterMs,
        int spikeMs,
        double packetLossRate,
        double reorderRate,
        BatchPattern batchPattern,
        String clientVersion,
        long seed) {

    public NetworkModel {
        if (latencyMinMs < 0 || latencyMaxMs < latencyMinMs) {
            throw new IllegalArgumentException("非法延迟段: " + latencyMinMs + ".." + latencyMaxMs);
        }
        if (packetLossRate < 0 || packetLossRate > 1) {
            throw new IllegalArgumentException("丢包率越界: " + packetLossRate);
        }
        if (reorderRate < 0 || reorderRate > 1) {
            throw new IllegalArgumentException("重排率越界: " + reorderRate);
        }
    }

    /** 默认口径：10-350ms 均匀采样，无抖动、无批处理（继承 server-core NetworkModel 口径）。 */
    public static NetworkModel defaults() {
        return new NetworkModel("default", 10, 350, 0, 0, 0.0, 0.0, BatchPattern.NONE, "1.21.11", 0L);
    }

    public static NetworkModel wifi() {
        return new NetworkModel("wifi", 20, 180, 15, 0, 0.01, 0.005, BatchPattern.WIFI, "1.21.11", 0L);
    }

    public static NetworkModel lte() {
        return new NetworkModel("lte", 40, 350, 35, 120, 0.03, 0.02, BatchPattern.LTE, "1.21.11", 0L);
    }
}
