package io.laplace.sprite.debug;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化日志录制（15.1 事件流回放一期）：把 {@code logs/latest.log} 按 server thread / render thread
 * 切分保存，给断点复现做对照。
 *
 * <p>一期只做录制 + 切分，没有回放引擎，也不在接口上假装有。</p>
 */
public final class LogRecorder {

    /** 形如 {@code [12:00:01] [Server thread/INFO]: 文本}。 */
    private static final Pattern LOG_LINE =
            Pattern.compile("^\\[\\d{2}:\\d{2}:\\d{2}\\] \\[([^/\\]]+)/([A-Z]+)]:?(.*)$");

    public static final String UNKNOWN_THREAD = "unknown";

    public static final String MERGED_THREAD = "all";

    private final Path outputDir;
    private final boolean splitByThread;

    public LogRecorder(Path outputDir, boolean splitByThread) {
        if (outputDir == null) {
            throw new IllegalArgumentException("输出目录不能为空");
        }
        this.outputDir = outputDir;
        this.splitByThread = splitByThread;
    }

    public Path outputDirectory() {
        return outputDir;
    }

    public boolean splitByThread() {
        return splitByThread;
    }

    /**
     * 读一份 latest.log 落成若干条录制产物。没有线程前缀的续行（栈帧等）归到上一条所属线程。
     */
    public List<RecordingArtifact> record(Path latestLog) {
        Map<String, List<String>> byThread = new LinkedHashMap<>();
        String current = UNKNOWN_THREAD;
        List<String> lines = readAll(latestLog);
        for (String line : lines) {
            Matcher matcher = LOG_LINE.matcher(line);
            if (matcher.matches()) {
                current = matcher.group(1).trim();
            }
            byThread.computeIfAbsent(current, key -> new ArrayList<>()).add(line);
        }
        return write(byThread);
    }

    private List<RecordingArtifact> write(Map<String, List<String>> byThread) {
        try {
            Files.createDirectories(outputDir);
        } catch (IOException ex) {
            throw new UncheckedIOException("创建录制输出目录失败: " + outputDir, ex);
        }
        if (!splitByThread) {
            List<String> merged = new ArrayList<>();
            byThread.values().forEach(merged::addAll);
            return List.of(writeArtifact(MERGED_THREAD, merged));
        }
        List<RecordingArtifact> artifacts = new ArrayList<>(byThread.size());
        byThread.forEach((thread, lines) -> artifacts.add(writeArtifact(thread, lines)));
        return List.copyOf(artifacts);
    }

    private RecordingArtifact writeArtifact(String thread, List<String> lines) {
        String id = "log-" + sanitize(thread);
        Path target = outputDir.resolve(id + ".log");
        try {
            Files.writeString(target, String.join(System.lineSeparator(), lines), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入录制产物失败: " + target, ex);
        }
        return new RecordingArtifact(id, thread, target, lines.size());
    }

    private static List<String> readAll(Path latestLog) {
        try {
            return Files.readAllLines(latestLog, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("读取 latest.log 失败: " + latestLog, ex);
        }
    }

    private static String sanitize(String threadName) {
        return threadName.replaceAll("[^\\p{L}\\p{N}._-]", "-");
    }
}
