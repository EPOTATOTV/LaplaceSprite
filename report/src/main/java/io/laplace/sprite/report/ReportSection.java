package io.laplace.sprite.report;

/**
 * 报告附加节（十五、各功能轨：「作为测试报告一节」）。
 *
 * <p>功能轨各自实现本接口，报告层不反向依赖它们——依赖方向只有「功能轨 → report」。</p>
 */
public interface ReportSection {

    /** 节 id，同时用作 {@code sections/} 下的文件名。 */
    String id();

    String title();

    String toMarkdown();

    String toJson();
}
