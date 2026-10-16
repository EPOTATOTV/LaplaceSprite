package io.laplace.sprite.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 敏感路径审计（15.4）：干净 spec 放行，挂到宿主敏感路径的 spec 拦下。 */
class SandboxAuditorTest {

    private final SandboxAuditor auditor = new SandboxAuditor();

    @Test
    void cleanSpecPassesAudit(@TempDir Path tempDir) {
        Path dataDir = tempDir.resolve("data");
        SandboxSpec spec = new SandboxSpec("case-1", "img", "PAPER", "1.21.11", 1024, 1.0, 717,
                List.of(SandboxMount.readOnly(tempDir.resolve("mods").toString(), "/data/mods")),
                dataDir, SandboxNetworkPolicy.isolate("case-1"), 25575, true, 1,
                java.util.Map.of(), java.util.Map.of());

        List<SandboxAudit> audits = auditor.audit(spec);
        assertEquals(2, audits.size(), "挂载点与数据目录都要审");
        assertTrue(audits.stream().noneMatch(SandboxAudit::sensitive));
        assertTrue(auditor.clean(spec));
        assertTrue(auditor.sensitive(spec).isEmpty());
    }

    @Test
    void sensitiveMountIsFlagged() {
        SandboxSpec spec = new SandboxSpec("case-2", "img", "PAPER", "1.21.11", 1024, 1.0, 717,
                List.of(SandboxMount.readOnly(System.getProperty("user.home"), "/data/mods")),
                Path.of("build", "sandbox-test", "data"), SandboxNetworkPolicy.isolate("case-2"), 25575,
                true, 1, java.util.Map.of(), java.util.Map.of());

        List<SandboxAudit> sensitive = auditor.sensitive(spec);
        assertEquals(1, sensitive.size());
        assertEquals(System.getProperty("user.home"), sensitive.get(0).path());
        assertTrue(sensitive.get(0).reason().contains("用户主目录"));
        assertFalse(auditor.clean(spec));
    }

    @Test
    void knownHostDangerZonesAreDetected() {
        assertSensitive("/var/run/docker.sock", "系统路径");
        assertSensitive("/etc", "系统配置目录");
        assertSensitive("C:\\", "盘符根目录");
        assertSensitive("/home/dev/.ssh", "凭据目录");
        assertSensitive("/home/dev/.aws/credentials", "凭据目录");
        assertSensitive("/home/dev/release.pem", "密钥/证书文件");
        assertSensitive("/var/lib/docker", "系统路径");
    }

    @Test
    void ordinaryProjectPathsStayClean() {
        assertFalse(new SandboxAuditor().auditPath("build/sandbox/case-1/data").sensitive());
        assertFalse(new SandboxAuditor().auditPath("server/mods/example.jar").sensitive());
        assertFalse(new SandboxAuditor().auditPath("/opt/laplace/fixtures").sensitive());
        assertThrows(IllegalArgumentException.class, () -> new SandboxAuditor().auditPath(" "));
    }

    private void assertSensitive(String path, String reasonFragment) {
        SandboxAudit audit = new SandboxAuditor().auditPath(path);
        assertTrue(audit.sensitive(), path + " 应判为敏感路径");
        assertTrue(audit.reason().contains(reasonFragment),
                path + " 的原因应含「" + reasonFragment + "」，实际 " + audit.reason());
    }
}
