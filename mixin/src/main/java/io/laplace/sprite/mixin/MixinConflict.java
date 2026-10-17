package io.laplace.sprite.mixin;

import java.util.List;
import java.util.Objects;

/**
 * 一个目标类上的注入冲突（15.11 P1）。
 *
 * @param targetClass  被注入的 MC 类
 * @param participants 参与注入的 mod id（去重，保持首次出现顺序）
 * @param kinds        涉及的注入方式（去重）
 * @param risk         风险等级，按 {@link MixinRisk} 排序
 * @param reason       中文归因说明
 */
public record MixinConflict(String targetClass, List<String> participants, List<MixinInjectionKind> kinds,
                            MixinRisk risk, String reason) {

    public MixinConflict {
        Objects.requireNonNull(targetClass, "targetClass");
        participants = participants == null ? List.of() : List.copyOf(participants);
        kinds = kinds == null ? List.of() : List.copyOf(kinds);
        Objects.requireNonNull(risk, "risk");
        Objects.requireNonNull(reason, "reason");
    }
}
