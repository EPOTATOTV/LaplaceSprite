package io.laplace.sprite.adapters.deps;

import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactDependency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 依赖三语义统一排序（14.2）。
 *
 * <p>HARD：依赖必须存在且排在被依赖者之后；SOFT：只影响顺序，缺失时静默降级；
 * REVERSE（loadbefore）：声明的目标要排在本产物之后，即反向边。</p>
 *
 * <p>内部按拓扑排序实现，出环或硬依赖缺失都直接失败——装配阶段发现问题比运行期发现便宜。</p>
 */
public final class DependencyOrderer {

    /** 硬依赖缺失。 */
    public static final class MissingHardDependencyException extends IllegalStateException {
        MissingHardDependencyException(String message) {
            super(message);
        }
    }

    /** 依赖成环。 */
    public static final class DependencyCycleException extends IllegalStateException {
        DependencyCycleException(String message) {
            super(message);
        }
    }

    /** 排序结果：硬依赖在前、软依赖只调整顺序、loadbefore 反向。 */
    public List<Artifact> order(List<Artifact> artifacts) {
        Map<String, Artifact> byId = new LinkedHashMap<>();
        for (Artifact artifact : artifacts) {
            byId.putIfAbsent(artifact.id(), artifact);
        }
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        for (Artifact artifact : byId.values()) {
            edges.computeIfAbsent(artifact.id(), k -> new LinkedHashSet<>());
        }
        for (Artifact artifact : byId.values()) {
            for (ArtifactDependency dependency : artifact.dependencies()) {
                String target = dependency.id();
                switch (dependency.semantics()) {
                    case HARD -> {
                        if (!byId.containsKey(target)) {
                            throw new MissingHardDependencyException(
                                    "产物「%s」的硬依赖「%s」未在装配集合中".formatted(artifact.id(), target));
                        }
                        edges.get(target).add(artifact.id());
                    }
                    case SOFT -> {
                        if (byId.containsKey(target)) {
                            edges.get(target).add(artifact.id());
                        }
                    }
                    case REVERSE -> {
                        if (byId.containsKey(target)) {
                            edges.get(artifact.id()).add(target);
                        }
                    }
                }
            }
        }
        List<String> sorted = topological(edges);
        List<Artifact> result = new ArrayList<>(sorted.size());
        sorted.forEach(id -> result.add(byId.get(id)));
        return List.copyOf(result);
    }

    /** 装配前的人可读检查项：硬依赖缺失、软依赖降级、loadbefore 目标缺失。 */
    public List<String> verify(List<Artifact> artifacts) {
        Set<String> ids = new HashSet<>();
        artifacts.forEach(a -> ids.add(a.id()));
        List<String> notices = new ArrayList<>();
        for (Artifact artifact : artifacts) {
            for (ArtifactDependency dependency : artifact.dependencies()) {
                String target = dependency.id();
                if (ids.contains(target)) {
                    continue;
                }
                switch (dependency.semantics()) {
                    case HARD -> notices.add("硬依赖缺失: %s → %s".formatted(artifact.id(), target));
                    case SOFT -> notices.add("软依赖降级: %s → %s（不影响装配）".formatted(artifact.id(), target));
                    case REVERSE -> notices.add("loadbefore 目标缺失: %s 声明早于 %s".formatted(artifact.id(), target));
                }
            }
        }
        return orderQuietly(artifacts, notices);
    }

    private List<String> orderQuietly(List<Artifact> artifacts, List<String> notices) {
        try {
            order(artifacts);
        } catch (MissingHardDependencyException ex) {
            notices.add("排序失败: " + ex.getMessage());
        } catch (DependencyCycleException ex) {
            notices.add("排序失败: " + ex.getMessage());
        }
        return List.copyOf(notices);
    }

    /** Kahn 拓扑排序；无入度节点按 id 字典序取出，保证同输入同输出。 */
    private List<String> topological(Map<String, Set<String>> edges) {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        edges.keySet().forEach(id -> indegree.put(id, 0));
        edges.forEach((from, tos) -> tos.forEach(to -> indegree.merge(to, 1, Integer::sum)));

        Deque<String> ready = new ArrayDeque<>();
        indegree.entrySet().stream()
                .filter(e -> e.getValue() == 0)
                .map(Map.Entry::getKey)
                .sorted()
                .forEach(ready::add);

        List<String> sorted = new ArrayList<>(indegree.size());
        while (!ready.isEmpty()) {
            String id = ready.poll();
            sorted.add(id);
            List<String> next = new ArrayList<>(edges.get(id));
            next.sort(String::compareTo);
            for (String to : next) {
                int left = indegree.merge(to, -1, Integer::sum);
                if (left == 0) {
                    ready.add(to);
                }
            }
        }
        if (sorted.size() != indegree.size()) {
            Set<String> remaining = new HashSet<>(indegree.keySet());
            sorted.forEach(remaining::remove);
            throw new DependencyCycleException("依赖成环，涉及产物: " + String.join(", ", sortedIds(remaining)));
        }
        return sorted;
    }

    private static List<String> sortedIds(Set<String> remaining) {
        List<String> ids = new ArrayList<>(remaining);
        ids.sort(String::compareTo);
        return ids;
    }

    /** 供测试与报告复用：把「顺序」表达成 id 列表。 */
    public List<String> orderIds(List<Artifact> artifacts) {
        return order(artifacts).stream().map(Artifact::id).toList();
    }
}
