package io.laplace.sprite.protocol;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 基岩格桥接（15.6）：Geyser + Floodgate 挂在 Paper 侧，验证基岩登录链路。
 *
 * <p>认证模式固定为 floodgate：基岩玩家用 Xbox 登录，经 Floodgate 做身份桥接，
 * 不必给服务端开正版验证。</p>
 *
 * @param geyserJar     Geyser 插件 jar
 * @param floodgateJar  Floodgate 插件 jar
 * @param bedrockPort   基岩监听端口
 * @param javaPort      后端 Java 服务端端口
 * @param authType      认证模式
 */
public record GeyserBridgeSpec(Path geyserJar, Path floodgateJar, int bedrockPort, int javaPort, String authType) {

    public static final String FLOODGATE_AUTH = "floodgate";
    public static final int DEFAULT_BEDROCK_PORT = 19132;
    public static final String GEYSER_TARGET = "plugins/Geyser-Spigot.jar";
    public static final String FLOODGATE_TARGET = "plugins/floodgate.jar";

    public GeyserBridgeSpec {
        authType = authType == null || authType.isBlank() ? FLOODGATE_AUTH : authType;
    }

    /** 默认基岩端口与 floodgate 认证。 */
    public static GeyserBridgeSpec standard(Path geyserJar, Path floodgateJar, int javaPort) {
        return new GeyserBridgeSpec(geyserJar, floodgateJar, DEFAULT_BEDROCK_PORT, javaPort, FLOODGATE_AUTH);
    }

    public boolean geyserAvailable() {
        return geyserJar != null && Files.isRegularFile(geyserJar);
    }

    public boolean floodgateAvailable() {
        return floodgateJar != null && Files.isRegularFile(floodgateJar);
    }

    /** 两个 jar 都就位才算可用。 */
    public boolean available() {
        return geyserAvailable() && floodgateAvailable();
    }

    /** 两个 jar 在服务端里的落点。 */
    public List<String> installTargets() {
        return List.of(GEYSER_TARGET, FLOODGATE_TARGET);
    }

    /** Geyser 配置文件的关键片段。 */
    public String configYaml() {
        return """
                bedrock:
                  port: %d
                remote:
                  address: 127.0.0.1
                  port: %d
                auth-type: %s
                """.formatted(bedrockPort, javaPort, authType);
    }

    /** 降级说明：jar 缺失时只渲染配置。 */
    public List<String> notes() {
        if (available()) {
            return List.of();
        }
        return List.of("Geyser %s / Floodgate %s，基岩格只能渲染配置，无法真实拉起"
                .formatted(geyserAvailable() ? "已就位" : "缺失", floodgateAvailable() ? "已就位" : "缺失"));
    }
}
