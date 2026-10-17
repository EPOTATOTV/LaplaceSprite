package io.laplace.sprite.servers;

import java.util.Map;

/**
 * Docker Engine API 传输（12.2）：可注入的 HTTP 通道。
 *
 * <p>真实实现见 {@link DockerApiHttpTransport}；测试注入伪造实现后，请求形状（方法 / 路径 / 体）与响应
 * 解析都能离线断言。{@link #endpoint()} 供驱动器拼版本前缀路径。</p>
 */
public interface DockerApiTransport {

    /** 一次 API 请求：请求行（方法 + 含查询串的路径）、头、JSON 体（GET 时为空串）。 */
    record ApiRequest(String method, String path, Map<String, String> headers, String body) {
    }

    /** API 响应：状态码 + 原始响应体（JSON 文本）。 */
    record ApiResponse(int status, String body) {

        public boolean successful() {
            return status >= 200 && status < 300;
        }
    }

    ApiResponse send(ApiRequest request);

    DockerApiEndpoint endpoint();

    /** 通道是否可用（socket 存在 / 端点已配置）。 */
    default boolean available() {
        return true;
    }

    /** 人类可读说明，不可用时写明原因。 */
    String describe();
}
