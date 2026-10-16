package io.laplace.sprite.protocol;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * ViaProxy 独立代理入口（15.6）：一个独立代理承接协议转换，后端不必逐个装 Via 三件套。
 *
 * <p>本类只渲染启动命令与配置，不真拉起进程。jar 缺失时 {@link #available()} 为 false，
 * 命令仍可打印用于 dry-run，但不会假装协议入口已经起来。</p>
 */
public final class ViaProxyLauncher {

    public static final int DEFAULT_LISTEN_PORT = 25568;
    public static final String DEFAULT_BIND_ADDRESS = "0.0.0.0";
    public static final String DEFAULT_JAVA_BINARY = "java";
    public static final String CONFIG_FILE = "viaproxy.yml";

    private final Path jarPath;
    private final int listenPort;
    private final String targetHost;
    private final int targetPort;
    private final String bindAddress;
    private final String javaBinary;

    public ViaProxyLauncher(Path jarPath, int listenPort, String targetHost, int targetPort) {
        this(jarPath, listenPort, targetHost, targetPort, DEFAULT_BIND_ADDRESS, DEFAULT_JAVA_BINARY);
    }

    public ViaProxyLauncher(Path jarPath, int listenPort, String targetHost, int targetPort, String bindAddress,
                            String javaBinary) {
        this.jarPath = Objects.requireNonNull(jarPath, "jarPath");
        this.listenPort = listenPort;
        this.targetHost = Objects.requireNonNull(targetHost, "targetHost");
        this.targetPort = targetPort;
        this.bindAddress = bindAddress == null || bindAddress.isBlank() ? DEFAULT_BIND_ADDRESS : bindAddress;
        this.javaBinary = javaBinary == null || javaBinary.isBlank() ? DEFAULT_JAVA_BINARY : javaBinary;
    }

    /** jar 是否就位。 */
    public boolean available() {
        return Files.isRegularFile(jarPath);
    }

    public Path jarPath() {
        return jarPath;
    }

    public int listenPort() {
        return listenPort;
    }

    /** 客户端连过来的入口地址。 */
    public String listenAddress() {
        return bindAddress + ":" + listenPort;
    }

    /** 后端目标地址。 */
    public String targetAddress() {
        return targetHost + ":" + targetPort;
    }

    /** 启动命令；参数名以 ViaProxy 的 --help 为准，这里按 3.x 的 CLI 命名渲染。 */
    public List<String> startCommand() {
        return List.of(javaBinary, "-jar", jarPath.toString(),
                "--bind-address", bindAddress,
                "--bind-port", Integer.toString(listenPort),
                "--target-address", targetHost,
                "--target-port", Integer.toString(targetPort));
    }

    /** 一份最小可用配置。 */
    public String configYaml() {
        return """
                bind-address: %s
                bind-port: %d
                target-address: %s
                target-port: %d
                """.formatted(bindAddress, listenPort, targetHost, targetPort);
    }

    /** 把配置写到目录下，返回文件路径。 */
    public Path writeConfig(Path configDir) {
        Path config = configDir.resolve(CONFIG_FILE);
        try {
            Files.createDirectories(configDir);
            Files.writeString(config, configYaml(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入 ViaProxy 配置失败: " + config, ex);
        }
        return config;
    }

    /** 降级说明：jar 缺失时不静默通过。 */
    public List<String> notes() {
        if (available()) {
            return List.of();
        }
        return List.of("未找到 ViaProxy jar（%s），启动命令仅作 dry-run，协议入口未拉起".formatted(jarPath));
    }
}
