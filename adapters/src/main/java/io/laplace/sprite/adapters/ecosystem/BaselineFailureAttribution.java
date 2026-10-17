package io.laplace.sprite.adapters.ecosystem;

import java.util.Objects;

/**
 * 基线格失败归因（14.9）：对照格只装基线包、不装被测目标。
 *
 * <p>对照格复现同一故障，说明基线包自身就过不了，记「模组自身问题」；
 * 对照格干净而测试格失败，才把账挂到被测目标上。归因结论必须有对照格，不能凭栈帧猜。</p>
 */
public final class BaselineFailureAttribution {

    /** 归因结论。 */
    public enum Verdict {
        /** 基线包自身问题。 */
        MOD_ITSELF,
        /** 被测目标引入的问题。 */
        TARGET_UNDER_TEST,
        /** 两格都没失败。 */
        NO_FAILURE
    }

    /**
     * 一格的结果。
     *
     * @param cellId           格标识（含 mc / loader，便于回溯）
     * @param failed           是否失败
     * @param failureSignature 失败特征，如首个 mixin 报错行
     */
    public record CellOutcome(String cellId, boolean failed, String failureSignature) {

        public CellOutcome {
            Objects.requireNonNull(cellId, "cellId");
        }

        public static CellOutcome pass(String cellId) {
            return new CellOutcome(cellId, false, null);
        }

        public static CellOutcome fail(String cellId, String failureSignature) {
            return new CellOutcome(cellId, true, failureSignature);
        }
    }

    public Verdict attribute(CellOutcome control, CellOutcome target) {
        Objects.requireNonNull(control, "control");
        Objects.requireNonNull(target, "target");
        if (control.failed()) {
            return Verdict.MOD_ITSELF;
        }
        return target.failed() ? Verdict.TARGET_UNDER_TEST : Verdict.NO_FAILURE;
    }

    /** 只关心布尔信号的调用方用这个重载。 */
    public Verdict attribute(boolean controlReproduced, boolean targetFailed) {
        if (controlReproduced) {
            return Verdict.MOD_ITSELF;
        }
        return targetFailed ? Verdict.TARGET_UNDER_TEST : Verdict.NO_FAILURE;
    }
}
