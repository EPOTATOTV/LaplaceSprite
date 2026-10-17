package io.laplace.sprite.loadtest;

import java.util.List;

/**
 * 门禁结论（15.9）。
 *
 * @param passed            是否通过
 * @param breakingConcurrency 第一个破线的并发；全部通过时为 null
 * @param violations        破线明细
 * @param curvePoints       并发 × 指标曲线点
 */
public record LoadVerdict(boolean passed, Integer breakingConcurrency, List<String> violations,
                          List<CurvePoint> curvePoints) {

    public LoadVerdict {
        violations = violations == null ? List.of() : List.copyOf(violations);
        curvePoints = curvePoints == null ? List.of() : List.copyOf(curvePoints);
    }

    /** 一个并发档位的观测值。 */
    public record CurvePoint(int concurrency, double tps, double msptP95, boolean passed) {
    }

    public boolean broke() {
        return breakingConcurrency != null;
    }
}
