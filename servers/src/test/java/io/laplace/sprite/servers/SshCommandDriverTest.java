package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.RunStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SSH 控制轨（12.2）：ssh 参数向量与远端命令的渲染、以及通道不可用时的降级。 */
class SshCommandDriverTest {

    private static final PlatformKey PAPER = PlatformKey.of(PlatformFamily.PAPER, "1.21.11");

    @Test
    void processTransportRendersExactSshArgumentVector() {
        ProcessSshTransport transport = new ProcessSshTransport("ssh", "10.0.0.7", "laplace", 2222,
                "/keys/id_ed25519");

        assertEquals(List.of("-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=accept-new",
                "-p", "2222", "-i", "/keys/id_ed25519", "laplace@10.0.0.7", "echo hi"),
                transport.arguments("echo hi"));
    }

    @Test
    void processTransportOmitsKeyWhenNotConfigured() {
        ProcessSshTransport transport = new ProcessSshTransport("ssh", "node-8h8g", "root", 22, "");

        assertEquals(List.of("-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=accept-new",
                "-p", "22", "root@node-8h8g", "docker ps"), transport.arguments("docker ps"));
    }

    @Test
    void unconfiguredProcessTransportDegradesInsteadOfFakingExecution() {
        ProcessSshTransport transport = new ProcessSshTransport("ssh", "", "", 22, "");

        assertFalse(transport.available());
        assertTrue(transport.describe().contains("unavailable"));

        SshTransport.ExecResult result = transport.exec("docker ps");
        assertFalse(result.success());
        assertTrue(result.stderr().contains("unavailable"));

        SshCommandDriver driver = new SshCommandDriver(transport, "/opt/laplace");
        assertDoesNotThrow(() -> driver.start(TestServerSpec.inProcess("alpha", PAPER, "1.0.0")));
        assertEquals(DriverState.UNAVAILABLE, driver.state("alpha"));
        assertEquals(RunStatus.SKIPPED, driver.status("alpha"));
        assertTrue(driver.tail("alpha", 5).stream().anyMatch(line -> line.contains("unavailable")));
    }

    @Test
    void remoteComposeCommandsQuotePathsWithSpaces() {
        SshCommandDriver driver = new SshCommandDriver(new InMemorySshTransport(),
                "/opt/laplace node", "compose files/docker-compose.yml");

        assertEquals("cd '/opt/laplace node' && docker compose -f 'compose files/docker-compose.yml' up -d 'alpha'",
                driver.composeCommand("up", "-d", "'alpha'"));
        assertEquals("cd '/opt/laplace node' && docker compose -f 'compose files/docker-compose.yml' ps 'alpha'",
                driver.composeCommand("ps", "'alpha'"));
    }

    @Test
    void driverComposesRemoteCommandsForLifecycle() {
        InMemorySshTransport transport = new InMemorySshTransport()
                .withFallback(SshTransport.ExecResult.ok("running\n"));
        SshCommandDriver driver = new SshCommandDriver(transport, "/srv/laplace",
                "deploy/docker-compose.yml");
        TestServerSpec spec = TestServerSpec.inProcess("alpha", PAPER, "1.0.0");

        driver.start(spec);
        assertEquals(DriverState.RUNNING, driver.state("alpha"));
        assertEquals(RunStatus.RUNNING, driver.status("alpha"));
        assertEquals("cd '/srv/laplace' && docker compose -f 'deploy/docker-compose.yml' up -d 'alpha'",
                transport.executed().get(0));

        assertEquals(List.of("running"), driver.tail("alpha", 10));
        assertTrue(transport.executed().get(1).contains("logs --tail 10 'alpha'"));

        assertEquals("running", driver.health("alpha"));
        assertTrue(transport.executed().get(2).contains("ps 'alpha'"));

        driver.stop("alpha");
        assertEquals(DriverState.STOPPED, driver.state("alpha"));
        assertTrue(transport.executed().get(3).contains("stop 'alpha'"));
    }

    @Test
    void failedRemoteStartIsReportedAsFailed() {
        SshCommandDriver driver = new SshCommandDriver(
                new InMemorySshTransport().withFallback(SshTransport.ExecResult.failed("no such service")),
                "/srv/laplace");

        driver.start(TestServerSpec.inProcess("beta", PAPER, "1.0.0"));

        assertEquals(DriverState.FAILED, driver.state("beta"));
        assertEquals(RunStatus.FAILED, driver.status("beta"));
        assertTrue(driver.tail("beta", 5).stream().anyMatch(line -> line.contains("no such service")));
    }
}
