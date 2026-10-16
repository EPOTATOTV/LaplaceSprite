package io.laplace.sprite.crashdiag;

import java.nio.file.Path;
import java.util.List;

/**
 * 崩溃现场采集结果（15.5）：每一条都是「采集类别 + 落点 + 是否存在」。
 *
 * <p>没采到的东西也要留一条 {@code found=false} 的记录，报告里能看出「本来该有什么、缺了什么」。</p>
 *
 * @param root  被采集的实例目录
 * @param files 采集条目
 */
public record CrashArtifacts(Path root, List<CollectedFile> files) {

    public static final String KIND_CRASH_REPORTS = "crash-reports";
    public static final String KIND_LATEST_LOG = "latest.log";
    public static final String KIND_HS_ERR = "hs_err";
    public static final String KIND_START_COMMAND = "start-command";
    public static final String KIND_JVM_ARGS = "jvm-args";
    public static final String KIND_MOD_MANIFEST = "mod-manifest";

    public CrashArtifacts {
        files = files == null ? List.of() : List.copyOf(files);
    }

    /** 已采集到的条目。 */
    public List<CollectedFile> found() {
        return files.stream().filter(CollectedFile::found).toList();
    }

    public List<CollectedFile> ofKind(String kind) {
        return files.stream().filter(file -> file.kind().equals(kind)).toList();
    }

    public boolean hasKind(String kind) {
        return files.stream().anyMatch(file -> file.found() && file.kind().equals(kind));
    }

    /** 是否存在 hs_err_pid 文件：原生崩溃分支的首要判据。 */
    public boolean hsErrPresent() {
        return hasKind(KIND_HS_ERR);
    }

    /**
     * 一条采集记录。
     *
     * @param kind  采集类别
     * @param path  期望或实际的落点
     * @param found 是否真的采到
     */
    public record CollectedFile(String kind, Path path, boolean found) {
    }
}
