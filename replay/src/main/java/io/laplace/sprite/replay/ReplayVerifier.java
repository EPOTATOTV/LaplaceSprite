package io.laplace.sprite.replay;

import java.util.ArrayList;
import java.util.List;

/**
 * 回放判定（15.10）。
 *
 * <p><strong>确定性边界：</strong>原版 Minecraft 不保证 tick 级确定性，世界随机数、实体遍历顺序与
 * 异步任务都可能让同一输入走出不同轨迹。因此这里只判「复现同类崩溃 / 同一日志特征」，
 * 即崩溃签名或日志特征是否相同；tick 数只写进备注，不参与判定，也不做逐 tick 状态相等断言。</p>
 */
public final class ReplayVerifier {

    /** 写进每条结果说明的边界，避免报告被误读成逐 tick 一致。 */
    public static final String DETERMINISM_NOTE =
            "原版不保证 tick 级确定性：判定只看崩溃签名与日志特征，不比对逐 tick 状态";

    public ReplayResult verify(ReplayExpectation expected, ReplayObservation observed) {
        boolean sameCrashSignature = equalNonBlank(expected.crashSignature(), observed.crashSignature());
        boolean sameLogFeature = equalNonBlank(expected.logFeature(), observed.logFeature());

        boolean matched;
        if (expected.expectsCrash()) {
            matched = sameCrashSignature || sameLogFeature;
        } else {
            // 录制时无崩溃：只有重放也没崩才算复现
            matched = !observed.hasCrash();
        }

        List<String> notes = new ArrayList<>();
        notes.add(DETERMINISM_NOTE);
        if (expected.recordedTicks() != observed.replayTicks()) {
            notes.add("录制 %d tick，重放 %d tick；tick 数差异不作为判定依据"
                    .formatted(expected.recordedTicks(), observed.replayTicks()));
        }
        if (expected.expectsCrash() && !matched) {
            notes.add("未复现记录的崩溃签名或日志特征");
        }
        if (!expected.expectsCrash() && !matched) {
            notes.add("录制时无崩溃，重放出现崩溃");
        }
        return new ReplayResult(matched, sameCrashSignature, sameLogFeature, notes);
    }

    private static boolean equalNonBlank(String expected, String observed) {
        return expected != null && observed != null && expected.equals(observed);
    }
}
