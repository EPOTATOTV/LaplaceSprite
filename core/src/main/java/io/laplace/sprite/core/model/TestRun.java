package io.laplace.sprite.core.model;

import java.time.Instant;
import java.util.List;

/**
 * 一次测试运行（第五章 TestRun）：11.2 测试运行表的一行。
 *
 * @param id           运行 id
 * @param target       检测目标 id
 * @param suite        套件名（legit / cheat / boundary / concurrency）
 * @param platform     运行平台；进程内运行为空
 * @param metrics      指标集
 * @param breakdown    检出率三视图明细
 * @param evidenceRefs 证据引用
 * @param flags        flag 明细
 * @param status       状态
 * @param seed         随机种子（可复现）
 * @param startedAt    开始时间
 * @param durationMs   耗时
 */
public record TestRun(
        String id,
        String target,
        String suite,
        PlatformKey platform,
        MetricSet metrics,
        DetectionBreakdown breakdown,
        List<String> evidenceRefs,
        List<FlagEvent> flags,
        RunStatus status,
        long seed,
        Instant startedAt,
        long durationMs) {

    public TestRun {
        breakdown = breakdown == null ? DetectionBreakdown.empty() : breakdown;
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        flags = flags == null ? List.of() : List.copyOf(flags);
    }

    public List<FlagEvent> falsePositives() {
        return flags.stream().filter(FlagEvent::falsePositive).toList();
    }

    /** 被豁免的合规 flag：不计入误报率，但仍留在证据链里供审计。 */
    public List<FlagEvent> exemptedFlags() {
        return flags.stream().filter(FlagEvent::isExempted).toList();
    }

    /** 真检出：既不是误报，也不是被豁免的合规触发。 */
    public List<FlagEvent> detections() {
        return flags.stream().filter(f -> !f.falsePositive() && !f.isExempted()).toList();
    }
}
