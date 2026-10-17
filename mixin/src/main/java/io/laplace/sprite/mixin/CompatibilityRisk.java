package io.laplace.sprite.mixin;

import java.util.List;
import java.util.Objects;

/**
 * 兼容性风险用例（15.11 P2）。
 *
 * <p>{@code markers} 保存命中的原始日志行，调用方原样留存日志即可回溯。</p>
 *
 * @param modId   模组 id
 * @param markers 命中的重定向日志行
 * @param risk    风险说明
 */
public record CompatibilityRisk(String modId, List<String> markers, String risk) {

    public static final String COMPATIBILITY_RISK =
            "兼容性风险：重定向记录说明被测 mod 存在隐含加载顺序依赖，需复核排序并留存启动日志";

    public CompatibilityRisk {
        Objects.requireNonNull(modId, "modId");
        markers = markers == null ? List.of() : List.copyOf(markers);
        Objects.requireNonNull(risk, "risk");
    }
}
