package io.laplace.sprite.servers;

import java.nio.file.Path;

/**
 * Docker Engine API 端点（12.2）：平台端经 Docker API 控制测试节点，端点有两种形状。
 *
 * <p>unix socket：连接 {@code /var/run/docker.sock}，逻辑主机名 localhost；TCP：{@code host:port}。
 * 两种形状的请求路径都带 Docker 版本前缀，例如 {@code /v1.43/containers/create}。</p>
 *
 * @param kind       传输介质
 * @param host       TCP 主机；unix socket 时为 localhost
 * @param port       TCP 端口；unix socket 时无意义
 * @param unixSocket unix socket 文件；TCP 时为空
 * @param apiVersion Docker API 版本前缀（如 v1.43）
 */
public record DockerApiEndpoint(Kind kind, String host, int port, Path unixSocket, String apiVersion) {

    public enum Kind {
        UNIX_SOCKET,
        TCP
    }

    public DockerApiEndpoint {
        if (apiVersion == null || apiVersion.isBlank()) {
            throw new IllegalArgumentException("Docker API 版本不能为空");
        }
    }

    public static DockerApiEndpoint unixSocket(Path socket, String apiVersion) {
        return new DockerApiEndpoint(Kind.UNIX_SOCKET, "localhost", 0, socket, apiVersion);
    }

    public static DockerApiEndpoint tcp(String host, int port, String apiVersion) {
        return new DockerApiEndpoint(Kind.TCP, host, port, null, apiVersion);
    }

    /** 请求行里的版本前缀，如 {@code /v1.43}。 */
    public String basePath() {
        return "/" + apiVersion;
    }

    public String describe() {
        return switch (kind) {
            case UNIX_SOCKET -> "unix://%s（HTTP over unix socket，API %s）".formatted(unixSocket, apiVersion);
            case TCP -> "tcp://%s:%d（API %s）".formatted(host, port, apiVersion);
        };
    }
}
