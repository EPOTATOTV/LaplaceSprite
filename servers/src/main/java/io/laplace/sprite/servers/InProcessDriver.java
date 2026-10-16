package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.RunStatus;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 开发轨驱动器（12.1）：不起真实服务端，只维护实例状态与日志，用于进程内快迭代。
 */
public final class InProcessDriver implements ServerDriver {

    private static final int LOG_CAPACITY = 500;

    private final Map<String, Boolean> running = new ConcurrentHashMap<>();
    private final Map<String, Boolean> known = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> logs = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "in-process";
    }

    @Override
    public void start(TestServerSpec spec) {
        known.put(spec.instanceId(), Boolean.TRUE);
        running.put(spec.instanceId(), Boolean.TRUE);
        log(spec.instanceId(), "进程内开发轨启动: 平台 %s / 插件 %s（无真实服务端）"
                .formatted(spec.platform().id(), spec.pluginVersion()));
    }

    @Override
    public void stop(String instanceId) {
        running.put(instanceId, Boolean.FALSE);
        log(instanceId, "进程内开发轨停机");
    }

    @Override
    public boolean running(String instanceId) {
        return Boolean.TRUE.equals(running.get(instanceId));
    }

    @Override
    public List<String> tail(String instanceId, int lines) {
        Deque<String> entries = logs.getOrDefault(instanceId, new ArrayDeque<>());
        List<String> all = List.copyOf(entries);
        int from = Math.max(0, all.size() - Math.max(0, lines));
        return all.subList(from, all.size());
    }

    @Override
    public RunStatus status(String instanceId) {
        if (running(instanceId)) {
            return RunStatus.RUNNING;
        }
        return Boolean.TRUE.equals(known.get(instanceId)) ? RunStatus.ABORTED : RunStatus.SKIPPED;
    }

    @Override
    public DriverState state(String instanceId) {
        return running(instanceId) ? DriverState.RUNNING : DriverState.STOPPED;
    }

    /** 供编排控制台把外部日志并进同一通道。 */
    public void log(String instanceId, String line) {
        Deque<String> entries = logs.computeIfAbsent(instanceId, key -> new ArrayDeque<>());
        synchronized (entries) {
            entries.addLast(line);
            while (entries.size() > LOG_CAPACITY) {
                entries.removeFirst();
            }
        }
    }
}
