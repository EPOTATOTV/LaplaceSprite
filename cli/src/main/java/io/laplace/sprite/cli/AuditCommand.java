package io.laplace.sprite.cli;

import io.laplace.sprite.report.EvidenceAuditTrail;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code laplace audit}：全局证据审计（v3.0 L3-04）。
 *
 * <p>审计事件统一落在 {@code <workspace>/audit}，默认就是 {@code build/laplace/audit}；
 * 导出与校验都读同一份 EvidenceVault，校验复用 HMAC 证据链。</p>
 */
@Command(name = "audit", description = "全局证据审计：导出事件 JSON、校验证据链",
        mixinStandardHelpOptions = true,
        subcommands = {AuditCommand.ExportCommand.class, AuditCommand.VerifyCommand.class})
final class AuditCommand implements Runnable {

    static final String DEFAULT_WORKSPACE = "build/laplace/audit";

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    private static Path workspace(Path override) {
        return override != null ? override : Path.of(DEFAULT_WORKSPACE);
    }

    @Command(name = "export", description = "导出全部审计事件为 JSON 数组",
            mixinStandardHelpOptions = true)
    static final class ExportCommand implements Callable<Integer> {

        @Option(names = "--workspace", description = "审计目录，默认 build/laplace/audit")
        Path workspace;

        @Option(names = "--out", required = true, paramLabel = "<json>", description = "导出文件路径")
        Path output;

        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            Path root = workspace(workspace);
            try {
                EvidenceAuditTrail trail = new EvidenceAuditTrail(root);
                int count = trail.events().size();
                trail.exportJson(output);
                out.printf("审计事件导出 %s · %d 条 → %s%n", root.toAbsolutePath(), count,
                        output.toAbsolutePath());
                return 0;
            } catch (RuntimeException ex) {
                err.println("审计导出失败：" + ex.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "verify", description = "校验审计证据链 HMAC",
            mixinStandardHelpOptions = true)
    static final class VerifyCommand implements Callable<Integer> {

        @Option(names = "--workspace", description = "审计目录，默认 build/laplace/audit")
        Path workspace;

        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            Path root = workspace(workspace);
            try {
                var verification = new EvidenceAuditTrail(root).verify();
                out.printf("审计证据链校验 %s · 链接 %d 条 · 首断点 %s%n",
                        verification.valid() ? "通过" : "未通过", verification.checkedLinks(),
                        verification.firstBrokenIndex() < 0
                                ? "无" : String.valueOf(verification.firstBrokenIndex()));
                out.println("  说明：" + verification.reason());
                return verification.valid() ? 0 : 1;
            } catch (RuntimeException ex) {
                err.println("审计校验失败：" + ex.getMessage());
                return 1;
            }
        }
    }
}
