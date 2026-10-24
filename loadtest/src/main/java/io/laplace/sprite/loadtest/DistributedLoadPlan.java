package io.laplace.sprite.loadtest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 分布式压测编排（升级实施文档 L-07 / v2 L2-03）：把剧本并发拆到多台节点，再把各节点判定聚合回一份结论。
 *
 * <p>拆分按节点轮转分配，余数从前往后补，保证各节点之和等于剧本峰值并发。节点在健康检查里掉线时，
 * 用 {@link #redistribute(Set)} 把总并发重新分摊到存活节点，总数不变。聚合取最保守口径：
 * 任一存活节点失败即整体失败，破线并发取所有失败节点里最小的那个；被跳过的节点单独记录，
 * 聚合结论标记为降级。</p>
 */
public final class DistributedLoadPlan {

    /** 节点接入方式。 */
    public enum NodeTransport {
        SSH,
        DOCKER
    }

    /**
     * 一个压测节点。
     *
     * @param id        节点标识
     * @param transport 接入方式
     * @param host      主机地址
     * @param bots      该节点分到的假人数
     */
    public record Node(String id, NodeTransport transport, String host, int bots) {

        public Node {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("节点 id 不能为空");
            }
            transport = Objects.requireNonNull(transport, "transport");
            host = host == null ? "" : host;
            if (bots <= 0) {
                throw new IllegalArgumentException("节点假人数必须为正数: " + bots);
            }
        }
    }

    /**
     * 聚合结论。
     *
     * @param passed             是否所有存活节点都通过
     * @param breakingConcurrency 最早破线并发；没有失败节点时为 null
     * @param violations         带节点前缀的破线明细
     * @param nodes              参与聚合的节点数
     * @param degraded           是否有节点因故障被跳过
     * @param skippedNodes       被跳过的节点 id
     */
    public record AggregatedVerdict(boolean passed, Integer breakingConcurrency,
                                    List<String> violations, int nodes, boolean degraded,
                                    List<String> skippedNodes) {

        public AggregatedVerdict {
            violations = violations == null ? List.of() : List.copyOf(violations);
            skippedNodes = skippedNodes == null ? List.of() : List.copyOf(skippedNodes);
        }

        public AggregatedVerdict(boolean passed, Integer breakingConcurrency,
                                 List<String> violations, int nodes) {
            this(passed, breakingConcurrency, violations, nodes, false, List.of());
        }
    }

    private final String scenarioId;
    private final int totalBots;
    private final List<Node> nodes;

    private DistributedLoadPlan(String scenarioId, int totalBots, List<Node> nodes) {
        this.scenarioId = scenarioId;
        this.totalBots = totalBots;
        this.nodes = List.copyOf(nodes);
    }

    /** 按剧本峰值并发拆分到节点；节点数多于并发数时直接拒绝，不生成空节点。 */
    public static DistributedLoadPlan split(LoadScenario scenario, List<Node> nodes) {
        Objects.requireNonNull(scenario, "scenario");
        return new DistributedLoadPlan(scenario.id(), scenario.maxConcurrency(),
                distribute(scenario.maxConcurrency(), nodes));
    }

    private static List<Node> distribute(int totalBots, List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("分布式压测至少要一个节点");
        }
        if (nodes.size() > totalBots) {
            throw new IllegalArgumentException("节点数 %d 超过峰值并发 %d".formatted(nodes.size(), totalBots));
        }
        int base = totalBots / nodes.size();
        int remainder = totalBots % nodes.size();
        List<Node> split = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            int bots = base + (i < remainder ? 1 : 0);
            split.add(new Node(node.id(), node.transport(), node.host(), bots));
        }
        return List.copyOf(split);
    }

    public String scenarioId() {
        return scenarioId;
    }

    public int totalBots() {
        return totalBots;
    }

    public List<Node> nodes() {
        return nodes;
    }

    /** 节点 id → 假人数，便于渲染编排脚本。 */
    public Map<String, Integer> botsByNode() {
        Map<String, Integer> byNode = new LinkedHashMap<>();
        nodes.forEach(node -> byNode.put(node.id(), node.bots()));
        return Map.copyOf(byNode);
    }

    /**
     * 健康检查后重排：跳过不可用节点，把总并发重新分摊给存活节点。
     *
     * <p>总数必须保持不变，否则压测峰值会悄悄缩水，判定结果不可比。</p>
     *
     * @param unavailableNodeIds 健康检查失败的节点 id
     * @throws IllegalArgumentException 所有节点都不可用
     */
    public DistributedLoadPlan redistribute(Set<String> unavailableNodeIds) {
        Set<String> unavailable = unavailableNodeIds == null ? Set.of() : Set.copyOf(unavailableNodeIds);
        List<Node> healthy = nodes.stream().filter(node -> !unavailable.contains(node.id())).toList();
        if (healthy.isEmpty()) {
            throw new IllegalArgumentException("所有节点均不可用，无法降级执行");
        }
        if (healthy.size() == nodes.size()) {
            return this;
        }
        return new DistributedLoadPlan(scenarioId, totalBots, distribute(totalBots, healthy));
    }

    /**
     * 聚合各节点判定。
     *
     * @param verdicts 节点 id → 该节点的压测判定
     */
    public static AggregatedVerdict aggregate(Map<String, LoadVerdict> verdicts) {
        return aggregate(verdicts, Set.of());
    }

    /**
     * 聚合各存活节点判定，并记录被跳过的节点。
     *
     * @param verdicts     节点 id → 该节点的压测判定
     * @param skippedNodes 健康检查或重试后仍不可用的节点
     */
    public static AggregatedVerdict aggregate(Map<String, LoadVerdict> verdicts, Set<String> skippedNodes) {
        List<String> skipped = skippedNodes == null ? List.of() : List.copyOf(skippedNodes);
        boolean degraded = !skipped.isEmpty();
        if (verdicts == null || verdicts.isEmpty()) {
            return new AggregatedVerdict(false, null,
                    List.of("没有节点判定结果，无法聚合"), 0, degraded, skipped);
        }
        List<String> violations = new ArrayList<>();
        Integer breaking = null;
        boolean passed = true;
        for (Map.Entry<String, LoadVerdict> entry : verdicts.entrySet()) {
            LoadVerdict verdict = entry.getValue();
            if (verdict == null) {
                passed = false;
                violations.add(entry.getKey() + "：缺少判定结果");
                continue;
            }
            if (!verdict.passed()) {
                passed = false;
                if (verdict.breakingConcurrency() != null
                        && (breaking == null || verdict.breakingConcurrency() < breaking)) {
                    breaking = verdict.breakingConcurrency();
                }
                verdict.violations().forEach(line -> violations.add(entry.getKey() + "：" + line));
            }
        }
        return new AggregatedVerdict(passed, breaking, violations, verdicts.size(), degraded, skipped);
    }
}
