package io.laplace.sprite.loadtest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 分布式压测调度（升级实施文档 L-07）：把拆分后的节点命令真正下发，并汇总各节点判定。
 *
 * <p>本类不绑定 SSH / Docker 实现：命令只描述「在节点上跑哪个剧本、分到多少假人」，
 * 由 {@link NodeExecutor} 决定是走 SSH、Docker 还是测试替身。任一节点失败即整体失败。</p>
 */
public final class DistributedLoadOrchestrator {

    /** 节点命令执行接缝。 */
    @FunctionalInterface
    public interface NodeExecutor {
        Execution execute(DistributedLoadPlan.Node node, String command);
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
     * @param node    节点
     * @param command 实际下发的命令
     * @param execution 执行结果
     */
    public record NodeOutcome(DistributedLoadPlan.Node node, String command, Execution execution) {
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
    }

    private final NodeExecutor executor;

    public DistributedLoadOrchestrator(NodeExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public Dispatch dispatch(DistributedLoadPlan plan, LoadScenario scenario) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(scenario, "scenario");
        List<NodeOutcome> outcomes = new ArrayList<>();
        Map<String, LoadVerdict> verdicts = new LinkedHashMap<>();

        for (DistributedLoadPlan.Node node : plan.nodes()) {
            String command = command(node, scenario.id());
            Execution execution = executor.execute(node, command);
            LoadVerdict verdict = execution.verdict() != null ? execution.verdict()
                    : Execution.fromExitCode(execution.exitCode(), execution.output()).verdict();
            outcomes.add(new NodeOutcome(node, command, execution));
            verdicts.put(node.id(), verdict);
        }
        return new Dispatch(outcomes, DistributedLoadPlan.aggregate(verdicts));
    }

    /**
     * 节点命令：SSH 直接远程执行，Docker 用固定镜像名起一次性容器。
     *
     * <p>命令只表达参数，不负责引号转义；真实执行器应按目标 shell 规则处理。</p>
     */
    public static String command(DistributedLoadPlan.Node node, String scenarioId) {
        String base = "laplace loadtest --scenario " + scenarioId + " --bots " + node.bots()
                + " --node-id " + node.id();
        return switch (node.transport()) {
            case SSH -> "ssh " + node.host() + " " + base;
            case DOCKER -> "docker run --rm laplace-cli " + base;
        };
    }
}
