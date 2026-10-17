package io.laplace.sprite.mixin;

/**
 * Mixin 注入点数量约束（15.11）。
 *
 * <p>{@code require = N} 要求实际注入点必须恰好 N 个，不然 Mixin 直接抛
 * {@code InvalidInjectionException}；{@code expect = N} 则是「期望但不强制」，
 * Mixin 自身不会因数量不符而失败。本模块沿用这个区分：require 差异阻塞 CI，
 * expect 差异只记告警，比 Mixin 的语义更严格一点，但绝不把软约束升级成硬失败。</p>
 */
public enum MixinRequireMode {

    REQUIRE("require", false),
    EXPECT("expect", true);

    private final String token;

    private final boolean soft;

    MixinRequireMode(String token, boolean soft) {
        this.token = token;
        this.soft = soft;
    }

    public String token() {
        return token;
    }

    /** 软约束：数量不符只告警。 */
    public boolean soft() {
        return soft;
    }
}
