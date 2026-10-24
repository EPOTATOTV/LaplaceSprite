package io.laplace.sprite.report;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.EvidenceRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 全局证据审计（L3-04）：多类事件落盘、重启可读、可导出，篡改 blob 后校验失败。 */
class EvidenceAuditTrailTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    @TempDir
    Path dir;

    @Test
    void recordsEventsAndSurvivesRestartInTimeOrder() {
        EvidenceAuditTrail trail = new EvidenceAuditTrail(dir);
        trail.record(new AuditEvent(AuditType.DOWNLOAD, "26.3", "client.jar sha1=abc",
                BASE.plusSeconds(2), Map.of("sha1", "abc")));
        trail.record(new AuditEvent(AuditType.INSTANCE_START, "instance-1", "启动",
                BASE, Map.of()));
        trail.record(new AuditEvent(AuditType.TEST_RUN, "run-1", "基准",
                BASE.plusSeconds(1), Map.of()));

        EvidenceAuditTrail reopened = new EvidenceAuditTrail(dir);
        List<AuditEvent> events = reopened.events();
        assertEquals(3, events.size());
        assertEquals(List.of(AuditType.INSTANCE_START, AuditType.TEST_RUN, AuditType.DOWNLOAD),
                events.stream().map(AuditEvent::type).toList());
        assertEquals("abc", events.get(2).attributes().get("sha1"));
        assertTrue(reopened.verify().valid(), reopened.verify().reason());
    }

    @Test
    void exportWritesFullJsonArray() throws IOException {
        EvidenceAuditTrail trail = new EvidenceAuditTrail(dir);
        trail.record(new AuditEvent(AuditType.ASSET_CHANGE, "mod-a", "新增", BASE, Map.of()));
        trail.record(new AuditEvent(AuditType.SANDBOX_CHECK, "case-1", "通过", BASE.plusSeconds(1),
                Map.of()));

        Path out = dir.resolve("export/audit.json");
        trail.exportJson(out);

        assertTrue(Files.isRegularFile(out));
        AuditEvent[] exported = Json.read(Files.readString(out, StandardCharsets.UTF_8),
                AuditEvent[].class);
        assertEquals(2, exported.length);
        assertEquals(AuditType.ASSET_CHANGE, exported[0].type());
    }

    @Test
    void tamperedBlobFailsVerification() throws IOException {
        EvidenceAuditTrail trail = new EvidenceAuditTrail(dir);
        EvidenceRecord record = trail.record(new AuditEvent(AuditType.DOWNLOAD, "26.3", "下载",
                BASE, Map.of()));
        assertTrue(trail.verify().valid());

        Files.writeString(Path.of(record.path()), "tampered", StandardCharsets.UTF_8);

        EvidenceChainVerifier.ChainVerification verification = trail.verify();
        assertFalse(verification.valid());
        assertEquals(0, verification.firstBrokenIndex());
        assertTrue(verification.reason().contains("payloadHash"), verification.reason());
    }
}
