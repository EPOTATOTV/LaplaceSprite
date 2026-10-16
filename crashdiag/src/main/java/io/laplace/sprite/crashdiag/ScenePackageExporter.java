package io.laplace.sprite.crashdiag;

import io.laplace.sprite.crashdiag.CrashArtifacts.CollectedFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 问题现场包导出（15.5）：把现场与诊断打成一个 zip，出问题可以直接发出去求社区帮助。
 *
 * <p>包含 crash-reports、logs、hs_err、启动命令行 / JVM 参数 / mod 清单、诊断 JSON，
 * 以及一份列明内容的 MANIFEST.txt。</p>
 */
public final class ScenePackageExporter {

    public static final String MANIFEST_ENTRY = "MANIFEST.txt";
    public static final String DIAGNOSIS_ENTRY = "diagnosis.json";

    private static final String DIR_CRASH_REPORTS = "crash-reports/";
    private static final String DIR_LOGS = "logs/";
    private static final String DIR_HS_ERR = "hs_err/";
    private static final String DIR_ENV = "env/";

    /** 导出并返回 zip 路径。 */
    public Path export(Path targetZip, CrashArtifacts artifacts, CrashDiagnosis diagnosis) {
        Path target = targetZip.toAbsolutePath().normalize();
        List<String> manifest = new ArrayList<>();
        Set<String> written = new LinkedHashSet<>();
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
                for (CollectedFile file : artifacts.found()) {
                    String entryName = entryName(file);
                    if (!written.add(entryName)) {
                        continue;
                    }
                    write(zip, entryName, Files.readAllBytes(file.path()));
                    manifest.add("kind=%s entry=%s source=%s".formatted(file.kind(), entryName, file.path()));
                }
                write(zip, DIAGNOSIS_ENTRY,
                        new DiagnosisRenderer(diagnosis).toJson().getBytes(StandardCharsets.UTF_8));
                manifest.add("kind=diagnosis entry=%s source=(generated)".formatted(DIAGNOSIS_ENTRY));
                write(zip, MANIFEST_ENTRY, manifest(diagnosis, manifest).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("导出问题现场包失败: " + target, ex);
        }
        return target;
    }

    /** 采集记录在包内的落点：按类别分目录，便于解压后一眼看懂。 */
    private static String entryName(CollectedFile file) {
        String name = file.path().getFileName().toString();
        return switch (file.kind()) {
            case CrashArtifacts.KIND_CRASH_REPORTS -> DIR_CRASH_REPORTS + name;
            case CrashArtifacts.KIND_LATEST_LOG -> DIR_LOGS + name;
            case CrashArtifacts.KIND_HS_ERR -> DIR_HS_ERR + name;
            default -> DIR_ENV + name;
        };
    }

    private static String manifest(CrashDiagnosis diagnosis, List<String> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("问题现场包（Laplace Sprite 15.5）\n");
        sb.append("instanceId=").append(diagnosis.instanceId()).append('\n');
        sb.append("diagnosisId=").append(diagnosis.id()).append('\n');
        sb.append("category=").append(diagnosis.category().name()).append('\n');
        sb.append("signature=").append(diagnosis.signature()).append('\n');
        sb.append("classIndexCoverage=").append("%.4f".formatted(diagnosis.classIndexCoverage())).append('\n');
        sb.append("collectedAt=").append(diagnosis.collectedAt()).append('\n');
        sb.append("entries=").append(entries.size() + 1).append('\n');
        sb.append("---\n");
        entries.forEach(entry -> sb.append(entry).append('\n'));
        sb.append("kind=manifest entry=").append(MANIFEST_ENTRY).append(" source=(generated)\n");
        return sb.toString();
    }

    private static void write(ZipOutputStream zip, String entryName, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write(content);
        zip.closeEntry();
    }
}
