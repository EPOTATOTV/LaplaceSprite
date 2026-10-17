package io.laplace.sprite.loadtest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 网络层 bot 启动器（15.9）：把剧本渲染成 Node 调用，并交给 {@link BotRunner} 执行。
 *
 * <p>本类不附带、也不下载 Mineflayer 脚本：脚本与依赖由使用方准备。node 或脚本缺失时
 * 只返回空会话列表并给中文说明，不伪造在线会话。</p>
 */
public final class MineflayerLauncher {

    public static final String DEFAULT_NODE = "node";
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 25565;

    /** 探测可执行文件是否存在；抽出来是为了让离线测试不依赖本机是否装了 node。 */
    @FunctionalInterface
    public interface ExecutableProbe {
        boolean available(String executable);
    }

    private final Path script;
    private final String nodeExecutable;
    private final String host;
    private final int port;
    private final BotRunner runner;
    private final ExecutableProbe probe;
    private String note = "";

    public MineflayerLauncher(Path script, BotRunner runner) {
        this(script, DEFAULT_NODE, DEFAULT_HOST, DEFAULT_PORT, runner, MineflayerLauncher::probeProcess);
    }

    public MineflayerLauncher(Path script, String nodeExecutable, String host, int port, BotRunner runner,
                              ExecutableProbe probe) {
        this.script = Objects.requireNonNull(script, "script");
        this.nodeExecutable = Objects.requireNonNull(nodeExecutable, "nodeExecutable");
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.runner = Objects.requireNonNull(runner, "runner");
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    /** node 与脚本都在才认为可启动。 */
    public boolean available() {
        return Files.isRegularFile(script) && probe.available(nodeExecutable);
    }

    /**
     * 拉起会话。
     *
     * <p>无论走哪条路径都只返回真实的启停结果：环境不具备就是空列表，note 写明原因。</p>
     */
    public List<BotSession> start(LoadScenario scenario) {
        if (!Files.isRegularFile(script)) {
            note = "跳过 Mineflayer 压测：Node 脚本不存在（%s），未启动任何登录会话".formatted(script);
            return List.of();
        }
        if (!probe.available(nodeExecutable)) {
            note = "跳过 Mineflayer 压测：未找到 node 可执行文件（%s），未启动任何登录会话"
                    .formatted(nodeExecutable);
            return List.of();
        }
        List<BotSession> sessions = runner.start(scenario);
        note = "已提交 Node 调用：%s；BotRunner 返回会话数 %d（是否真实登录由 runner 实现决定）"
                .formatted(render(scenario), sessions.size());
        return sessions;
    }

    /** 参数向量，便于调用方直接交给 ProcessBuilder。 */
    public List<String> command(LoadScenario scenario) {
        List<String> command = new ArrayList<>();
        command.add(nodeExecutable);
        command.add(script.toString());
        command.add("--host");
        command.add(host);
        command.add("--port");
        command.add(Integer.toString(port));
        command.add("--count");
        command.add(Integer.toString(scenario.maxConcurrency()));
        command.add("--actions");
        command.add(actionsArgument(scenario));
        command.add("--duration-ticks");
        command.add(Integer.toString(scenario.durationTicks()));
        command.add("--ramp-start");
        command.add(Integer.toString(scenario.ramp().startConcurrency()));
        command.add("--ramp-step");
        command.add(Integer.toString(scenario.ramp().step()));
        command.add("--ramp-step-ticks");
        command.add(Integer.toString(scenario.ramp().stepTicks()));
        return List.copyOf(command);
    }

    /** 单行渲染，写日志与报告用。 */
    public String render(LoadScenario scenario) {
        return String.join(" ", command(scenario));
    }

    /** 最近一次启动的说明；中文，环境不具备时说明缺什么。 */
    public String note() {
        return note;
    }

    private static String actionsArgument(LoadScenario scenario) {
        return scenario.actions().stream()
                .map(action -> action.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(","));
    }

    private static boolean probeProcess(String executable) {
        try {
            Process process = new ProcessBuilder(executable, "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
