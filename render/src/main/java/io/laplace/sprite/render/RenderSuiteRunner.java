package io.laplace.sprite.render;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 渲染套件执行器（升级实施文档 L-06）：按 golden 目录批量比对实拍截图。
 *
 * <p>目录布局沿用 {@link GoldenPathResolver}：{@code <root>/<mc>/<backend>/<case>.png}。
 * golden 或 actual 缺失记为 MISSING，不拿空白图凑数；尺寸不一致由 {@link PixelDiff} 直接判失败。
 * 容差由 {@link RenderMatrix#toleranceFor(String, RenderBackend)} 按版本与管线给出；26.4 起
 * VulkanMod 也进 diff，之前仍只跑冒烟。</p>
 */
public final class RenderSuiteRunner {

    /** 单个用例的结论。 */
    public enum Status {
        PASSED,
        FAILED,
        MISSING
    }

    /**
     * 一条用例结果。
     *
     * @param caseId      用例 id
     * @param mcVersion   目标版本
     * @param backend     渲染后端
     * @param status      结论
     * @param diffPercent 差异像素百分比；缺失时为 0
     * @param reason      说明
     */
    public record CaseResult(String caseId, String mcVersion, RenderBackend backend, Status status,
                             double diffPercent, String reason) {
    }

    /**
     * 一次套件结果。
     *
     * @param cases 用例结果，按版本 / 后端 / 用例顺序
     */
    public record Report(List<CaseResult> cases) {

        public Report {
            cases = cases == null ? List.of() : List.copyOf(cases);
        }

        public boolean passed() {
            return !cases.isEmpty() && cases.stream().allMatch(item -> item.status() == Status.PASSED);
        }

        public long count(Status status) {
            return cases.stream().filter(item -> item.status() == status).count();
        }

        public String toMarkdown() {
            StringBuilder md = new StringBuilder();
            md.append("| 版本 | 后端 | 用例 | 结果 | 差异 | 说明 |\n");
            md.append("| --- | --- | --- | --- | ---: | --- |\n");
            for (CaseResult testCase : cases) {
                md.append("| ").append(testCase.mcVersion())
                        .append(" | ").append(testCase.backend().id())
                        .append(" | ").append(testCase.caseId())
                        .append(" | ").append(testCase.status())
                        .append(" | ").append(String.format(java.util.Locale.ROOT, "%.3f%%",
                                testCase.diffPercent()))
                        .append(" | ").append(testCase.reason().replace("|", "\\|"))
                        .append(" |\n");
            }
            return md.toString();
        }
    }

    private final RenderGoldenLibrary library;

    public RenderSuiteRunner() {
        this(new RenderGoldenLibrary());
    }

    public RenderSuiteRunner(RenderGoldenLibrary library) {
        this.library = Objects.requireNonNull(library, "library");
    }

    public Report run(Path goldenRoot, Path actualRoot) {
        return run(goldenRoot, actualRoot, library.cases());
    }

    public Report run(Path goldenRoot, Path actualRoot, List<RenderGoldenLibrary.GoldenCase> goldenCases) {
        Objects.requireNonNull(goldenRoot, "goldenRoot");
        Objects.requireNonNull(actualRoot, "actualRoot");
        GoldenPathResolver goldenResolver = new GoldenPathResolver(goldenRoot);
        GoldenPathResolver actualResolver = new GoldenPathResolver(actualRoot);
        List<CaseResult> results = new ArrayList<>();

        for (RenderGoldenLibrary.GoldenCase goldenCase : goldenCases) {
            for (RenderBackend backend : RenderBackend.values()) {
                if (!backend.diffAllowed(goldenCase.mcVersion())) {
                    continue;
                }
                RenderMatrixCell cell = new RenderMatrixCell(goldenCase.mcVersion(), backend, null, true,
                        RenderMatrix.toleranceFor(goldenCase.mcVersion(), backend));
                Path golden = goldenResolver.goldenPng(cell, goldenCase.id());
                Path actual = actualResolver.goldenPng(cell, goldenCase.id());
                results.add(compare(goldenCase, backend, golden, actual, cell.tolerancePercent()));
            }
        }
        return new Report(results);
    }

    private static CaseResult compare(RenderGoldenLibrary.GoldenCase goldenCase, RenderBackend backend,
                                      Path golden, Path actual, double tolerance) {
        if (!Files.isRegularFile(golden)) {
            return new CaseResult(goldenCase.id(), goldenCase.mcVersion(), backend, Status.MISSING, 0,
                    "缺少 golden: " + golden);
        }
        if (!Files.isRegularFile(actual)) {
            return new CaseResult(goldenCase.id(), goldenCase.mcVersion(), backend, Status.MISSING, 0,
                    "缺少实拍截图: " + actual);
        }
        try {
            PixelDiffResult result = PixelDiff.compare(Screenshot.fromPath(golden), Screenshot.fromPath(actual),
                    new PixelDiffOptions(tolerance, false, 0));
            return new CaseResult(goldenCase.id(), goldenCase.mcVersion(), backend,
                    result.passed() ? Status.PASSED : Status.FAILED, result.diffPercent(), result.reason());
        } catch (IllegalArgumentException ex) {
            return new CaseResult(goldenCase.id(), goldenCase.mcVersion(), backend, Status.FAILED, 0,
                    "截图不可读: " + ex.getMessage());
        }
    }
}
