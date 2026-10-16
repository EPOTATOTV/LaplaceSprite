package io.laplace.sprite.crashdiag;

/**
 * 堆 / GC 摘要（15.5 内存分支）：由剖析轨提供，用于把 OOM 归类到「配额不足」还是「疑似泄漏」。
 *
 * @param heapUsedMb   堆占用（MB）
 * @param heapMaxMb    堆上限（MB）
 * @param gcPauseP99Ms GC 停顿 p99（ms），无数据时为 null
 * @param gcPauseMaxMs GC 停顿最大值（ms），无数据时为 null
 */
public record HeapGcSummary(long heapUsedMb, long heapMaxMb, Double gcPauseP99Ms, Double gcPauseMaxMs) {

    /** 堆占用比例。上限未知时返回 0。 */
    public double heapUsedRatio() {
        return heapMaxMb <= 0 ? 0.0 : (double) heapUsedMb / heapMaxMb;
    }

    public String describe() {
        String gc = gcPauseP99Ms == null ? "GC 停顿无数据"
                : "GC p99 %.1fms / max %s".formatted(gcPauseP99Ms,
                        gcPauseMaxMs == null ? "无数据" : "%.1fms".formatted(gcPauseMaxMs));
        return "堆 %dMB / %dMB（%.0f%%），%s".formatted(heapUsedMb, heapMaxMb, heapUsedRatio() * 100, gc);
    }
}
