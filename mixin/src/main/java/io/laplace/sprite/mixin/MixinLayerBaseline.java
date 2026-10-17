package io.laplace.sprite.mixin;

import java.util.List;

/**
 * Mixin 行为基线（15.11 P2）。
 *
 * @param loaderVersionDeclared   fabric.mod.json 里声明的 Loader 版本下界；无法解析时为空
 * @param subsystemVersionObserved 启动日志里的 SpongePowered MIXIN Subsystem Version
 * @param simulatedOldBehaviour    Loader 是否按 0.12 之前的旧行为模拟
 * @param consistent              声明与观测是否一致
 * @param notes                   中文说明
 */
public record MixinLayerBaseline(String loaderVersionDeclared, String subsystemVersionObserved,
                                 boolean simulatedOldBehaviour, boolean consistent, List<String> notes) {

    public MixinLayerBaseline {
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
