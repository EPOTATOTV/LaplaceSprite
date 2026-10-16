package io.laplace.sprite.crashdiag;

import io.laplace.sprite.crashdiag.CrashArtifacts.CollectedFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * 崩溃现场采集（15.5）：容器异常退出后在实例目录里收四类现场。
 *
 * <p>本类只认目录，不认容器运行时，所以完全离线可测。约定：
 * crash-reports/*.txt、logs/latest.log、hs_err_pid*.log（根目录或 logs/）、
 * 以及编排侧写入的 start-command.txt / jvm-args.txt / mod-manifest.txt。</p>
 */
public final class CrashCollector {

    public static final String DIR_CRASH_REPORTS = "crash-reports";
    public static final String DIR_LOGS = "logs";
    public static final String FILE_LATEST_LOG = "latest.log";
    public static final String HS_ERR_PREFIX = "hs_err_pid";
    public static final String FILE_START_COMMAND = "start-command.txt";
    public static final String FILE_JVM_ARGS = "jvm-args.txt";
    public static final String FILE_MOD_MANIFEST = "mod-manifest.txt";

    private static final String CRASH_REPORT_SUFFIX = ".txt";
    private static final String LOG_SUFFIX = ".log";

    /** 采集实例目录下的全部现场。 */
    public CrashArtifacts collect(Path instanceDir) {
        Path dir = instanceDir.toAbsolutePath().normalize();
        List<CollectedFile> files = new ArrayList<>();

        collectCrashReports(dir, files);
        collectLatestLog(dir, files);
        collectHsErr(dir, files);
        collectSingleFile(dir, FILE_START_COMMAND, CrashArtifacts.KIND_START_COMMAND, files);
        collectSingleFile(dir, FILE_JVM_ARGS, CrashArtifacts.KIND_JVM_ARGS, files);
        collectSingleFile(dir, FILE_MOD_MANIFEST, CrashArtifacts.KIND_MOD_MANIFEST, files);

        return new CrashArtifacts(dir, files);
    }

    /**
     * 把采集到的文本类现场拼成一段用于签名匹配与栈帧归因的文本。
     *
     * <p>顺序固定为 crash-reports → latest.log → hs_err，保证同一份现场每次拼接一致。</p>
     */
    public String combinedText(CrashArtifacts artifacts) {
        StringBuilder sb = new StringBuilder();
        appendKind(sb, artifacts, CrashArtifacts.KIND_CRASH_REPORTS);
        appendKind(sb, artifacts, CrashArtifacts.KIND_LATEST_LOG);
        appendKind(sb, artifacts, CrashArtifacts.KIND_HS_ERR);
        return sb.toString();
    }

    private static void appendKind(StringBuilder sb, CrashArtifacts artifacts, String kind) {
        for (CollectedFile file : artifacts.ofKind(kind)) {
            if (!file.found()) {
                continue;
            }
            sb.append(read(file.path())).append('\n');
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            // 读不到就当没采到，不让采集本身把诊断流程打断
            return "";
        }
    }

    private static void collectCrashReports(Path dir, List<CollectedFile> files) {
        Path reportsDir = dir.resolve(DIR_CRASH_REPORTS);
        List<Path> reports = list(reportsDir,
                path -> path.getFileName().toString().endsWith(CRASH_REPORT_SUFFIX));
        if (reports.isEmpty()) {
            files.add(new CollectedFile(CrashArtifacts.KIND_CRASH_REPORTS, reportsDir, false));
            return;
        }
        reports.forEach(path -> files.add(new CollectedFile(CrashArtifacts.KIND_CRASH_REPORTS, path, true)));
    }

    private static void collectLatestLog(Path dir, List<CollectedFile> files) {
        Path latest = dir.resolve(DIR_LOGS).resolve(FILE_LATEST_LOG);
        files.add(new CollectedFile(CrashArtifacts.KIND_LATEST_LOG, latest, Files.isRegularFile(latest)));
    }

    private static void collectHsErr(Path dir, List<CollectedFile> files) {
        List<Path> found = new ArrayList<>();
        found.addAll(list(dir, CrashCollector::isHsErr));
        found.addAll(list(dir.resolve(DIR_LOGS), CrashCollector::isHsErr));
        if (found.isEmpty()) {
            files.add(new CollectedFile(CrashArtifacts.KIND_HS_ERR, dir.resolve("hs_err_pid{PID}.log"), false));
            return;
        }
        found.forEach(path -> files.add(new CollectedFile(CrashArtifacts.KIND_HS_ERR, path, true)));
    }

    private static void collectSingleFile(Path dir, String fileName, String kind, List<CollectedFile> files) {
        Path path = dir.resolve(fileName);
        files.add(new CollectedFile(kind, path, Files.isRegularFile(path)));
    }

    private static boolean isHsErr(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(HS_ERR_PREFIX) && name.endsWith(LOG_SUFFIX);
    }

    private static List<Path> list(Path dir, Predicate<Path> filter) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile).filter(filter).sorted().toList();
        } catch (IOException ex) {
            return List.of();
        }
    }
}
