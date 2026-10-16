package io.laplace.sprite.cheats;

import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.CheatType;

/**
 * 单一作弊类型的注入器：把合法基线改写成该作弊行为的事件流。
 *
 * <p>注入器不修改传入的基线流，只在副本上改写字段或追加事件。</p>
 */
public interface CheatInjector {

    CheatType type();

    EventStream inject(CheatInjectionRequest request);
}
