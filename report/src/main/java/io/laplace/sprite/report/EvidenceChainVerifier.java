package io.laplace.sprite.report;

import io.laplace.sprite.core.model.EvidenceRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 证据链校验（4.6）：逐环重算 mac 与 payloadHash，检出内容被改、链接被删/被重排、mac 被伪造。
 *
 * <p>能查出什么取决于密钥是否外置保管：密钥与数据同处一地时，能同时改写两者的攻击者可以重算整条链，
 * 本校验只能说明「链内自洽」。整段后缀被整体删除且索引同步删除也无法被发现——那要靠 vault 之外
 * 锚定链尾 mac 或条目数。</p>
 */
public final class EvidenceChainVerifier {

    /** 校验结论。{@code firstBrokenIndex} 无断点时取 -1。 */
    public record ChainVerification(boolean valid, int checkedLinks, int firstBrokenIndex, String reason) {
    }

    private final EvidenceVault vault;

    public EvidenceChainVerifier(EvidenceVault vault) {
        this.vault = vault;
    }

    public ChainVerification verify() {
        List<EvidenceChainLink> links = vault.chain();
        List<EvidenceRecord> records = vault.all();
        if (links.size() != records.size()) {
            return new ChainVerification(false, Math.min(links.size(), records.size()), links.size(),
                    "链长 %d 与索引 %d 不一致：有链接被删或未随索引落盘".formatted(links.size(), records.size()));
        }
        Map<String, EvidenceRecord> byId = records.stream()
                .collect(Collectors.toMap(EvidenceRecord::id, Function.identity()));
        byte[] key = vault.hmacKey();

        for (int i = 0; i < links.size(); i++) {
            EvidenceChainLink link = links.get(i);
            if (link.index() != i) {
                return new ChainVerification(false, i, i,
                        "第 %d 环 index=%d 与位置不符：链被重排".formatted(i, link.index()));
            }
            String expectedPrev = i == 0 ? EvidenceChainLink.GENESIS_PREV_MAC : links.get(i - 1).mac();
            if (!expectedPrev.equals(link.prevMac())) {
                return new ChainVerification(false, i, i,
                        "第 %d 环 prevMac 与上一环 mac 不连续：有链接被删或被重排".formatted(i));
            }
            String recomputed = EvidenceChainLink.mac(key, link.index(), link.timestamp(), link.payloadHash(),
                    link.prevMac());
            if (!recomputed.equals(link.mac())) {
                return new ChainVerification(false, i, i,
                        "第 %d 环 mac 校验失败：字段被改或 mac 被伪造".formatted(i));
            }
            EvidenceRecord record = byId.get(link.evidenceId());
            if (record == null) {
                return new ChainVerification(false, i, i,
                        "第 %d 环引用的证据 %s 不在索引中".formatted(i, link.evidenceId()));
            }
            String actual = payloadHashOrNull(record.path());
            if (actual == null) {
                return new ChainVerification(false, i, i,
                        "第 %d 环证据 %s 的落盘文件缺失".formatted(i, link.evidenceId()));
            }
            if (!actual.equals(link.payloadHash())) {
                return new ChainVerification(false, i, i,
                        "第 %d 环证据 %s 内容与 payloadHash 不符：落盘内容被改".formatted(i, link.evidenceId()));
            }
        }
        return new ChainVerification(true, links.size(), -1, "全部 %d 环校验通过".formatted(links.size()));
    }

    private static String payloadHashOrNull(String path) {
        try {
            return EvidenceChainLink.payloadHash(Files.readAllBytes(Path.of(path)));
        } catch (IOException ex) {
            return null;
        }
    }
}
