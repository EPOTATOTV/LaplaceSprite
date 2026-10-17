package io.laplace.sprite.servers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存 SSH 传输（12.2）：测试注入。按命令给出预置结果，并记录执行过的命令，便于断言远端命令串。
 */
public final class InMemorySshTransport implements SshTransport {

    private final List<String> executed = new ArrayList<>();
    private final Map<String, ExecResult> canned = new LinkedHashMap<>();
    private ExecResult fallback = ExecResult.ok("");

    public InMemorySshTransport on(String command, ExecResult result) {
        canned.put(command, result);
        return this;
    }

    public InMemorySshTransport withFallback(ExecResult result) {
        this.fallback = result;
        return this;
    }

    /** 依次执行过的远端命令。 */
    public List<String> executed() {
        return List.copyOf(executed);
    }

    @Override
    public ExecResult exec(String command) {
        executed.add(command);
        return canned.getOrDefault(command, fallback);
    }
}
