package io.laplace.sprite.core.spi;

import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.PlatformKey;

/**
 * ④ 事件桥（14.4）：平台事件 → 内部事件流。
 *
 * <p>Folia 区域 tick、Velocity 异步挂起（Continuation）、BungeeCord intent 在桥内收敛为
 * 「事件 + 时间戳 + 上下文」，对断言引擎透明。</p>
 */
public interface EventBridge {

    /** 返回该平台对应的归一化事件流。 */
    EventStream bridge(PlatformKey key);
}
