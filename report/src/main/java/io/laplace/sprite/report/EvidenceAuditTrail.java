package io.laplace.sprite.report;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.EvidenceRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 全局证据审计（v3.0 L3-04）：下载、实例启动、测试运行、资产变更、沙箱检查都写进同一个
 * {@link EvidenceVault}（固定 runId {@code audit}），复用现有 HMAC 链，重启后仍可查、可校验。
 *
 * <p>事件内容以 JSON 落盘，索引与链由 vault 负责；这里只做类型到 kind 的映射和读回排序。</p>
 */
public final class EvidenceAuditTrail {

    private static final String RUN_ID = "audit";

    private final EvidenceVault vault;

    public EvidenceAuditTrail(Path root) {
        this.vault = new EvidenceVault(root);
    }

    /** 归档一条事件并返回证据记录，blob 路径可从记录里取到。 */
    public EvidenceRecord record(AuditEvent event) {
        String kind = event.type().name().toLowerCase(Locale.ROOT);
        String description = event.detail().isBlank()
                ? event.subject() : event.subject() + " · " + event.detail();
        return vault.archive(RUN_ID, kind, Json.write(event), description);
    }

    /** 读回全部事件并按发生时间排序；索引里的 blob 缺失或损坏会如实抛错，不静默跳过。 */
    public List<AuditEvent> events() {
        return vault.byRun(RUN_ID).stream()
                .map(record -> readEvent(record.path()))
                .sorted(Comparator.comparing(AuditEvent::at))
                .toList();
    }

    /** 导出完整事件数组 JSON。 */
    public void exportJson(Path out) {
        Json.writeFile(out, events());
    }

    /** 复用现有证据链校验。 */
    public EvidenceChainVerifier.ChainVerification verify() {
        return new EvidenceChainVerifier(vault).verify();
    }

    private static AuditEvent readEvent(String path) {
        try {
            return Json.read(Files.readString(Path.of(path), StandardCharsets.UTF_8), AuditEvent.class);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
