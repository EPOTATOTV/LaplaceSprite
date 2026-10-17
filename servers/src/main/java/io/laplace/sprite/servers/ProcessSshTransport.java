package io.laplace.sprite.servers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 真实进程 SSH 传输（12.2）：以外部 ssh 可执行文件连远端测试节点。
 *
 * <p>参数向量固定带 {@code -o BatchMode=yes}（禁止交互式提问，主机不可达时立即失败而不是挂住），并带
 * {@code -o StrictHostKeyChecking=accept-new} 以便首次连接非交互地接受主机键。未配置 host/user 或 ssh
 * 不可用时 {@link #available()} 为 false，{@link #exec} 返回带中文说明的非 0 结果。</p>
 */
public final class ProcessSshTransport implements SshTransport {

    public static final String DEFAULT_SSH_EXECUTABLE = "ssh";

    private static final long PROBE_TIMEOUT_SECONDS = 3L;
    private static final long EXEC_TIMEOUT_SECONDS = 60L;

    private final String sshExecutable;
    private final String host;
    private final String user;
    private final int port;
    private final String keyPath;

    public ProcessSshTransport(String sshExecutable, String host, String user, int port, String keyPath) {
        this.sshExecutable = sshExecutable == null || sshExecutable.isBlank()
                ? DEFAULT_SSH_EXECUTABLE : sshExecutable;
        this.host = host == null ? "" : host;
        this.user = user == null ? "" : user;
        this.port = port;
        this.keyPath = keyPath == null ? "" : keyPath;
    }

    /** 生产入口：连接参数与 ssh 路径均从环境变量读，缺省 host/user 即视为未配置。 */
    public static ProcessSshTransport fromEnvironment() {
        return new ProcessSshTransport(
                envOr("LAPLACE_SSH_BINARY", DEFAULT_SSH_EXECUTABLE),
                envOr("LAPLACE_SSH_HOST", ""),
                envOr("LAPLACE_SSH_USER", ""),
                parsePort(envOr("LAPLACE_SSH_PORT", "22")),
                envOr("LAPLACE_SSH_KEY", ""));
    }

    /**
     * ssh 参数向量（不含 ssh 可执行文件本身）。
     *
     * <p>顺序与 OpenSSH 一致：{@code -o BatchMode=yes -o StrictHostKeyChecking=accept-new [-p port]
     * [-i key] user@host <command>}。整条远端命令作为最后一个 argv 元素传入，远端 shell 按原样解析，
     * 因此命令内部的引号由调用方（{@link SshCommandDriver}）负责。</p>
     */
    public List<String> arguments(String command) {
        List<String> args = new ArrayList<>();
        args.add("-o");
        args.add("BatchMode=yes");
        args.add("-o");
        args.add("StrictHostKeyChecking=accept-new");
        if (port > 0) {
            args.add("-p");
            args.add(Integer.toString(port));
        }
        if (!keyPath.isBlank()) {
            args.add("-i");
            args.add(keyPath);
        }
        args.add(user + "@" + host);
        args.add(command);
        return List.copyOf(args);
    }

    @Override
    public boolean available() {
        if (!configured()) {
            return false;
        }
        try {
            Process process = new ProcessBuilder(sshExecutable, "-V").redirectErrorStream(true).start();
            boolean finished = process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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

    @Override
    public ExecResult exec(String command) {
        if (!available()) {
            return ExecResult.failed("unavailable: " + describe());
        }
        List<String> full = new ArrayList<>();
        full.add(sshExecutable);
        full.addAll(arguments(command));
        try {
            // 合并 stderr 到 stdout：docker compose logs 走 stderr，且避免两路管道未同时读取时互相堵死
            Process process = new ProcessBuilder(full).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ExecResult.failed("unavailable: ssh 执行超时，远端命令结果未知");
            }
            return new ExecResult(process.exitValue(), output, "");
        } catch (IOException ex) {
            return ExecResult.failed("unavailable: 无法执行 ssh（" + ex.getMessage() + "）");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ExecResult.failed("unavailable: ssh 执行被中断");
        }
    }

    @Override
    public String describe() {
        if (!configured()) {
            return "unavailable: SSH 未配置（需要 LAPLACE_SSH_HOST / LAPLACE_SSH_USER，可选 LAPLACE_SSH_PORT / LAPLACE_SSH_KEY）";
        }
        return "SSH %s@%s:%d（ssh=%s，BatchMode=yes）".formatted(user, host, port, sshExecutable);
    }

    private boolean configured() {
        return !host.isBlank() && !user.isBlank();
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 22;
        }
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
