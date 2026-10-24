package io.laplace.sprite.core.model;

import java.util.List;

/**
 * GameTest 与数据包测试轨共用的报告契约（升级实施文档 v2 L2-05）。
 *
 * <p>两条轨的执行方式不同，但归档字段一致：轨道、目标版本、加载器、结论、用例规模与问题清单。
 * 报告由 CLI 以 JSON 落盘，下游看板不必区分 gametest / datapack 两套结构。</p>
 *
 * @param track     轨道：gametest 或 datapack
 * @param minecraft 目标 MC 版本或版本清单
 * @param loader    加载器；数据包轨为空
 * @param passed    是否整体通过
 * @param total     用例 / 版本列总数
 * @param failed    失败数
 * @param issues    问题明细
 */
public record TestTrackReport(String track, String minecraft, String loader, boolean passed,
                              int total, int failed, List<String> issues) {

    public TestTrackReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static TestTrackReport gameTest(String minecraft, String loader, boolean passed,
                                           int total, int failed, List<String> issues) {
        return new TestTrackReport("gametest", minecraft, loader, passed, total, failed, issues);
    }

    public static TestTrackReport datapack(String minecraft, boolean passed,
                                           int total, int failed, List<String> issues) {
        return new TestTrackReport("datapack", minecraft, null, passed, total, failed, issues);
    }
}
