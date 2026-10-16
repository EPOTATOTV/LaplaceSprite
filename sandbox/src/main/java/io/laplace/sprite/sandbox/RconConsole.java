package io.laplace.sprite.sandbox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * RCON 控制面（15.4）：通过 25575 下发命令（spark、数据包 reload、用例触发命令），不依赖 SSH。
 *
 * <p>传输可插拔（{@link RconTransport}），默认走 TCP；测试注入内存替身后，帧编解码与命令收敛
 * 都能离线断言。</p>
 */
public final class RconConsole implements AutoCloseable {

    public static final int DEFAULT_PORT = 25575;

    private final String host;
    private final int port;
    private final String password;
    private final RconTransport transport;
    private int nextId = 1;
    private boolean authenticated;

    private RconConsole(String host, int port, String password, RconTransport transport) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.transport = transport;
    }

    public static RconConsole over(String host, int port, String password, RconTransport transport) {
        return new RconConsole(host, port, password, Objects.requireNonNull(transport, "transport"));
    }

    /** 用 spec 的派生密码与端口建控制面。 */
    public static RconConsole forSpec(SandboxSpec spec, RconTransport transport) {
        return over("127.0.0.1", spec.rconPort(), spec.rconPassword(), transport);
    }

    /** 打开 TCP 连接；连不上抛 UncheckedIOException，调用方按环境不具备处理。 */
    public static RconConsole tcp(String host, int port, String password) {
        try {
            return new RconConsole(host, port, password, SocketRconTransport.connect(host, port));
        } catch (IOException ex) {
            throw new UncheckedIOException("连接 RCON 失败: " + host + ":" + port, ex);
        }
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public boolean authenticated() {
        return authenticated;
    }

    /** 登录：登录应答 id 为 -1 即密码错误。 */
    public void login() {
        RconPacket response = exchange(new RconPacket(nextId++, RconPacket.Type.LOGIN, password));
        if (response.id() == -1) {
            throw new IllegalStateException("RCON 认证失败（密码错误），命令未下发");
        }
        authenticated = true;
    }

    public RconPacket command(String command) {
        if (!authenticated) {
            throw new IllegalStateException("RCON 未认证，先调用 login() 再下发命令");
        }
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("RCON 命令不能为空");
        }
        RconPacket response = exchange(new RconPacket(nextId++, RconPacket.Type.COMMAND, stripSlash(command)));
        if (response.id() == -1) {
            throw new IllegalStateException("RCON 拒绝执行命令: " + command);
        }
        return response;
    }

    public String commandText(String command) {
        return command(command).body();
    }

    public String sparkTps() {
        return commandText("spark tps");
    }

    public String sparkHealth() {
        return commandText("spark health");
    }

    /** 原版数据包 reload：与 15.2 冷启动轨禁止的 Bukkit 插件 reload 不是同一套机制。 */
    public String reloadDataPacks() {
        return commandText("minecraft:reload");
    }

    /** 用例触发命令。 */
    public String triggerCase(String caseId) {
        return commandText("laplace run " + caseId);
    }

    @Override
    public void close() {
        try {
            transport.close();
        } catch (IOException ex) {
            throw new UncheckedIOException("关闭 RCON 传输失败", ex);
        }
    }

    /** RCON 不收前导斜杠：文档里的 /spark 到这里收敛成 spark。 */
    private static String stripSlash(String command) {
        String trimmed = command.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    private RconPacket exchange(RconPacket request) {
        try {
            return RconPacket.decode(transport.exchange(request.encode()));
        } catch (IOException ex) {
            throw new UncheckedIOException("RCON 交换失败: " + host + ":" + port, ex);
        }
    }
}
