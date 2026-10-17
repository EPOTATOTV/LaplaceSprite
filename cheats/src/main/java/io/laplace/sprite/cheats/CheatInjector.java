package io.laplace.sprite.cheats;

import io.laplace.sprite.core.model.CheatType;

import java.util.Locale;

/**
 * 单一作弊类型的注入器：把合法基线改写成该作弊行为的事件流。
 *
 * <p>注入器不修改传入的基线流，只在副本上改写字段或追加事件。</p>
 */
public interface CheatInjector extends SampleInjector {

    CheatType type();

    /** 与样本 YAML 的 injector 字段对齐：类型名去下划线转小写（AUTO_CLICKER → autoclicker）。 */
    @Override
    default String id() {
        return type().name().toLowerCase(Locale.ROOT).replace("_", "");
    }
}
