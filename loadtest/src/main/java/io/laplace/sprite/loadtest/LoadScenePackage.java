package io.laplace.sprite.loadtest;

import io.laplace.sprite.core.io.Json;

import java.util.List;

/**
 * 门禁破线时的压测现场（15.9）。
 *
 * <p>记录剧本、完整时间轴与破线点，供复算和归档。现场包与 15.5 崩溃诊断器共用同一存储根目录，
 * 文件名以 {@code load-scene-} 前缀区分，导出问题现场时可直接并入同一目录。</p>
 *
 * @param scenario           剧本
 * @param breakingConcurrency 第一个破线并发；未破线为 null
 * @param violations         破线明细
 * @param samples            完整时间轴
 */
public record LoadScenePackage(LoadScenario scenario, Integer breakingConcurrency,
                               List<String> violations, List<LoadSample> samples) {

    public LoadScenePackage {
        violations = violations == null ? List.of() : List.copyOf(violations);
        samples = samples == null ? List.of() : List.copyOf(samples);
    }

    public static LoadScenePackage of(LoadScenario scenario, LoadVerdict verdict, LoadTimeline timeline) {
        return new LoadScenePackage(scenario, verdict.breakingConcurrency(), verdict.violations(),
                timeline.samples());
    }

    public String toJson() {
        return Json.write(this);
    }
}
