package io.laplace.sprite.report;

import io.laplace.sprite.core.io.Json;

import java.util.List;

/**
 * 远端同步结果（4.6）：一次 {@link EvidenceVault#syncRemote} 的逐条统计，可直接作为报告一节。
 *
 * @param attempted 参与本轮同步的本地证据条数
 * @param uploaded  本轮新上传成功的条数
 * @param skipped   已有远端引用、本轮跳过的条数
 * @param failed    上传失败的条数（含存储未配置）
 * @param failures  失败说明，逐条中文
 */
public record RemoteSyncReport(int attempted, int uploaded, int skipped, int failed,
                               List<String> failures) implements ReportSection {

    public RemoteSyncReport {
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    @Override
    public String id() {
        return "evidence-remote-sync";
    }

    @Override
    public String title() {
        return "证据链 · 云端同步";
    }

    @Override
    public String toMarkdown() {
        StringBuilder md = new StringBuilder();
        md.append("| 项 | 值 |\n| --- | ---: |\n");
        md.append("| 参与同步 | ").append(attempted).append(" |\n");
        md.append("| 新上传 | ").append(uploaded).append(" |\n");
        md.append("| 已同步跳过 | ").append(skipped).append(" |\n");
        md.append("| 失败 | ").append(failed).append(" |\n\n");
        if (failures.isEmpty()) {
            md.append("无失败项：本地证据均已有远端副本或被本轮同步。\n");
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
