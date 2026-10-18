package io.laplace.sprite.loadtest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 分布式压测编排（升级实施文档 L-07）：把剧本并发拆到多台节点，再把各节点判定聚合回一份结论。
 *
 * <p>拆分按节点轮转分配，余数从前往后补，保证各节点之和等于剧本峰值并发。聚合取最保守口径：
 * 任一节点失败即整体失败，破线并发取所有失败节点里最小的那个。</p>
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
     * @param passed             是否所有节点都通过
     * @param breakingConcurrency 最早破线并发；没有失败节点时为 null
     * @param violations         带节点前缀的破线明细
     * @param nodes              参与聚合的节点数
     */
    public record AggregatedVerdict(boolean passed, Integer breakingConcurrency,
                                    List<String> violations, int nodes) {

        public AggregatedVerdict {
            violations = violations == null ? List.of() : List.copyOf(violations);
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
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("分布式压测至少要一个节点");
        }
        int total = scenario.maxConcurrency();
        if (nodes.size() > total) {
            throw new IllegalArgumentException("节点数 %d 超过峰值并发 %d".formatted(nodes.size(), total));
        }
        int base = total / nodes.size();
        int remainder = total % nodes.size();
        List<Node> split = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            int bots = base + (i < remainder ? 1 : 0);
            split.add(new Node(node.id(), node.transport(), node.host(), bots));
        }
        return new DistributedLoadPlan(scenario.id(), total, split);
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
     * 聚合各节点判定。
     *
     * @param verdicts 节点 id → 该节点的压测判定
     */
    public static AggregatedVerdict aggregate(Map<String, LoadVerdict> verdicts) {
        if (verdicts == null || verdicts.isEmpty()) {
            return new AggregatedVerdict(false, null,
                    List.of("没有节点判定结果，无法聚合"), 0);
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
        return new AggregatedVerdict(passed, breaking, violations, verdicts.size());
    }
}
