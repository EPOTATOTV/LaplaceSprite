package io.laplace.sprite.report;

import io.laplace.sprite.core.model.LogEntry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构化日志流（11.3 / 11.6）：每条按 logfmt 单行落盘，内存保留用于 tail。
 */
public final class LogStreamWriter {

    private final Path file;
    private final List<LogEntry> entries = new ArrayList<>();

    public LogStreamWriter(Path file) {
        this.file = file;
    }

    public void append(LogEntry entry) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, entry.toLogfmt() + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        entries.add(entry);
    }

    /** 最近 n 条日志（按写入顺序）。 */
    public List<LogEntry> tail(int n) {
        if (n <= 0) {
            return List.of();
        }
        int from = Math.max(0, entries.size() - n);
        return List.copyOf(entries.subList(from, entries.size()));
    }
}
