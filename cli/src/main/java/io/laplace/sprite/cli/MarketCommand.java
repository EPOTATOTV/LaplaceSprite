package io.laplace.sprite.cli;

import io.laplace.sprite.cheats.market.SampleMarket;
import io.laplace.sprite.cheats.market.SamplePackage;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.report.EvidenceVault;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.concurrent.Callable;

/**
 * {@code laplace market}：场景与样本市场（升级实施文档 L-08）。
 *
 * <p>安装 / 回滚都要求签名校验通过；提供 {@code --evidence} 时把操作记录写入证据链。
 * 密钥优先取 {@code --key}，其次环境变量 {@value #KEY_ENV}，两者都没有就拒绝执行。</p>
 */
@Command(name = "market", description = "样本市场：签名校验、安装、回滚与证据归档",
        mixinStandardHelpOptions = true)
final class MarketCommand implements Callable<Integer> {

    public static final String KEY_ENV = "LAPLACE_MARKET_KEY";

    @Option(names = "--root", description = "市场目录，默认 build/laplace/market")
    Path root;

    @Option(names = "--key", description = "签名密钥（64 位 hex 或原始字符串）")
    String key;

    @Option(names = "--install", description = "安装样本包 JSON")
    Path install;

    @Option(names = "--rollback", description = "回滚到上一个版本（参数为包 id）")
    String rollback;

    @Option(names = "--verify", description = "只校验样本包签名")
    Path verify;

    @Option(names = "--list", description = "列出当前已安装的样本包")
    boolean list;

    @Option(names = "--evidence", description = "证据库目录；提供后把安装记录写入证据链")
    Path evidence;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        var out = spec.commandLine().getOut();
        byte[] signKey = resolveKey();
        SampleMarket market = new SampleMarket(root == null ? Path.of("build", "laplace", "market") : root, signKey);

        if (verify != null) {
            SamplePackage pkg = Json.readFile(verify, SamplePackage.class);
            boolean ok = market.verify(pkg);
            out.printf("签名校验：%s（%s@%s）%n", ok ? "通过" : "未通过", pkg.id(), pkg.version());
            return ok ? 0 : 1;
        }
        if (install != null) {
            SamplePackage pkg = Json.readFile(install, SamplePackage.class);
            return record(market.install(pkg), out);
        }
        if (rollback != null) {
            return record(market.rollback(rollback), out);
        }
        if (list) {
            market.all().forEach(pkg -> out.printf("  %s@%s — %s（%d 个样本）%n",
                    pkg.id(), pkg.version(), pkg.publisher(), pkg.samples().size()));
            out.printf("共 %d 个包%n", market.all().size());
            return 0;
        }
        throw new CommandLine.ParameterException(spec.commandLine(),
                "至少指定 --install / --rollback / --verify / --list 之一");
    }

    private int record(SampleMarket.InstallRecord record, java.io.PrintWriter out) {
        out.printf("样本包 %s@%s %s，样本 %d 个%n", record.packageId(), record.version(),
                record.action(), record.sampleIds().size());
        if (evidence != null) {
            EvidenceVault vault = new EvidenceVault(evidence);
            var archived = vault.archive("market", "sample-" + record.action(), record.evidencePayload(),
                    "样本包 " + record.packageId() + "@" + record.version() + " " + record.action());
            out.printf("  证据已归档：%s%n", archived.id());
        }
        return 0;
    }

    private byte[] resolveKey() {
        String raw = key != null ? key : System.getenv(KEY_ENV);
        if (raw == null || raw.isBlank()) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "缺少市场签名密钥：用 --key 或环境变量 " + KEY_ENV + " 提供");
        }
        String trimmed = raw.trim();
        if (trimmed.matches("[0-9a-fA-F]{64}")) {
            return HexFormat.of().parseHex(trimmed);
        }
        return trimmed.getBytes(StandardCharsets.UTF_8);
    }
}
