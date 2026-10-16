package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.RunStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三条环境轨道（12.1 / 12.2）：开发轨、容器轨降级、本地进程轨降级。 */
class ServerDriverTest {

    private static final PlatformKey PAPER = PlatformKey.of(PlatformFamily.PAPER, "1.21.11");

    @Test
    void inProcessDriverRunsTheDevelopmentTrack() {
        InProcessDriver driver = new InProcessDriver();
        TestServerSpec spec = TestServerSpec.inProcess("dev-1", PAPER, "1.0.0");
        assertEquals(RunStatus.SKIPPED, driver.status(spec.instanceId()), "未启动前为 SKIPPED");

        driver.start(spec);
        assertTrue(driver.running(spec.instanceId()));
        assertEquals(DriverState.RUNNING, driver.state(spec.instanceId()));
        assertEquals(RunStatus.RUNNING, driver.status(spec.instanceId()));
        assertFalse(driver.tail(spec.instanceId(), 10).isEmpty());

        driver.stop(spec.instanceId());
        assertFalse(driver.running(spec.instanceId()));
        assertEquals(RunStatus.ABORTED, driver.status(spec.instanceId()));
    }

    @Test
    void dockerDriverDegradesGracefullyWhenDockerIsAbsent() {
        DockerComposeDriver driver = new DockerComposeDriver("definitely-not-a-docker-binary-xyz");
        assertFalse(driver.available(), "不存在的 docker 可执行文件应判为不可用");
        TestServerSpec spec = TestServerSpec.inProcess("docker-1", PAPER, "1.0.0");

        assertDoesNotThrow(() -> driver.start(spec), "docker 缺失不得抛出");
        assertFalse(driver.running(spec.instanceId()));
        assertEquals(DriverState.UNAVAILABLE, driver.state(spec.instanceId()));
        assertEquals(RunStatus.SKIPPED, driver.status(spec.instanceId()));
        assertTrue(driver.tail(spec.instanceId(), 5).stream().anyMatch(line -> line.contains("unavailable")),
                "必须给出非致命的 unavailable 说明");
        assertDoesNotThrow(() -> driver.stop(spec.instanceId()));
    }

    @Test
    void dockerDriverRendersComposeFileForTheInstanceSet(@TempDir Path tempDir) {
        DockerComposeDriver driver = new DockerComposeDriver("definitely-not-a-docker-binary-xyz", tempDir);
        List<TestServerSpec> specs = List.of(
                TestServerSpec.inProcess("alpha", PAPER, "1.0.0"),
                new TestServerSpec("beta", PlatformKey.of(PlatformFamily.FABRIC, "1.21.11"), 4096, 25566, true,
                        true, "itzg/minecraft-server:java21", List.of("-XX:+UseG1GC"), "0.19.5"));
        String yaml = driver.composeContent(specs);
        assertTrue(yaml.startsWith("services:"));
        assertTrue(yaml.contains("  alpha:"));
        assertTrue(yaml.contains("  beta:"));
        assertTrue(yaml.contains("EULA: \"TRUE\""));
        assertTrue(yaml.contains("MEMORY: \"4096M\""));
        assertTrue(yaml.contains("-XX:+UseG1GC"));
        assertTrue(yaml.contains("25566:25566"));

        Path compose = driver.composeFile();
        driver.writeCompose(compose, specs);
        assertTrue(compose.toFile().isFile());
    }

    @Test
    void localProcessDriverDegradesWhenJarIsMissing(@TempDir Path tempDir) {
        LocalProcessDriver driver = new LocalProcessDriver(tempDir.resolve("missing-server.jar"));
        assertFalse(driver.available());
        TestServerSpec spec = TestServerSpec.inProcess("local-1", PAPER, "1.0.0");

        assertDoesNotThrow(() -> driver.start(spec), "缺少 jar 不得抛出");
        assertEquals(DriverState.UNAVAILABLE, driver.state(spec.instanceId()));
        assertEquals(RunStatus.SKIPPED, driver.status(spec.instanceId()));
        assertTrue(driver.tail(spec.instanceId(), 5).stream().anyMatch(line -> line.contains("unavailable")));
        assertEquals("local-process", driver.name());
    }

    @Test
    void jvmArgsIncludeMemoryFirst() {
        TestServerSpec spec = new TestServerSpec("mem", PAPER, 2048, 25565, true, true, null,
                List.of("-XX:+UseZGC"), "1.0.0");
        List<String> args = spec.jvmArgsWithMemory();
        assertEquals("-Xmx2048M", args.get(0));
        assertEquals("-Xms1024M", args.get(1));
        assertEquals("-XX:+UseZGC", args.get(2));
    }
}
