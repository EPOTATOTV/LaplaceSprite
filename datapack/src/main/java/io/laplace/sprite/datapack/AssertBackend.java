package io.laplace.sprite.datapack;

/**
 * 数据包用例的断言后端（15.8）。
 */
public enum AssertBackend {

    /** 日志尾部匹配：断言 {@code /function} 回显最后一行以期望串结尾。 */
    ENDS_WITH,
    /**
     * 官方 Game Test 断言：1.21.5 起可用，平台先 {@code /test create} 搭结构，再解析回包。
     * 低版本列由执行器回退到 {@link #ENDS_WITH}。
     */
    ASSERT_GAMETEST
}
