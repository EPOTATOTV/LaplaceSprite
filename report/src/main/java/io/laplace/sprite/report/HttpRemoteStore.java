package io.laplace.sprite.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HTTP 对象存储（4.6「可上云存储」）：面向 S3 兼容 / 通用对象存储，按 path-style 发 PUT / HEAD / GET。
 *
 * <p>配置全部来自环境变量（见 {@link #ENV_ENDPOINT} 等），凭据绝不写进代码或提交进仓库。请求按 AWS
 * SigV4（服务 {@code s3}）签名，因此对 MinIO 等 S3 兼容端点可用。HTTP 传输可注入，测试用伪造实现
 * 离线断言请求形状。</p>
 *
 * <p>未配置时 {@link #configured()} 为 false，{@link #describe()} 给出中文说明，所有写操作抛异常——只报
 * 不可用，绝不假装上传成功。</p>
 */
public final class HttpRemoteStore implements EvidenceRemoteStore {

    public static final String ENV_ENDPOINT = "LAPLACE_OBJECTSTORE_ENDPOINT";
    public static final String ENV_BUCKET = "LAPLACE_OBJECTSTORE_BUCKET";
    public static final String ENV_REGION = "LAPLACE_OBJECTSTORE_REGION";
    public static final String ENV_ACCESS_KEY = "LAPLACE_OBJECTSTORE_ACCESS_KEY";
    public static final String ENV_SECRET_KEY = "LAPLACE_OBJECTSTORE_SECRET_KEY";

    private static final String DEFAULT_REGION = "us-east-1";
    private static final String ALGORITHM_TAG = "AWS4-HMAC-SHA256";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SERVICE = "s3";
    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    /** 可注入的 HTTP 传输：真实实现见 {@link #jdkTransport()}，测试注入伪造实现。 */
    public interface HttpTransport {
        StorageResponse send(StorageRequest request);
    }

    /** 一次对象存储请求：方法 + 绝对 URL + 头 + 体。 */
    public record StorageRequest(String method, String url, Map<String, String> headers, byte[] body) {
    }

    /** 对象存储响应：状态码 + 体。 */
    public record StorageResponse(int status, byte[] body) {
        public boolean successful() {
            return status >= 200 && status < 300;
        }
    }

    private final Map<String, String> environment;
    private final HttpTransport transport;
    private final Map<String, RemoteObject> objects = new ConcurrentHashMap<>();

    public HttpRemoteStore(Map<String, String> environment, HttpTransport transport) {
        this.environment = Map.copyOf(environment);
        this.transport = transport;
    }

    /** 生产入口：读进程环境变量，走 JDK HttpClient。 */
    public static HttpRemoteStore fromEnvironment() {
        return new HttpRemoteStore(System.getenv(), jdkTransport());
    }

    @Override
    public boolean configured() {
        return !value(ENV_ENDPOINT).isBlank() && !value(ENV_BUCKET).isBlank()
                && !value(ENV_ACCESS_KEY).isBlank() && !value(ENV_SECRET_KEY).isBlank();
    }

    @Override
    public String put(String evidenceId, String relativePath, byte[] payload) {
        ensureConfigured();
        String key = EvidenceRemoteStore.normalizeKey(relativePath);
        StorageResponse response = transport.send(signed("PUT", key, payload));
        if (!response.successful()) {
            throw new IllegalStateException("上传证据 %s 失败：HTTP %d".formatted(evidenceId, response.status()));
        }
        objects.put(key, new RemoteObject(key, evidenceId, payload.length,
                EvidenceChainLink.payloadHash(payload), Instant.now()));
        return key;
    }

    @Override
    public byte[] get(String remoteRef) {
        ensureConfigured();
        StorageResponse response = transport.send(signed("GET", EvidenceRemoteStore.normalizeKey(remoteRef),
                new byte[0]));
        if (!response.successful()) {
            throw new IllegalStateException("取回远端对象 %s 失败：HTTP %d".formatted(remoteRef, response.status()));
        }
        return response.body();
    }

    @Override
    public boolean exists(String remoteRef) {
        if (!configured()) {
            return false;
        }
        StorageResponse response = transport.send(signed("HEAD", EvidenceRemoteStore.normalizeKey(remoteRef),
                new byte[0]));
        return response.successful();
    }

    /** 本会话内成功上传的对象；不做桶级 ListObjects（那是额外一次分页的 XML 解析），清点以本地索引为准。 */
    @Override
    public List<RemoteObject> list() {
        return configured() ? List.copyOf(objects.values()) : List.of();
    }

    @Override
    public String describe() {
        if (!configured()) {
            return "unavailable: 对象存储未配置（需要环境变量 %s / %s / %s / %s），未上传任何证据"
                    .formatted(ENV_ENDPOINT, ENV_BUCKET, ENV_ACCESS_KEY, ENV_SECRET_KEY);
        }
        return "HTTP 对象存储 endpoint=%s bucket=%s region=%s（凭据仅经环境变量读取）"
                .formatted(endpoint(), bucket(), region());
    }

    /** 默认传输：JDK HttpClient。Host 等受限头由 JDK 自行写入，这里跳过签名时用的小写副本。 */
    public static HttpTransport jdkTransport() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return request -> {
            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest
                    .newBuilder(URI.create(request.url()))
                    .timeout(Duration.ofSeconds(20));
            request.headers().forEach((name, valueName) -> {
                if (!"host".equalsIgnoreCase(name)) {
                    builder.header(name, valueName);
                }
            });
            builder.method(request.method(), request.body() == null || request.body().length == 0
                    ? java.net.http.HttpRequest.BodyPublishers.noBody()
                    : java.net.http.HttpRequest.BodyPublishers.ofByteArray(request.body()));
            try {
                HttpResponse<byte[]> response = client.send(builder.build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                return new StorageResponse(response.statusCode(), response.body());
            } catch (IOException ex) {
                throw new UncheckedIOException("对象存储请求失败: " + request.url(), ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("对象存储请求被中断: " + request.url(), ex);
            }
        };
    }

    /**
     * 按 AWS SigV4 签名并渲染请求。path-style：路径固定为 {@code /<bucket>/<key>}，桶不进 Host。
     * 参与签名的头只有 host / x-amz-content-sha256 / x-amz-date（字典序），Authorization 在签名后追加。
     */
    private StorageRequest signed(String method, String key, byte[] payload) {
        URI base = URI.create(stripTrailingSlash(endpoint()));
        String host = base.getHost() + (base.getPort() > 0 ? ":" + base.getPort() : "");
        String canonicalUri = "/" + bucket() + "/" + encodePath(key);
        byte[] body = payload == null ? new byte[0] : payload;
        String payloadHash = EvidenceChainLink.payloadHash(body);

        Instant now = Instant.now();
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);

        Map<String, String> canonical = new LinkedHashMap<>();
        canonical.put("host", host);
        canonical.put("x-amz-content-sha256", payloadHash);
        canonical.put("x-amz-date", amzDate);
        String signedHeaders = String.join(";", canonical.keySet());
        StringBuilder headerBlock = new StringBuilder();
        canonical.forEach((name, value) -> headerBlock.append(name).append(':').append(value).append('\n'));

        String canonicalRequest = String.join("\n", method, canonicalUri, "", headerBlock.toString(),
                signedHeaders, payloadHash);
        String scope = dateStamp + "/" + region() + "/" + SERVICE + "/aws4_request";
        String stringToSign = String.join("\n", ALGORITHM_TAG, amzDate, scope,
                EvidenceChainLink.payloadHash(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        String signature = HexFormat.of().formatHex(hmac(signingKey(dateStamp), stringToSign));

        Map<String, String> headers = new LinkedHashMap<>(canonical);
        headers.put("Authorization", ALGORITHM_TAG + " Credential=" + accessKey() + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature);
        return new StorageRequest(method, base.getScheme() + "://" + host + canonicalUri, headers, body);
    }

    private byte[] signingKey(String dateStamp) {
        byte[] kDate = hmac(("AWS4" + secretKey()).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmac(kDate, region());
        byte[] kService = hmac(kRegion, SERVICE);
        return hmac(kService, "aws4_request");
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(HMAC_ALGORITHM + " 不可用", ex);
        }
    }

    /** 路径逐段做 RFC3986 编码；空格等会成为 %XX，桶与键之间的分隔斜杠保留。 */
    private static String encodePath(String key) {
        StringBuilder encoded = new StringBuilder();
        String[] segments = key.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                encoded.append('/');
            }
            encoded.append(encodeSegment(segments[i]));
        }
        return encoded.toString();
    }

    private static String encodeSegment(String segment) {
        StringBuilder encoded = new StringBuilder();
        for (byte raw : segment.getBytes(StandardCharsets.UTF_8)) {
            int c = raw & 0xff;
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~';
            if (unreserved) {
                encoded.append((char) c);
            } else {
                encoded.append(String.format(Locale.ROOT, "%%%02X", c));
            }
        }
        return encoded.toString();
    }

    private void ensureConfigured() {
        if (!configured()) {
            throw new IllegalStateException(describe());
        }
    }

    private String endpoint() {
        return value(ENV_ENDPOINT);
    }

    private String bucket() {
        return value(ENV_BUCKET);
    }

    private String region() {
        String region = value(ENV_REGION);
        return region.isBlank() ? DEFAULT_REGION : region;
    }

    private String accessKey() {
        return value(ENV_ACCESS_KEY);
    }

    private String secretKey() {
        return value(ENV_SECRET_KEY);
    }

    private String value(String name) {
        String value = environment.get(name);
        return value == null ? "" : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
