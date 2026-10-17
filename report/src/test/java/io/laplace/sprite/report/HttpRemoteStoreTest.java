package io.laplace.sprite.report;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HTTP 对象存储（4.6）：请求形状与降级路径都用伪造传输离线断言。 */
class HttpRemoteStoreTest {

    private static final Map<String, String> ENV = Map.of(
            HttpRemoteStore.ENV_ENDPOINT, "https://s3.example.com",
            HttpRemoteStore.ENV_BUCKET, "laplace-evidence",
            HttpRemoteStore.ENV_ACCESS_KEY, "AKIAEXAMPLE",
            HttpRemoteStore.ENV_SECRET_KEY, "secret-example-key");

    @Test
    void unconfiguredStoreReportsItselfAndRefusesWrites() {
        FakeTransport transport = new FakeTransport();
        HttpRemoteStore store = new HttpRemoteStore(Map.of(), transport);

        assertFalse(store.configured());
        assertTrue(store.describe().startsWith("unavailable:"), store.describe());
        assertTrue(store.describe().contains("未上传任何证据"));
        assertTrue(store.list().isEmpty());
        assertFalse(store.exists("anything"));
        assertThrows(IllegalStateException.class,
                () -> store.put("ev-00001", "run-1/log.txt", "x".getBytes(StandardCharsets.UTF_8)));
        assertTrue(transport.requests.isEmpty(), "未配置时不得发出任何请求");
    }

    @Test
    void putRendersSignedPathStyleRequest() {
        FakeTransport transport = new FakeTransport();
        HttpRemoteStore store = new HttpRemoteStore(ENV, transport);
        assertTrue(store.configured());
        assertTrue(store.describe().contains("bucket=laplace-evidence"));

        byte[] payload = "evidence".getBytes(StandardCharsets.UTF_8);
        String ref = store.put("ev-00001", "run-1/log-ev-00001.txt", payload);

        assertEquals("run-1/log-ev-00001.txt", ref);
        HttpRemoteStore.StorageRequest request = transport.requests.get(0);
        assertEquals("PUT", request.method());
        assertEquals("https://s3.example.com/laplace-evidence/run-1/log-ev-00001.txt", request.url());
        assertEquals("s3.example.com", request.headers().get("host"));
        assertEquals(EvidenceChainLink.payloadHash(payload), request.headers().get("x-amz-content-sha256"));
        assertTrue(request.headers().get("Authorization").startsWith("AWS4-HMAC-SHA256 "),
                request.headers().get("Authorization"));
        assertTrue(request.headers().get("Authorization").contains("Credential=AKIAEXAMPLE/"),
                request.headers().get("Authorization"));
        assertTrue(request.headers().get("Authorization").contains("/us-east-1/s3/aws4_request"),
                "默认区域应为 us-east-1");

        assertEquals(1, store.list().size());
        assertEquals("ev-00001", store.list().get(0).evidenceId());
    }

    @Test
    void getAndHeadRenderTheRightVerbs() {
        FakeTransport transport = new FakeTransport();
        transport.body("GET", "remote-bytes".getBytes(StandardCharsets.UTF_8));
        HttpRemoteStore store = new HttpRemoteStore(ENV, transport);

        assertEquals("remote-bytes",
                new String(store.get("run-1/log-ev-00001.txt"), StandardCharsets.UTF_8));
        assertTrue(store.exists("run-1/log-ev-00001.txt"));

        assertEquals("GET", transport.requests.get(0).method());
        assertEquals("HEAD", transport.requests.get(1).method());
        assertEquals("/laplace-evidence/run-1/log-ev-00001.txt",
                java.net.URI.create(transport.requests.get(0).url()).getPath());
    }

    @Test
    void keyWithSpacesIsPercentEncodedInPath() {
        FakeTransport transport = new FakeTransport();
        HttpRemoteStore store = new HttpRemoteStore(ENV, transport);

        store.put("ev-00002", "run 2/log ev.txt", new byte[0]);

        assertEquals("https://s3.example.com/laplace-evidence/run%202/log%20ev.txt",
                transport.requests.get(0).url());
    }

    @Test
    void serverErrorIsReportedNotSwallowed() {
        FakeTransport transport = new FakeTransport();
        transport.status("PUT", 403);
        HttpRemoteStore store = new HttpRemoteStore(ENV, transport);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> store.put("ev-00003", "run-1/log.txt", new byte[0]));
        assertTrue(ex.getMessage().contains("403"), ex.getMessage());
        assertTrue(store.list().isEmpty(), "失败的上传不得进入对象清单");
    }

    /** 伪造传输：记录每个请求，按方法返回预置状态与体。 */
    private static final class FakeTransport implements HttpRemoteStore.HttpTransport {

        private final List<HttpRemoteStore.StorageRequest> requests = new ArrayList<>();
        private final Map<String, Integer> statuses = new HashMap<>();
        private final Map<String, byte[]> bodies = new HashMap<>();

        void status(String method, int status) {
            statuses.put(method, status);
        }

        void body(String method, byte[] body) {
            bodies.put(method, body);
        }

        @Override
        public HttpRemoteStore.StorageResponse send(HttpRemoteStore.StorageRequest request) {
            requests.add(request);
            return new HttpRemoteStore.StorageResponse(statuses.getOrDefault(request.method(), 200),
                    bodies.getOrDefault(request.method(), new byte[0]));
        }
    }
}
