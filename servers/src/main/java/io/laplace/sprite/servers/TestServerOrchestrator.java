package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.LogEntry;
import io.laplace.sprite.core.model.MetricSample;
import io.laplace.sprite.core.model.ScenarioRun;
import io.laplace.sprite.core.model.ServerHealth;
import io.laplace.sprite.core.model.TestRun;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 编排控制台（11.3 / 12.2）：起停实例集、汇总健康、汇总场景进度与日志流。
 *
 * <p>健康的 TPS / MSPT / 内存 / 连接数来自该实例挂载的 {@link MetricsAgent}；
 * 没挂真实数据源时用合成画像，控制器照样有数可看。</p>
 */
public final class TestServerOrchestrator {

    private final ServerDriver driver;
    private final Map<String, TestServerSpec> specs = new LinkedHashMap<>();
    private final Map<String, MetricsAgent> agents = new ConcurrentHashMap<>();
    private final List<LogEntry> logs = new CopyOnWriteArrayList<>();
    private final List<ScenarioRun> scenarioRuns = new CopyOnWriteArrayList<>();

    public TestServerOrchestrator(ServerDriver driver) {
        this.driver = driver;
    }

    public ServerDriver driver() {
        return driver;
    }

    /** 起停全部实例：环境不具备时驱动器自行降级，这里照常记录状态。 */
    public synchronized void startAll(Collection<TestServerSpec> requested) {
        for (TestServerSpec spec : requested) {
            specs.put(spec.instanceId(), spec);
            driver.start(spec);
            agents.putIfAbsent(spec.instanceId(), MetricsAgent.synthetic(spec.instanceId(),
                    SyntheticProfile.steady()));
            logs.add(LogEntry.info("orchestrator", "实例 %s 启动指令已下发（驱动 %s / 状态 %s）"
                    .formatted(spec.instanceId(), driver.name(), driver.state(spec.instanceId()))));
            if (!spec.mounts().isEmpty()) {
                String bindings = spec.mounts().stream().map(ServerMount::volumeArgument)
                        .collect(Collectors.joining(", "));
                logs.add(LogEntry.info("orchestrator", "实例 %s 目录绑定 %s"
                        .formatted(spec.instanceId(), bindings)));
            }
        }
    }

    public synchronized void stopAll() {
        for (String instanceId : specs.keySet()) {
            driver.stop(instanceId);
            MetricsAgent agent = agents.get(instanceId);
            if (agent != null) {
                agent.stop();
            }
            logs.add(LogEntry.info("orchestrator", "实例 %s 已停机（状态 %s）"
                    .formatted(instanceId, driver.state(instanceId))));
        }
    }

    /** 实例健康：指标取自挂载的采样代理，状态取自驱动器。 */
    public List<ServerHealth> health() {
        List<ServerHealth> health = new ArrayList<>(specs.size());
        for (TestServerSpec spec : specs.values()) {
            MetricsAgent agent = agents.get(spec.instanceId());
            MetricSample sample = agent == null
                    ? SyntheticProfile.steady().sample(0, Instant.now())
                    : agent.current();
            health.add(new ServerHealth(spec.instanceId(), spec.platform(), sample.tps(), sample.mspt(),
                    sample.heapUsedMb(), sample.heapMaxMb(), sample.connections(), 0, spec.pluginVersion(),
                    driver.status(spec.instanceId())));
        }
        return List.copyOf(health);
    }

    public List<ScenarioRun> scenarioRuns() {
        return List.copyOf(scenarioRuns);
    }

    public void attachScenarios(List<ScenarioRun> runs) {
        scenarioRuns.addAll(runs);
    }

    /** 记录一次运行结果（11.3 测试运行表的数据源之一）。 */
    public void recordRun(TestRun run) {
        logs.add(LogEntry.info("orchestrator", "运行 %s 结束: %s（误报 %d / 检出 %d）"
                .formatted(run.id(), run.status(), run.metrics().falsePositives(),
                        run.metrics().detectedSamples())));
    }

    public List<LogEntry> logs() {
        return List.copyOf(logs);
    }

    /** 日志尾部 n 条，logfmt 单行输出。 */
    public List<String> logTail(int n) {
        List<LogEntry> all = logs();
        int from = Math.max(0, all.size() - Math.max(0, n));
        return all.subList(from, all.size()).stream().map(LogEntry::toLogfmt).toList();
    }

    public Optional<MetricsAgent> metrics(String instanceId) {
        return Optional.ofNullable(agents.get(instanceId));
    }

    /** 挂载自定义采样代理（真实环境轨传入真实数据源）。 */
    public void attachMetrics(String instanceId, MetricsAgent agent) {
        agents.put(instanceId, agent);
    }

    public int instanceCount() {
        return specs.size();
    }

    /** 实例的目录绑定：混合端返回 plugins/ 与 mods/ 两个只读挂载点，其余实例为空。 */
    public List<ServerMount> mounts(String instanceId) {
        TestServerSpec spec = specs.get(instanceId);
        return spec == null ? List.of() : spec.mounts();
    }
}
