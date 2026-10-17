package io.laplace.sprite.cheats;

import io.laplace.sprite.core.event.EventStream;

/**
 * 样本注入器：把合法基线改写成该样本对应的事件流。
 *
 * <p>作弊子类与通用异常类别共用该契约；注入器只读基线，输出写到新的事件流。</p>
 */
public interface SampleInjector {

    /** 与样本 YAML 的 {@code injector} 字段一致的稳定 id。 */
    String id();

    EventStream inject(CheatInjectionRequest request);
}
