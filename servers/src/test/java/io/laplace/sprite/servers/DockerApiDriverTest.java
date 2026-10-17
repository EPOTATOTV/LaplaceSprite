package io.laplace.sprite.servers;

import com.fasterxml.jackson.databind.JsonNode;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.RunStatus;
import io.laplace.sprite.servers.DockerApiTransport.ApiRequest;
import io.laplace.sprite.servers.DockerApiTransport.ApiResponse;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Docker API 控制轨（12.2）：请求渲染、响应解析、以及守护进程不可达时的降级。 */
class DockerApiDriverTest {

    private static final PlatformKey ARCLIGHT = PlatformKey.of(PlatformFamily.ARCLIGHT, "1.21.1");
    private static final DockerApiEndpoint ENDPOINT =
            DockerApiEndpoint.tcp("127.0.0.1", 2375, "v1.43");

    private static TestServerSpec hybridSpec() {
        return TestServerSpec.hybrid("hy-1", ARCLIGHT, "1.0.0",
                Path.of("/srv/plugins"), Path.of("/srv/mods"));
    }

    @Test
    void startRendersNetworkContainerCreateAndStartRequests() {
        FakeTransport transport = new FakeTransport(ENDPOINT, DockerApiDriverTest::canned);
        DockerApiDriver driver = new DockerApiDriver(transport);

        driver.start(hybridSpec());

        assertEquals("POST", transport.request(0).method());
        assertEquals("/v1.43/networks/create", transport.request(0).path());
        assertTrue(transport.request(0).body().contains("laplace-hy-1"));

        assertEquals("POST", transport.request(1).method());
        assertEquals("/v1.43/containers/create?name=hy-1", transport.request(1).path());
        assertEquals("application/json", transport.request(1).headers().get("Content-Type"));

        assertEquals("POST", transport.request(2).method());
        assertEquals("/v1.43/containers/cid-1/start", transport.request(2).path());

        assertEquals(DriverState.RUNNING, driver.state("hy-1"));
        assertEquals(RunStatus.RUNNING, driver.status("hy-1"));
        assertTrue(driver.running("hy-1"));
    }

    @Test
    void createBodyBindsMountsReadOnlyAndPortsToLoopback() throws Exception {
        FakeTransport transport = new FakeTransport(ENDPOINT, DockerApiDriverTest::canned);
        new DockerApiDriver(transport).start(hybridSpec());

        JsonNode body = Json.jsonMapper().readTree(transport.request(1).body());
        assertEquals("itzg/minecraft-server:java21", body.path("Image").asText());
        assertTrue(body.path("Env").toString().contains("VERSION=1.21.1"));

        JsonNode hostConfig = body.path("HostConfig");
        assertEquals(2048L * 1024 * 1024, hostConfig.path("Memory").asLong());
        assertEquals(2, hostConfig.path("Binds").size());
        assertTrue(hostConfig.path("Binds").get(0).asText().contains(":/plugins:ro"),
                hostConfig.path("Binds").toString());
        assertTrue(hostConfig.path("Binds").get(1).asText().contains(":/mods:ro"));

        JsonNode binding = hostConfig.path("PortBindings").path("25565/tcp").get(0);
        assertEquals("127.0.0.1", binding.path("HostIp").asText());
        assertEquals("25565", binding.path("HostPort").asText());
    }

    @Test
    void healthTailAndStopRenderInspectLogsAndStopRequests() {
        FakeTransport transport = new FakeTransport(ENDPOINT, DockerApiDriverTest::canned);
        DockerApiDriver driver = new DockerApiDriver(transport);
        driver.start(hybridSpec());

        assertEquals("State.Status=running", driver.health("hy-1"));
        assertEquals("/v1.43/containers/cid-1/json", transport.request(3).path());
        assertEquals("GET", transport.request(3).method());

        assertEquals(List.of("line-1", "line-2"), driver.tail("hy-1", 10));
        assertEquals("/v1.43/containers/cid-1/logs?stdout=1&stderr=1&tail=10", transport.request(4).path());

        driver.stop("hy-1");
        assertEquals("POST", transport.request(5).method());
        assertEquals("/v1.43/containers/cid-1/stop", transport.request(5).path());
        assertEquals(DriverState.STOPPED, driver.state("hy-1"));
        assertEquals(RunStatus.ABORTED, driver.status("hy-1"));
    }

    @Test
    void existingNetworkConflictIsNotFatal() {
        FakeTransport transport = new FakeTransport(ENDPOINT,
                request -> request.path().contains("/networks/create")
                        ? new ApiResponse(409, "{}") : canned(request));
        DockerApiDriver driver = new DockerApiDriver(transport);

        driver.start(hybridSpec());

        assertEquals(DriverState.RUNNING, driver.state("hy-1"));
        assertTrue(transport.request(1).path().contains("/containers/create"), "409 后仍应继续创建容器");
    }

    @Test
    void unavailableDaemonDegradesInsteadOfFakingContainerState() {
        FakeTransport transport = new FakeTransport(ENDPOINT, DockerApiDriverTest::canned);
        transport.available(false);
        DockerApiDriver driver = new DockerApiDriver(transport);

        assertDoesNotThrow(() -> driver.start(hybridSpec()));

        assertEquals(DriverState.UNAVAILABLE, driver.state("hy-1"));
        assertEquals(RunStatus.SKIPPED, driver.status("hy-1"));
        assertTrue(transport.requests().isEmpty(), "不可用时不得下发任何请求");
        assertTrue(driver.tail("hy-1", 5).stream().anyMatch(line -> line.contains("unavailable")));
        assertTrue(driver.health("hy-1").contains("unavailable"));
    }

    @Test
    void orchestratorCanDriveDockerApiUnchanged() {
        FakeTransport transport = new FakeTransport(ENDPOINT, DockerApiDriverTest::canned);
        TestServerOrchestrator orchestrator = new TestServerOrchestrator(new DockerApiDriver(transport));

        orchestrator.startAll(List.of(hybridSpec()));

        assertEquals(RunStatus.RUNNING, orchestrator.health().get(0).status());
        assertFalse(orchestrator.logs().isEmpty());
    }

    private static ApiResponse canned(ApiRequest request) {
        String path = request.path();
        if (path.contains("/networks/create")) {
            return new ApiResponse(201, "{}");
        }
        if (path.contains("/containers/create")) {
            return new ApiResponse(201, "{\"Id\":\"cid-1\"}");
        }
        if (path.endsWith("/start") || path.endsWith("/stop")) {
            return new ApiResponse(204, "");
        }
        if (path.endsWith("/json")) {
            return new ApiResponse(200, "{\"State\":{\"Status\":\"running\"}}");
        }
        if (path.contains("/logs")) {
            return new ApiResponse(200, "line-1\nline-2");
        }
        return new ApiResponse(404, "{}");
    }

    /** 伪造 Docker API 通道：记录请求，按注入的 handler 返回响应。 */
    private static final class FakeTransport implements DockerApiTransport {

        private final DockerApiEndpoint endpoint;
        private final Function<ApiRequest, ApiResponse> handler;
        private final List<ApiRequest> requests = new ArrayList<>();
        private boolean available = true;

        FakeTransport(DockerApiEndpoint endpoint, Function<ApiRequest, ApiResponse> handler) {
            this.endpoint = endpoint;
            this.handler = handler;
        }

        void available(boolean value) {
            this.available = value;
        }

        List<ApiRequest> requests() {
            return requests;
        }

        ApiRequest request(int index) {
            return requests.get(index);
        }

        @Override
        public ApiResponse send(ApiRequest request) {
            requests.add(request);
            return handler.apply(request);
        }

        @Override
        public DockerApiEndpoint endpoint() {
            return endpoint;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public String describe() {
            return "伪造 Docker API 通道";
        }
    }
}
