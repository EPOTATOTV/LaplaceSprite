package io.laplace.sprite.profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存命令通道：把命令映射到预先备好的文本回执，供离线测试与演示使用。
 *
 * <p>{@link #unavailable()} 用于验证 spark 不可用时的降级路径。</p>
 */
public final class InMemoryCommandChannel implements CommandChannel {

    private final Map<String, String> canned = new LinkedHashMap<>();
    private final List<String> sent = new CopyOnWriteArrayList<>();
    private final boolean available;

    private InMemoryCommandChannel(boolean available) {
        this.available = available;
    }

    public static InMemoryCommandChannel available() {
        return new InMemoryCommandChannel(true);
    }

    public static InMemoryCommandChannel unavailable() {
        return new InMemoryCommandChannel(false);
    }

    /** 登记某条命令的回执，返回自身便于链式拼接。 */
    public InMemoryCommandChannel respond(String command, String output) {
        canned.put(command, output);
        return this;
    }

    /** 已下发的命令，按顺序。 */
    public List<String> sentCommands() {
        return List.copyOf(sent);
    }

    @Override
    public String send(String command) {
        sent.add(command);
        if (!available) {
            throw new CommandChannelUnavailableException("命令通道不可用: " + command);
        }
        return canned.getOrDefault(command, "");
    }
}
