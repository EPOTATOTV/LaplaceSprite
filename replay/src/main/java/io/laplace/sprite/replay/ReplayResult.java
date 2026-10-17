package io.laplace.sprite.replay;

import java.util.List;

/**
 * 回放判定结果（15.10）。
 *
 * @param matched          是否复现
 * @param sameCrashSignature 崩溃签名是否一致
 * @param sameLogFeature   日志特征是否一致
 * @param notes            判定说明，含确定性边界
 */
public record ReplayResult(boolean matched, boolean sameCrashSignature, boolean sameLogFeature,
                           List<String> notes) {

    public ReplayResult {
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
