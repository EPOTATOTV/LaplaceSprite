package io.laplace.sprite.cli;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.loadtest.DistributedLoadPlan;
import io.laplace.sprite.loadtest.LoadScenario;
import io.laplace.sprite.loadtest.LoadScenarioParser;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code laplace loadtest}：15.9 假玩家压测轨的计划渲染。
 *
 * <p>只解析并渲染压测剧本与门禁阈值；真正的 TPS / MSPT 采样要连真实服务端与 spark，
 * 命令不会伪造采样结果。</p>
 */
@Command(name = "loadtest",
        description = "假玩家压测轨：压测剧本解析、bot 后端调用渲染、TPS/MSPT 门禁口径",
        mixinStandardHelpOptions = true)
final class LoadtestCommand implements Callable<Integer> {

    @Option(names = "--scenario", required = true, description = "压测剧本 JSON")
    Path scenario;

    @Option(names = "--nodes", description = "分布式节点 JSON（Node 数组），提供后输出拆分计划")
    Path nodes;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        if (!Files.isRegularFile(scenario)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "压测剧本不存在: " + scenario.toAbsolutePath());
        }
        var writer = spec.commandLine().getOut();
        LoadScenario parsed = new LoadScenarioParser().parse(scenario);

        writer.println("假玩家压测轨（15.9）");
        writer.println("  剧本 " + parsed.id() + " · 后端 " + parsed.backend()
                + " · 时长 " + parsed.durationTicks() + " tick");
        writer.println("  动作 " + parsed.actions());
        writer.printf("  爬坡 起始 %d · 步长 %d · 每步 %d tick · 上限 %d%n",
                parsed.ramp().startConcurrency(), parsed.ramp().step(), parsed.ramp().stepTicks(),
                parsed.ramp().maxConcurrency());
        if (parsed.note() != null && !parsed.note().isBlank()) {
            writer.println("  说明 " + parsed.note());
        }
        writer.println("  门禁：稳态 TPS ≥19.5 且 P95 MSPT ≤50ms（LoadGate 判定）");
        if (nodes != null) {
            DistributedLoadPlan plan = DistributedLoadPlan.split(parsed, readNodes(nodes));
            writer.printf("  分布式拆分：%d 个假人分到 %d 个节点%n", plan.totalBots(), plan.nodes().size());
            plan.nodes().forEach(node -> writer.printf("    %s（%s %s）— %d%n",
                    node.id(), node.transport(), node.host(), node.bots()));
            writer.println("  聚合口径：任一节点失败即整体失败，破线并发取失败节点中的最小值");
        }
        writer.println("  采样依赖真实服务端与 spark / MCBenchmark，本命令只渲染计划，不产出伪造采样");
        return 0;
    }

    private static List<DistributedLoadPlan.Node> readNodes(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new CommandLine.ParameterException(new CommandLine(new Object()),
                    "节点清单不存在: " + file.toAbsolutePath());
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return List.of(Json.jsonMapper().readValue(content, DistributedLoadPlan.Node[].class));
        } catch (IOException ex) {
            throw new UncheckedIOException("解析节点清单失败: " + file, ex);
        }
    }
}
