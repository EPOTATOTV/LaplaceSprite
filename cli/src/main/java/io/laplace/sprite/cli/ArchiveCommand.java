package io.laplace.sprite.cli;

import io.laplace.sprite.core.model.EvidenceRecord;
import io.laplace.sprite.core.model.RegressionCase;
import io.laplace.sprite.report.EvidenceChainVerifier;
import io.laplace.sprite.report.EvidenceVault;
import io.laplace.sprite.report.LocalMirrorRemoteStore;
import io.laplace.sprite.report.RegressionLibrary;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code laplace archive}：查看证据库与回归库（4.6 输出层）。
 */
@Command(name = "archive", description = "查看证据链（EvidenceVault）与回归用例库（RegressionLibrary）",
        mixinStandardHelpOptions = true)
final class ArchiveCommand implements Callable<Integer> {

    @Option(names = "--workspace", description = "工作目录，默认 build/laplace")
    Path workspace;

    @Option(names = "--run", description = "只看某次运行的证据")
    String runId;

    @Option(names = "--regression", description = "改列回归用例库")
    boolean regression;

    @Option(names = "--verify-chain", description = "校验证据链 HMAC（防删改留痕）")
    boolean verifyChain;

    @Option(names = "--sync-remote", description = "把本地证据同步到远端存储（默认本地镜像目录）")
    boolean syncRemote;

    @Option(names = "--verify-remote", description = "校验远端副本与链上 payloadHash 是否一致")
    boolean verifyRemote;

    @Option(names = "--remote-dir", description = "远端镜像目录，默认 <workspace>/evidence-remote")
    Path remoteDir;

    @Option(names = "--limit", defaultValue = "50", description = "最多列出条数")
    int limit;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        Path ws = Support.workspace(workspace);
        var out = spec.commandLine().getOut();

        if (regression) {
            RegressionLibrary library = new RegressionLibrary(ws.resolve("regression.json"));
            out.printf("回归用例库 %s（%d 条）%n", ws.resolve("regression.json").toAbsolutePath(), library.size());
            library.all().stream().limit(limit).forEach(c -> out.printf("  %s ← flag %s · 轨迹 %s · 修复 %s%n",
                    c.id(), c.flagId(), c.trajectoryRef(), c.fixRef()));
            return 0;
        }

        EvidenceVault vault = new EvidenceVault(ws.resolve("evidence"));

        if (verifyChain) {
            var verification = new EvidenceChainVerifier(vault).verify();
            out.printf("证据链校验 %s · 链接 %d 条 · 首断点 %s%n", verification.valid() ? "通过" : "未通过",
                    verification.checkedLinks(),
                    verification.firstBrokenIndex() < 0 ? "无" : String.valueOf(verification.firstBrokenIndex()));
            if (verification.reason() != null) {
                out.println("  说明：" + verification.reason());
            }
            return verification.valid() ? 0 : 1;
        }

        if (syncRemote || verifyRemote) {
            Path mirror = remoteDir != null ? remoteDir : ws.resolve("evidence-remote");
            var store = new LocalMirrorRemoteStore(mirror);
            if (syncRemote) {
                var report = vault.syncRemote(store);
                out.printf("远端同步 %s · 尝试 %d · 上传 %d · 跳过 %d · 失败 %d%n", mirror.toAbsolutePath(),
                        report.attempted(), report.uploaded(), report.skipped(), report.failed());
                report.failures().forEach(failure -> out.println("  - " + failure));
                if (report.failed() > 0) {
                    return 1;
                }
            }
            if (verifyRemote) {
                var verification = vault.verifyRemote(store);
                out.printf("远端校验 %s · 检查 %d · 一致 %d · 不一致 %d · 缺失 %d%n",
                        verification.consistent() ? "通过" : "未通过", verification.checked(),
                        verification.matched(), verification.mismatched(), verification.missing());
                verification.failures().forEach(failure -> out.println("  - " + failure));
                return verification.consistent() ? 0 : 1;
            }
            return 0;
        }

        List<EvidenceRecord> records = runId == null || runId.isBlank()
                ? vault.all()
                : vault.byRun(runId);
        out.printf("证据库 %s（本地 %d 条 · 已上云 %d 条%s）%n", ws.resolve("evidence").toAbsolutePath(),
                records.size(), vault.remoteCount(),
                runId == null || runId.isBlank() ? "" : "，运行 " + runId);
        records.stream()
                .sorted(Comparator.comparing(EvidenceRecord::createdAt).reversed())
                .limit(limit)
                .forEach(r -> out.printf("  [%s] %s · %s · %s%n", r.kind(), r.runId(), r.description(),
                        r.path()));
        return 0;
    }
}
