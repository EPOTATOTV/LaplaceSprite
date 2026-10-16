package io.laplace.sprite.protocol;

import io.laplace.sprite.core.spi.VersionOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 协议兼容矩阵（15.6）：服务端版本 × 相邻 3-5 个客户端版本，逐格跑最小闭环。
 *
 * <p>只负责挑邻近版本、逐格驱动探针、汇总失败格与失败阶段；具体怎么探由 {@link ProtocolProbe} 决定。</p>
 */
public final class ProtocolMatrix {

    /** 相邻客户端版本下限。 */
    public static final int MIN_CLIENT_VERSIONS = 3;
    /** 相邻客户端版本上限：再多就是矩阵爆炸。 */
    public static final int MAX_CLIENT_VERSIONS = 5;

    private final ProtocolProbe probe;
    private final List<ProtocolMatrixCell> cells = new ArrayList<>();
    private boolean viaProxy = true;

    public ProtocolMatrix(ProtocolProbe probe) {
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    /** 是否经 ViaProxy 入口接入，默认是。 */
    public ProtocolMatrix withViaProxy(boolean enabled) {
        this.viaProxy = enabled;
        return this;
    }

    /** 跑一轮矩阵：挑相邻客户端版本，逐格探测。 */
    public List<ProtocolMatrixCell> run(String serverVersion, List<String> clientVersions) {
        List<ProtocolMatrixCell> produced = new ArrayList<>();
        for (String clientVersion : adjacent(serverVersion, clientVersions)) {
            ProtocolMatrixCell seed = ProtocolMatrixCell.seed(serverVersion, clientVersion, viaProxy, false);
            produced.add(ProtocolMatrixCell.of(seed, probe.run(seed)));
        }
        cells.addAll(produced);
        return List.copyOf(produced);
    }

    /** 基岩格：客户端版本固定记为 bedrock，由 Geyser + Floodgate 承接。 */
    public ProtocolMatrixCell runBedrock(String serverVersion) {
        ProtocolMatrixCell seed = ProtocolMatrixCell.seed(serverVersion, "bedrock", viaProxy, true);
        ProtocolMatrixCell cell = ProtocolMatrixCell.of(seed, probe.run(seed));
        cells.add(cell);
        return cell;
    }

    /**
     * 选相邻客户端版本：以服务端版本在候选列表里的位置为中心取最多 5 个，不足 5 个就整体前移。
     *
     * <p>候选不足 {@link #MIN_CLIENT_VERSIONS} 个时全部使用，不做补版本。</p>
     */
    public static List<String> adjacent(String serverVersion, List<String> versions) {
        List<String> sorted = versions.stream().distinct().sorted(VersionOrder::compare).toList();
        int index = sorted.indexOf(serverVersion);
        if (index < 0) {
            throw new IllegalArgumentException("服务端版本 %s 不在候选客户端版本列表里: %s"
                    .formatted(serverVersion, versions));
        }
        if (sorted.size() < MIN_CLIENT_VERSIONS) {
            return List.copyOf(sorted);
        }
        int window = Math.min(MAX_CLIENT_VERSIONS, sorted.size());
        int from = Math.max(0, Math.min(index - window / 2, sorted.size() - window));
        return List.copyOf(sorted.subList(from, from + window));
    }

    public List<ProtocolMatrixCell> cells() {
        return List.copyOf(cells);
    }

    public List<ProtocolMatrixCell> failedCells() {
        return cells.stream().filter(cell -> !cell.ok()).toList();
    }

    /** 失败格与失败阶段的一行人话摘要。 */
    public List<String> failureSummary() {
        return cells.stream()
                .filter(cell -> !cell.ok())
                .map(cell -> "%s 失败于 %s 阶段".formatted(cell.id(), cell.failedStage()))
                .toList();
    }

    public void clear() {
        cells.clear();
    }
}
