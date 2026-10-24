package io.laplace.sprite.core.plugin;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 内核事件：钩子 + 主题 + 发生时间 + 载荷。
 *
 * <p>载荷在构造时复制为不可变视图，插件往事件里塞的可变 map 之后不会再影响已发布的事件。</p>
 */
public record KernelEvent(KernelHook hook, String topic, Instant at, Map<String, Object> payload) {

    public KernelEvent {
        Objects.requireNonNull(hook, "hook");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(at, "at");
        payload = payload == null || payload.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
