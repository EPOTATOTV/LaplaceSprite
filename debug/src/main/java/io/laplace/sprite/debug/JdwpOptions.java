package io.laplace.sprite.debug;

/**
 * jdwp 注入参数（15.1）。
 *
 * <p>默认 suspend=n：CI 上没人挂 IDE，suspend=y 会把启动直接卡死；只有「等待 IDE 附加」模式才用 y。</p>
 *
 * @param port           调试端口，映射回宿主机 IDE
 * @param suspendOnStart 是否挂起等待 IDE 附加
 * @param hostAddress    绑定地址；空串表示 address 只写端口
 */
public record JdwpOptions(int port, boolean suspendOnStart, String hostAddress) {

    public static final int MIN_PORT = 1024;
    public static final int MAX_PORT = 65535;

    public JdwpOptions {
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException("jdwp 端口越界（%d-%d）: %d".formatted(MIN_PORT, MAX_PORT, port));
        }
        hostAddress = hostAddress == null ? "" : hostAddress.trim();
    }

    /** CI 默认：suspend=n，不阻塞流水线。 */
    public static JdwpOptions ciDefault(int port) {
        return new JdwpOptions(port, false, "");
    }

    /** 等待 IDE 附加：suspend=y，JVM 起来就停住等人连。 */
    public static JdwpOptions waitForIde(int port) {
        return new JdwpOptions(port, true, "");
    }

    public JdwpOptions boundTo(String host) {
        return new JdwpOptions(port, suspendOnStart, host);
    }

    public String toAgentArg() {
        String address = hostAddress.isEmpty() ? Integer.toString(port) : hostAddress + ":" + port;
        return "-agentlib:jdwp=transport=dt_socket,server=y,suspend=%s,address=%s"
                .formatted(suspendOnStart ? "y" : "n", address);
    }

    /** 容器内调试端口映射回宿主：只绑 127.0.0.1，IDE 在本地连。 */
    public String publishArgument() {
        return "127.0.0.1:" + port + ":" + port;
    }

    public boolean waitsForIde() {
        return suspendOnStart;
    }
}
