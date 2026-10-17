package io.laplace.sprite.servers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * 真实 Docker API 传输（12.2）：unix socket 走原生 {@link SocketChannel} 手写 HTTP/1.1，TCP 走 HttpClient。
 *
 * <p>docker 守护进程不在（socket 文件缺失、连接被拒、TCP 不可达）时 {@link #available()} 为 false 或调用
 * 直接失败，由 {@link DockerApiDriver} 降级为 {@link DriverState#UNAVAILABLE}，绝不伪造远端容器状态。</p>
 *
 * <p>unix 分支按 Content-Length 帧读响应，不处理 chunked：Docker API 的 JSON 响应通常带 Content-Length。</p>
 */
public final class DockerApiHttpTransport implements DockerApiTransport {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final DockerApiEndpoint endpoint;

    public DockerApiHttpTransport(DockerApiEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    /** 生产入口：端点与 API 版本从环境变量读，默认连本机 unix socket。 */
    public static DockerApiHttpTransport fromEnvironment() {
        String version = envOr("LAPLACE_DOCKER_API_VERSION", "v1.43");
        String host = envOr("LAPLACE_DOCKER_HOST", "");
        if (!host.isBlank()) {
            int separator = host.lastIndexOf(':');
            if (separator > 0) {
                return new DockerApiHttpTransport(DockerApiEndpoint.tcp(host.substring(0, separator),
                        parsePort(host.substring(separator + 1)), version));
            }
            return new DockerApiHttpTransport(DockerApiEndpoint.tcp(host, 2375, version));
        }
        return new DockerApiHttpTransport(DockerApiEndpoint.unixSocket(
                Path.of(envOr("LAPLACE_DOCKER_SOCKET", "/var/run/docker.sock")), version));
    }

    @Override
    public DockerApiEndpoint endpoint() {
        return endpoint;
    }

    @Override
    public boolean available() {
        return switch (endpoint.kind()) {
            case UNIX_SOCKET -> Files.exists(endpoint.unixSocket());
            // TCP 端点无法在不发请求的情况下判定可达性，交给调用时失败降级
            case TCP -> true;
        };
    }

    @Override
    public ApiResponse send(ApiRequest request) {
        try {
            return switch (endpoint.kind()) {
                case UNIX_SOCKET -> sendOverUnix(request);
                case TCP -> sendOverTcp(request);
            };
        } catch (IOException ex) {
            throw new UncheckedIOException("Docker API 请求失败（" + endpoint.describe() + "）: "
                    + ex.getMessage(), ex);
        }
    }

    @Override
    public String describe() {
        if (!available()) {
            return "unavailable: Docker 守护进程端点不可达（" + endpoint.describe() + "），未下发任何容器操作";
        }
        return "Docker Engine API " + endpoint.describe();
    }

    private ApiResponse sendOverTcp(ApiRequest request) throws IOException {
        URI uri = URI.create("http://" + endpoint.host() + ":" + endpoint.port() + request.path());
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(uri).timeout(TIMEOUT);
        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        builder.method(request.method(), request.body() == null || request.body().isEmpty()
                ? java.net.http.HttpRequest.BodyPublishers.noBody()
                : java.net.http.HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new ApiResponse(response.statusCode(), response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Docker API 请求被中断", ex);
        }
    }

    private ApiResponse sendOverUnix(ApiRequest request) throws IOException {
        byte[] body = request.body() == null ? new byte[0] : request.body().getBytes(StandardCharsets.UTF_8);
        StringBuilder head = new StringBuilder();
        head.append(request.method()).append(' ').append(request.path()).append(" HTTP/1.1\r\n");
        head.append("Host: localhost\r\n");
        head.append("Connection: close\r\n");
        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            head.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        head.append("Content-Length: ").append(body.length).append("\r\n\r\n");

        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(endpoint.unixSocket()));
            OutputStream out = Channels.newOutputStream(channel);
            out.write(head.toString().getBytes(StandardCharsets.UTF_8));
            out.write(body);
            out.flush();
            InputStream in = Channels.newInputStream(channel);
            return parseHttpResponse(in);
        }
    }

    /** 只解析 HTTP/1.1 状态行与体（Connection: close 一次读完）。 */
    private static ApiResponse parseHttpResponse(InputStream in) throws IOException {
        String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        int headerEnd = raw.indexOf("\r\n\r\n");
        String head = headerEnd >= 0 ? raw.substring(0, headerEnd) : raw;
        String body = headerEnd >= 0 ? raw.substring(headerEnd + 4) : "";
        int status = 0;
        int firstSpace = head.indexOf(' ');
        if (firstSpace > 0) {
            int secondSpace = head.indexOf(' ', firstSpace + 1);
            if (secondSpace > firstSpace) {
                try {
                    status = Integer.parseInt(head.substring(firstSpace + 1, secondSpace).trim());
                } catch (NumberFormatException ex) {
                    status = 0;
                }
            }
        }
        return new ApiResponse(status, body);
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 2375;
        }
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
