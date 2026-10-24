package io.laplace.sprite.core.plugin;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 内核事件总线：同步分发，按钩子过滤。
 *
 * <p>单个监听器抛异常只记进 {@link #errors()}，不影响同钩子上的其他监听器。
 * 监听器列表用写时复制，发布期间订阅/退订不会打乱当前这轮分发。</p>
 */
public final class KernelEventBus {

    private final List<Subscription> listeners = new CopyOnWriteArrayList<>();
    private final List<String> errors = new CopyOnWriteArrayList<>();

    /** 订阅指定钩子，关闭返回值即退订。 */
    public AutoCloseable subscribe(KernelHook hook, Consumer<KernelEvent> listener) {
        Objects.requireNonNull(hook, "hook");
        Objects.requireNonNull(listener, "listener");
        Subscription subscription = new Subscription(hook, listener);
        listeners.add(subscription);
        return subscription;
    }

    /** 同步分发给当前订阅者；异常隔离后继续往下走。 */
    public void publish(KernelEvent event) {
        Objects.requireNonNull(event, "event");
        for (Subscription subscription : listeners) {
            if (subscription.hook != event.hook()) {
                continue;
            }
            try {
                subscription.listener.accept(event);
            } catch (RuntimeException ex) {
                errors.add("监听器处理 %s/%s 失败: %s".formatted(event.hook(), event.topic(), ex));
            }
        }
    }

    /** 被吞掉的监听器异常，按发生顺序保留。 */
    public List<String> errors() {
        return List.copyOf(errors);
    }

    private final class Subscription implements AutoCloseable {

        private final KernelHook hook;
        private final Consumer<KernelEvent> listener;

        private Subscription(KernelHook hook, Consumer<KernelEvent> listener) {
            this.hook = hook;
            this.listener = listener;
        }

        @Override
        public void close() {
            listeners.remove(this);
        }
    }
}
