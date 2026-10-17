package io.laplace.sprite.replay;

/**
 * 重放后观察到的结果（15.10）。
 *
 * @param crashSignature 崩溃签名；未崩溃为 null
 * @param logFeature     命中的日志特征；没有为 null
 * @param replayTicks    重放时长（tick），只写进备注，不参与相等判定
 */
public record ReplayObservation(String crashSignature, String logFeature, int replayTicks) {

    public ReplayObservation {
        crashSignature = blankToNull(crashSignature);
        logFeature = blankToNull(logFeature);
    }

    public boolean hasCrash() {
        return crashSignature != null || logFeature != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
