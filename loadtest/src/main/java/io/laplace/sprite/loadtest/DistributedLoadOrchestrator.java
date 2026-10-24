package io.laplace.sprite.loadtest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 分布式压测调度（升级实施文档 L-07 / v2 L2-03）：把拆分后的节点命令真正下发，并汇总各节点判定。
 *
 * <p>本类不绑定 SSH / Docker 实现：命令只描述「在节点上跑哪个剧本、分到多少假人」，
 * 由 {@link NodeExecutor} 决定是走 SSH、Docker 还是测试替身。下发前先做节点健康检查，
 * 不可用节点被剔除并把并发重排给存活节点；执行期间抛异常的节点按 {@code maxAttempts}
 * 重试，仍失败则降级跳过，聚合结论标记 degraded，不会把基础设施抖动算成性能破线。</p>
 */
public final class DistributedLoadOrchestrator {

    /** 节点命令执行接缝。 */
    @FunctionalInterface
    public interface NodeExecutor {
        Execution execute(DistributedLoadPlan.Node node, String command);
    }

    /** 节点健康检查接缝；返回 false 的节点不参与本轮压测。 */
    @FunctionalInterface
    public interface NodeHealthCheck {
        boolean healthy(DistributedLoadPlan.Node node);
    }

    /**
     * 一次节点执行结果。
     *
     * @param exitCode 退出码
     * @param output   输出摘要
     * @param verdict  该节点的压测判定
     */
    public record Execution(int exitCode, String output, LoadVerdict verdict) {

        public Execution {
            output = output == null ? "" : output;
        }

        /** 只拿到退出码时按「0 通过、非 0 失败」构造判定。 */
        public static Execution fromExitCode(int exitCode, String output) {
            String text = output == null ? "" : output;
            LoadVerdict verdict = exitCode == 0
                    ? new LoadVerdict(true, null, List.of(), List.of())
                    : new LoadVerdict(false, null,
                            List.of("节点退出码 " + exitCode + (text.isBlank() ? "" : "：" + text)), List.of());
            return new Execution(exitCode, text, verdict);
        }
    }

    /**
     * 一个节点的执行记录。
     *
     * @param node       节点
     * @param command    实际下发的命令；健康检查阶段被跳过时为空
     * @param execution  执行结果
     * @param attempts   实际尝试次数
     * @param skipped    是否因健康检查或重试耗尽而跳过
     */
    public record NodeOutcome(DistributedLoadPlan.Node node, String command, Execution execution,
                              int attempts, boolean skipped) {
    }

    /**
     * 一次分布式调度结果。
     *
     * @param outcomes  逐节点记录
     * @param aggregate 聚合判定
     */
    public record Dispatch(List<NodeOutcome> outcomes, DistributedLoadPlan.AggregatedVerdict aggregate) {

        public Dispatch {
            outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        }

        public boolean passed() {
            return aggregate != null && aggregate.passed();
        }

        /** 是否有节点被降级跳过。 */
        public boolean degraded() {
            return aggregate != null && aggregate.degraded();
        }

        public List<String> skippedNodes() {
            return aggregate == null ? List.of() : aggregate.skippedNodes();
        }
    }

    /** 默认重试次数：节点命令抛异常时多给一次机会。 */
    private static final int DEFAULT_MAX_ATTEMPTS = 2;

    private final NodeExecutor executor;
    private final NodeHealthCheck healthCheck;
    private final int maxAttempts;

    public DistributedLoadOrchestrator(NodeExecutor executor) {
        this(executor, node -> true, DEFAULT_MAX_ATTEMPTS);
    }

    public DistributedLoadOrchestrator(NodeExecutor executor, NodeHealthCheck healthCheck, int maxAttempts) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.healthCheck = Objects.requireNonNull(healthCheck, "healthCheck");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("重试次数必须至少为 1: " + maxAttempts);
        }
        this.maxAttempts = maxAttempts;
    }

    public Dispatch dispatch(DistributedLoadPlan plan, LoadScenario scenario) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(scenario, "scenario");
        List<NodeOutcome> outcomes = new ArrayList<>();
        Set<String> skipped = new LinkedHashSet<>();

        for (DistributedLoadPlan.Node node : plan.nodes()) {
            if (healthCheck.healthy(node)) {
                continue;
            }
            skipped.add(node.id());
            outcomes.add(new NodeOutcome(node, "",
                    Execution.fromExitCode(-2, "健康检查失败，节点已跳过"), 1, true));
        }

        DistributedLoadPlan effective;
        try {
            effective = plan.redistribute(skipped);
        } catch (IllegalArgumentException ex) {
            return new Dispatch(outcomes, DistributedLoadPlan.aggregate(Map.of(), skipped));
        }

        Map<String, LoadVerdict> verdicts = new LinkedHashMap<>();
        for (DistributedLoadPlan.Node node : effective.nodes()) {
            String command = command(node, scenario.id());
            Attempt attempt = execute(node, command);
            if (attempt.execution() == null) {
                skipped.add(node.id());
                outcomes.add(new NodeOutcome(node, command,
                        Execution.fromExitCode(-2, "重试 " + maxAttempts + " 次仍失败：" + attempt.failure()),
                        maxAttempts, true));
                continue;
            }
            Execution execution = attempt.execution();
            LoadVerdict verdict = execution.verdict() != null ? execution.verdict()
                    : Execution.fromExitCode(execution.exitCode(), execution.output()).verdict();
            outcomes.add(new NodeOutcome(node, command, execution, attempt.attempts(), false));
            verdicts.put(node.id(), verdict);
        }
        return new Dispatch(outcomes, DistributedLoadPlan.aggregate(verdicts, skipped));
    }

    private Attempt execute(DistributedLoadPlan.Node node, String command) {
        String failure = "";
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return new Attempt(executor.execute(node, command), attempt, null);
            } catch (RuntimeException ex) {
                failure = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            }
        }
        return new Attempt(null, maxAttempts, failure);
    }

    private record Attempt(Execution execution, int attempts, String failure) {
    }

    /**
     * 节点命令：SSH 直接远程执行，Docker 用固定镜像名起一次性容器。
     *
     * <p>命令只表达参数，不负责引号转义；真实执行器应按目标 shell 规则处理。</p>
     */
    public static String command(DistributedLoadPlan.Node node, String scenarioId) {
        String base = "laplace loadtest --scenario " + safeToken(scenarioId) + " --bots " + node.bots()
                + " --node-id " + safeToken(node.id());
        return switch (node.transport()) {
            case SSH -> "ssh " + safeToken(node.host()) + " " + base;
            case DOCKER -> "docker run --rm laplace-cli " + base;
        };
    }

    /** 只允许主机名 / 标识符里常见的字符，避免节点清单把 shell 元字符带进命令。 */
    private static String safeToken(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("节点命令参数不能为空");
        }
        if (!value.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("节点命令参数包含不安全字符: " + value);
        }
        return value;
    }
}
