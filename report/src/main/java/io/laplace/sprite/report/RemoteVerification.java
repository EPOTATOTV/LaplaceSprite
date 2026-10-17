package io.laplace.sprite.report;

import io.laplace.sprite.core.io.Json;

import java.util.List;

/**
 * 远端副本一致性校验（4.6）：逐条取回远端字节、重算 SHA-256 与链上 payloadHash 比对。
 *
 * @param checked    已比对条数
 * @param matched    一致条数
 * @param mismatched 内容不符条数
 * @param missing    远端缺失条数
 * @param failures   失败说明，逐条中文
 */
public record RemoteVerification(int checked, int matched, int mismatched, int missing,
                                 List<String> failures) implements ReportSection {

    public RemoteVerification {
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    /** 全部已同步证据的远端副本与链上摘要一致才算通过。 */
    public boolean consistent() {
        return mismatched == 0 && missing == 0 && failures.isEmpty();
    }

    @Override
    public String id() {
        return "evidence-remote-verify";
    }

    @Override
    public String title() {
        return "证据链 · 远端副本校验";
    }

    @Override
    public String toMarkdown() {
        StringBuilder md = new StringBuilder();
        md.append("| 项 | 值 |\n| --- | ---: |\n");
        md.append("| 已比对 | ").append(checked).append(" |\n");
        md.append("| 一致 | ").append(matched).append(" |\n");
        md.append("| 内容不符 | ").append(mismatched).append(" |\n");
        md.append("| 远端缺失 | ").append(missing).append(" |\n\n");
        if (failures.isEmpty()) {
            md.append(checked == 0 ? "无已同步证据，未做比对。\n" : "远端副本与链上摘要一致。\n");
        } else {
            md.append("失败明细：\n\n");
            failures.forEach(failure -> md.append("- ").append(failure).append('\n'));
        }
        return md.toString();
    }

    @Override
    public String toJson() {
        return Json.write(this);
    }
}
