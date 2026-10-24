package io.laplace.sprite.core.plugin;

import java.util.List;

/**
 * 测试套件提供者契约：套件能不能跑由插件是否加载决定，CLI 才能在插件缺失时提前拒绝，
 * 而不是等到生成样本阶段才因为缺类或空矩阵失败。
 */
public interface TestSuiteProvider {

    /** 该插件提供的套件 id。 */
    List<String> suiteIds();

    /** 默认按 id 精确匹配；大小写归一由调用方负责。 */
    default boolean supports(String suiteId) {
        return suiteId != null && suiteIds().contains(suiteId);
    }
}
