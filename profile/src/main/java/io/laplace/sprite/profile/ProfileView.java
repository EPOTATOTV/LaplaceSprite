package io.laplace.sprite.profile;

import java.util.List;

/**
 * 视角切分后的报告视图（15.3）：决定先看哪条线程、哪些指标排在前面。
 *
 * <p>指标键取自本类的常量，报告渲染按 {@link #primaryMetrics()} 的顺序摆放，
 * {@link #deferredMetrics()} 折叠到后面。</p>
 *
 * @param viewpoint       视角
 * @param primaryThread   首要查看的线程
 * @param threadFocus     线程查看顺序
 * @param primaryMetrics  先展示的指标
 * @param deferredMetrics 后展示的指标
 * @param report          原始报告
 */
public record ProfileView(ProfileViewpoint viewpoint, String primaryThread, List<String> threadFocus,
                          List<String> primaryMetrics, List<String> deferredMetrics, ProfileReport report) {

    /** Tick 健康：TPS / MSPT avg·min·max。 */
    public static final String TICK_HEALTH = "tick-health";
    /** JVM 资源：堆、GC 停顿、CPU、磁盘。 */
    public static final String JVM_RESOURCES = "jvm-resources";
    /** 尖刺专项：超过阈值的 tick。 */
    public static final String SPIKE_TICKS = "spike-ticks";
    /** 方法与插件级归因：spark 链接 artifact。 */
    public static final String ATTRIBUTION = "attribution";
    /** 网络 IO。 */
    public static final String NETWORK_IO = "network-io";
    /** 数据包处理线程。 */
    public static final String PACKET_THREAD = "packet-thread";

    public ProfileView {
        threadFocus = threadFocus == null ? List.of() : List.copyOf(threadFocus);
        primaryMetrics = primaryMetrics == null ? List.of() : List.copyOf(primaryMetrics);
        deferredMetrics = deferredMetrics == null ? List.of() : List.copyOf(deferredMetrics);
    }

    public static ProfileView of(ProfileReport report, ProfileViewpoint viewpoint) {
        return switch (viewpoint) {
            case DETECTION_DEVELOPER -> new ProfileView(viewpoint,
                    "Netty 数据包处理线程（Packet Decoder / Encoder）",
                    List.of("Netty IO 线程池", "Server thread（tick 主线程）"),
                    List.of(PACKET_THREAD, NETWORK_IO, SPIKE_TICKS),
                    List.of(TICK_HEALTH, ATTRIBUTION, JVM_RESOURCES),
                    report);
            case MOD_AUTHOR -> new ProfileView(viewpoint,
                    "Server thread（tick 主线程）",
                    List.of("Server thread（tick 主线程）", "Async Chunk / Worker 线程"),
                    List.of(TICK_HEALTH, SPIKE_TICKS, JVM_RESOURCES),
                    List.of(ATTRIBUTION, NETWORK_IO, PACKET_THREAD),
                    report);
        };
    }

    /** 该指标是否属于先展示的一档。 */
    public boolean surfacesPrimary(String metric) {
        return primaryMetrics.contains(metric);
    }
}
