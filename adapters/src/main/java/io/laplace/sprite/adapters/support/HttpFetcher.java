package io.laplace.sprite.adapters.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP 拉取封装：网络不通就把失败当正常分支处理。
 *
 * <p>产物解析与版本矩阵都要求「离线可跑」，所以这里所有异常都收敛成 {@code Optional.empty()}，
 * 由调用方退化到本地快照。超时压得比较短，离线环境下不会卡住构建。</p>
 */
public final class HttpFetcher {

    private final HttpClient client;
    private final Duration timeout;
    private final boolean enabled;

    public HttpFetcher(Duration timeout, boolean enabled) {
        this.timeout = timeout;
        this.enabled = enabled;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public boolean enabled() {
        return enabled;
    }

    /** 发起 GET；非 2xx、超时、DNS 失败一律返回空。 */
    public Optional<String> get(URI uri, Map<String, String> headers) {
        if (!enabled || uri == null) {
            return Optional.empty();
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", "laplace-sprite/1.0")
                .GET();
        headers.forEach(builder::header);
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return Optional.empty();
            }
            return Optional.of(response.body());
        } catch (IOException ex) {
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    public Optional<String> get(URI uri) {
        return get(uri, Map.of());
    }
}
