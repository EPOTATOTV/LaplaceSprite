package io.laplace.sprite.cli;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.loadtest.DistributedLoadOrchestrator;
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
import java.util.concurrent.TimeUnit;

/**
 * {@code laplace loadtest}：15.9 假玩家压测轨的计划渲染。
 *
 * <p>默认只解析并渲染压测剧本与门禁阈值；提供 {@code --nodes --execute} 时按节点下发命令，
 * 并把退出码聚合成一份结论。真正的 TPS / MSPT 采样仍要连真实服务端与 spark，命令不会伪造采样。</p>
 */
@Command(name = "loadtest",
        description = "假玩家压测轨：压测剧本解析、bot 后端调用渲染、TPS/MSPT 门禁口径",
        mixinStandardHelpOptions = true)
final class LoadtestCommand implements Callable<Integer> {

    @Option(names = "--scenario", required = true, description = "压测剧本 JSON")
    Path scenario;

    @Option(names = "--nodes", description = "分布式节点 JSON（Node 数组），提供后输出拆分计划")
    Path nodes;

    @Option(names = "--node-id", description = "当前节点 id（节点侧调用时使用）")
    String nodeId;

    @Option(names = "--bots", defaultValue = "0", description = "当前节点分到的假人数（节点侧调用时使用）")
    int bots;

    @Option(names = "--execute", description = "实际下发节点命令并聚合判定（需要 --nodes）")
    boolean execute;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        if (!Files.isRegularFile(scenario)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "压测剧本不存在: " + scenario.toAbsolutePath());
        }
        if (execute && nodes == null) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "--execute 需要 --nodes 提供分布式节点清单");
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
        if (nodeId != null) {
            writer.printf("  当前节点 %s · 分到假人 %d%n", nodeId, bots);
        }
        if (nodes != null) {
            DistributedLoadPlan plan = DistributedLoadPlan.split(parsed, readNodes(nodes));
            writer.printf("  分布式拆分：%d 个假人分到 %d 个节点%n", plan.totalBots(), plan.nodes().size());
            plan.nodes().forEach(node -> writer.printf("    %s（%s %s）— %d%n",
                    node.id(), node.transport(), node.host(), node.bots()));
            writer.println("  聚合口径：任一节点失败即整体失败，破线并发取失败节点中的最小值");
            if (execute) {
                DistributedLoadOrchestrator.Dispatch dispatch = new DistributedLoadOrchestrator(
                        (node, command) -> runNode(node, command)).dispatch(plan, parsed);
                dispatch.outcomes().forEach(outcome -> writer.printf("  [%s] %s — 退出码 %d%n",
                        outcome.execution().exitCode() == 0 ? "PASS" : "FAIL",
                        outcome.node().id(), outcome.execution().exitCode()));
                dispatch.aggregate().violations().forEach(line -> writer.println("  - " + line));
                writer.println("  分布式结果：" + (dispatch.passed() ? "通过" : "未通过"));
                return dispatch.passed() ? 0 : 1;
            }
        }
        writer.println("  采样依赖真实服务端与 spark / MCBenchmark，本命令只渲染计划，不产出伪造采样");
        return 0;
    }

    /** 节点命令执行：用系统 shell 下发，输出落临时文件避免管道写满。 */
    private static DistributedLoadOrchestrator.Execution runNode(DistributedLoadPlan.Node node,
                                                                 String command) {
        Path output = null;
        try {
            output = Files.createTempFile("laplace-loadtest-", ".log");
            boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
            String shell = windows
                    ? System.getenv().getOrDefault("ComSpec", "C:\\Windows\\System32\\cmd.exe")
                    : "/bin/sh";
            ProcessBuilder builder = windows
                    ? new ProcessBuilder(shell, "/c", command)
                    : new ProcessBuilder(shell, "-c", command);
            Process process = builder.redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();
            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
            }
            int exitCode = finished ? process.exitValue() : -1;
            String text = Files.readString(output, StandardCharsets.UTF_8);
            String summary = String.join(" | ", text.lines().limit(20).toList());
            return DistributedLoadOrchestrator.Execution.fromExitCode(exitCode, summary);
        } catch (IOException ex) {
            return DistributedLoadOrchestrator.Execution.fromExitCode(-1,
                    node.id() + " 下发失败: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return DistributedLoadOrchestrator.Execution.fromExitCode(-1, node.id() + " 执行被中断");
        } finally {
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException ignored) {
                    // 临时文件清理失败不影响判定
                }
            }
        }
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
