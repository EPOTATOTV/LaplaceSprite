package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.MetricSample;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 指标采集（12.3）：按可配置间隔采 TPS / MSPT / 堆 / GC 停顿 / CPU / 内存 / 连接数 / 区块数。
 *
 * <p>没有真实数据源时用 {@link SyntheticProfile} 产出确定性序列；{@link #sampleNow()} 手动推进一步，
 * 测试因此不必依赖真实时钟。</p>
 */
public final class MetricsAgent implements AutoCloseable {

    /** 5 秒默认采样间隔。 */
    public static final long DEFAULT_INTERVAL_MS = 5000L;

    private static final Instant SYNTHETIC_ORIGIN = Instant.parse("2026-01-01T00:00:00Z");

    /** 数据源：第 step 次采样。 */
    public interface MetricSource {
        MetricSample sample(int step);
    }

    private final String instanceId;
    private final long intervalMs;
    private final MetricSource source;
    private final List<MetricSample> history = new CopyOnWriteArrayList<>();
    private final AtomicInteger step = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean();
    private ScheduledExecutorService scheduler;

    public MetricsAgent(String instanceId, long intervalMs, MetricSource source) {
        this.instanceId = instanceId;
        this.intervalMs = intervalMs;
        this.source = source;
    }

    /** 离线合成代理：间隔默认 5s，时间戳按固定起点等距推进。 */
    public static MetricsAgent synthetic(String instanceId, SyntheticProfile profile) {
        return synthetic(instanceId, DEFAULT_INTERVAL_MS, profile);
    }

    public static MetricsAgent synthetic(String instanceId, long intervalMs, SyntheticProfile profile) {
        return new MetricsAgent(instanceId, intervalMs,
                step -> profile.sample(step, SYNTHETIC_ORIGIN.plusSeconds(intervalMs / 1000L * step)));
    }

    public String instanceId() {
        return instanceId;
    }

    public long intervalMs() {
        return intervalMs;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "metrics-" + instanceId);
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::sampleNow, 0L, intervalMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running.set(false);
        ScheduledExecutorService current = scheduler;
        scheduler = null;
        if (current != null) {
            current.shutdownNow();
        }
    }

    @Override
    public void close() {
        stop();
    }

    public boolean running() {
        return running.get();
    }

    /** 手动推进一步采样（测试与门禁复用）。 */
    public MetricSample sampleNow() {
        MetricSample sample = source.sample(step.getAndIncrement());
        history.add(sample);
        return sample;
    }

    public MetricSample current() {
        if (history.isEmpty()) {
            return sampleNow();
        }
        return history.get(history.size() - 1);
    }

    public List<MetricSample> history() {
        return List.copyOf(history);
    }

    /** 拐点采样（12.3 ⑤）：TPS < 20.0 或 MSPT > 50ms。 */
    public List<MetricSample> kneePoints() {
        return history.stream().filter(MetricSample::knee).toList();
    }

    public int sampleCount() {
        return history.size();
    }
}
