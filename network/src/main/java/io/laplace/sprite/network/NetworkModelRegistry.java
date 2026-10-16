package io.laplace.sprite.network;

import io.laplace.sprite.core.model.BatchPattern;
import io.laplace.sprite.core.model.NetworkModel;

import java.util.List;
import java.util.Optional;

/**
 * 内置网络条件模型库（4.3）：覆盖默认口径、wifi/lte 批处理，以及固定延迟、尖峰、丢包与基岩客户端。
 */
public final class NetworkModelRegistry {

    /** 基岩客户端模型 id，客户端版本与 Java 版区分开。 */
    public static final String BEDROCK_ID = "bedrock-1.21";

    private static final List<NetworkModel> BUILT_IN = List.of(
            NetworkModel.defaults(),
            NetworkModel.wifi(),
            NetworkModel.lte(),
            new NetworkModel("fixed-100ms", 100, 100, 0, 0, 0.0, 0.0, BatchPattern.NONE, "1.21", 0L),
            new NetworkModel("spike", 30, 90, 10, 400, 0.0, 0.0, BatchPattern.NONE, "1.21", 0L),
            new NetworkModel("lossy", 60, 260, 40, 0, 0.05, 0.04, BatchPattern.WIFI, "1.21", 0L),
            new NetworkModel(BEDROCK_ID, 20, 200, 20, 80, 0.01, 0.01, BatchPattern.NONE, BEDROCK_ID, 0L));

    public List<NetworkModel> builtIn() {
        return BUILT_IN;
    }

    public Optional<NetworkModel> byId(String id) {
        return BUILT_IN.stream().filter(model -> model.id().equals(id)).findFirst();
    }

    public NetworkModel defaultModel() {
        return NetworkModel.defaults();
    }

    public NetworkModel withSeed(NetworkModel base, long seed) {
        return new NetworkModel(base.id(), base.latencyMinMs(), base.latencyMaxMs(), base.jitterMs(),
                base.spikeMs(), base.packetLossRate(), base.reorderRate(), base.batchPattern(),
                base.clientVersion(), seed);
    }
}
