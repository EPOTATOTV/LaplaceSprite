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
import java.util.List;
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

    @Option(names = "--search", description = "按关键词搜索已安装样本包（相关度 + 可信度排序）")
    String search;

    @Option(names = "--revoke", description = "吊销样本包版本，格式 <包 id>@<版本>")
    String revoke;

    @Option(names = "--reason", description = "吊销原因")
    String reason;

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
            try {
                return record(market.install(pkg), out);
            } catch (SecurityException | IllegalArgumentException ex) {
                out.println("安装被拒绝：" + ex.getMessage());
                return 1;
            }
        }
        if (rollback != null) {
            try {
                return record(market.rollback(rollback), out);
            } catch (SecurityException | IllegalStateException ex) {
                out.println("回滚被拒绝：" + ex.getMessage());
                return 1;
            }
        }
        if (revoke != null) {
            int split = revoke.lastIndexOf('@');
            if (split <= 0 || split == revoke.length() - 1) {
                throw new CommandLine.ParameterException(spec.commandLine(),
                        "--revoke 格式应为 <包 id>@<版本>");
            }
            SampleMarket.Revocation revocation = market.revoke(revoke.substring(0, split),
                    revoke.substring(split + 1), reason);
            out.printf("已吊销 %s@%s（%s）%n", revocation.packageId(), revocation.version(),
                    revocation.reason().isBlank() ? "未注明原因" : revocation.reason());
            return 0;
        }
        if (search != null) {
            List<SampleMarket.SearchHit> hits = market.search(search);
            hits.forEach(hit -> out.printf("  %s@%s — %s · 相关度 %.1f · 可信度 %.2f%s%n",
                    hit.pkg().id(), hit.pkg().version(), hit.pkg().publisher(), hit.relevance(),
                    hit.trustScore(), hit.revoked() ? " · 已吊销" : ""));
            out.printf("共 %d 个结果%n", hits.size());
            return 0;
        }
        if (list) {
            market.all().forEach(pkg -> out.printf("  %s@%s — %s（%d 个样本，可信度 %.2f）%n",
                    pkg.id(), pkg.version(), pkg.publisher(), pkg.samples().size(), pkg.trustScore()));
            out.printf("共 %d 个包%n", market.all().size());
            return 0;
        }
        throw new CommandLine.ParameterException(spec.commandLine(),
                "至少指定 --install / --rollback / --verify / --list / --search / --revoke 之一");
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
