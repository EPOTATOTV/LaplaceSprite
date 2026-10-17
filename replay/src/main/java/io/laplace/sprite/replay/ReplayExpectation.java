package io.laplace.sprite.replay;

/**
 * 录制现场期望复现的结果（15.10）。
 *
 * @param crashSignature 崩溃签名；录制时无崩溃为 null
 * @param logFeature     日志特征；录制时无异常日志为 null
 * @param recordedTicks  录制时长（tick），只写进备注，不参与相等判定
 */
public record ReplayExpectation(String crashSignature, String logFeature, int recordedTicks) {

    public ReplayExpectation {
        crashSignature = blankToNull(crashSignature);
        logFeature = blankToNull(logFeature);
    }

    public boolean expectsCrash() {
        return crashSignature != null || logFeature != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
