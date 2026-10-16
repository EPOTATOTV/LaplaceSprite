package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.RunStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地进程轨驱动器（12.1）：用户给出真实服务端 jar 时用 {@code java -jar} 直接拉起。
 *
 * <p>jar 不存在时按 unavailable 处理，不抛异常——开发机大多没有 jar，这条轨本来就可选。</p>
 */
public final class LocalProcessDriver implements ServerDriver {

    private static final int LOG_CAPACITY = 500;

    private final Path jarPath;
    private final String javaExecutable;
    private final Map<String, Process> processes = new ConcurrentHashMap<>();
    private final Map<String, DriverState> states = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> logs = new ConcurrentHashMap<>();

    public LocalProcessDriver(Path jarPath) {
        this(jarPath, defaultJavaExecutable());
    }

    public LocalProcessDriver(Path jarPath, String javaExecutable) {
        this.jarPath = jarPath;
        this.javaExecutable = javaExecutable;
    }

    public static String defaultJavaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    @Override
    public String name() {
        return "local-process";
    }

    @Override
    public boolean available() {
        return jarPath != null && Files.isRegularFile(jarPath);
    }

    @Override
    public void start(TestServerSpec spec) {
        if (!available()) {
            states.put(spec.instanceId(), DriverState.UNAVAILABLE);
            log(spec.instanceId(), "unavailable: 未提供可用的服务端 jar（%s），回落到进程内轨".formatted(jarPath));
            return;
        }
        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        command.addAll(spec.jvmArgsWithMemory());
        command.add("-jar");
        command.add(jarPath.toString());
        command.add("--port");
        command.add(Integer.toString(spec.port()));
        try {
            Process process = new ProcessBuilder(command)
                    .directory(jarPath.getParent().toFile())
                    .redirectErrorStream(true)
                    .start();
            processes.put(spec.instanceId(), process);
            states.put(spec.instanceId(), DriverState.RUNNING);
            pump(spec.instanceId(), process);
            log(spec.instanceId(), "已启动服务端进程: " + String.join(" ", command));
        } catch (IOException ex) {
            states.put(spec.instanceId(), DriverState.FAILED);
            log(spec.instanceId(), "启动失败: " + ex.getMessage());
        }
    }

    @Override
    public void stop(String instanceId) {
        Process process = processes.remove(instanceId);
        if (process != null) {
            process.destroy();
        }
        states.put(instanceId, DriverState.STOPPED);
        log(instanceId, "服务端进程已停机");
    }

    @Override
    public boolean running(String instanceId) {
        Process process = processes.get(instanceId);
        return process != null && process.isAlive();
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
        DriverState state = states.getOrDefault(instanceId, DriverState.UNAVAILABLE);
        return switch (state) {
            case RUNNING -> RunStatus.RUNNING;
            case STOPPED -> RunStatus.ABORTED;
            case FAILED -> RunStatus.FAILED;
            case UNAVAILABLE -> RunStatus.SKIPPED;
        };
    }

    @Override
    public DriverState state(String instanceId) {
        return states.getOrDefault(instanceId, DriverState.UNAVAILABLE);
    }

    private void pump(String instanceId, Process process) {
        Thread reader = new Thread(() -> {
            try (BufferedReader buffered = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = buffered.readLine()) != null) {
                    log(instanceId, line);
                }
            } catch (IOException ex) {
                log(instanceId, "日志读取中断: " + ex.getMessage());
            }
        }, "server-log-" + instanceId);
        reader.setDaemon(true);
        reader.start();
    }

    private void log(String instanceId, String line) {
        Deque<String> entries = logs.computeIfAbsent(instanceId, key -> new ArrayDeque<>());
        synchronized (entries) {
            entries.addLast(line);
            while (entries.size() > LOG_CAPACITY) {
                entries.removeFirst();
            }
        }
    }
}
