package io.laplace.sprite.servers;

import java.nio.file.Path;

/**
 * 驱动器工厂（12.2）：按 {@link ServerDriverKind} 选控制路径，缺省参数从环境变量读。
 *
 * <p>SSH / DOCKER_API 面向平台端独立部署的场景：平台端与 8h8g 测试节点分开部署，所以需要经 SSH 或
 * Docker API 远控，而不是在同一个进程 / cwd 里跑 docker compose。两条远控轨未配置时各自降级为
 * {@link DriverState#UNAVAILABLE}，不影响其余轨道。</p>
 *
 * <p>本环境没有真实远端节点，也没有 docker 守护进程，两条远控轨只在测试里用注入的通道验证请求渲染与
 * 降级路径，从未对真实主机执行过。</p>
 */
public final class ServerDriverFactory {

    private ServerDriverFactory() {
    }

    public static ServerDriver create(ServerDriverKind kind) {
        return switch (kind) {
            case IN_PROCESS -> new InProcessDriver();
            case LOCAL_PROCESS -> new LocalProcessDriver(Path.of(envOr("LAPLACE_SERVER_JAR", "server.jar")));
            case DOCKER_COMPOSE -> new DockerComposeDriver();
            case SSH -> new SshCommandDriver(ProcessSshTransport.fromEnvironment(),
                    envOr("LAPLACE_REMOTE_WORKDIR", "."));
            case DOCKER_API -> new DockerApiDriver(DockerApiHttpTransport.fromEnvironment());
        };
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
