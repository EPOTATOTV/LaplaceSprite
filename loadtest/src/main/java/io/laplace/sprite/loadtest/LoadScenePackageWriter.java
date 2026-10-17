package io.laplace.sprite.loadtest;

import io.laplace.sprite.core.io.Json;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * 压测现场写入（15.9）。
 *
 * <p>与 15.5 崩溃诊断器共用存储根目录；这里只落一个 JSON，不额外打包，
 * 由上层导出问题现场包时统一收拢。</p>
 */
public final class LoadScenePackageWriter {

    public static final String PREFIX = "load-scene-";

    public Path write(Path directory, LoadScenePackage scenePackage) {
        Path target = directory.resolve(PREFIX + safeName(scenePackage.scenario().id()) + ".json");
        Json.writeFile(target, scenePackage);
        return target;
    }

    /** 门禁破线才落盘；通过时返回空，不产生无用现场文件。 */
    public Optional<Path> writeOnBreach(Path directory, LoadScenario scenario, LoadVerdict verdict,
                                        LoadTimeline timeline) {
        if (!verdict.broke()) {
            return Optional.empty();
        }
        return Optional.of(write(directory, LoadScenePackage.of(scenario, verdict, timeline)));
    }

    private static String safeName(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }
}
