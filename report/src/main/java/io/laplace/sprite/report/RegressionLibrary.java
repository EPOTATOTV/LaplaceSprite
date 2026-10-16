package io.laplace.sprite.report;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.RegressionCase;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 回归用例库（4.6 RegressionLibrary）：误报修复携带回归轨迹入库，持久化为 JSON，重启保留。
 */
public final class RegressionLibrary {

    private final Path file;
    private final List<RegressionCase> cases = new ArrayList<>();

    public RegressionLibrary(Path file) {
        this.file = file;
        cases.addAll(load());
    }

    /**
     * 入库一条回归用例。同一 {@code trajectoryRef} 视为重复：幂等返回既有用例。
     */
    public RegressionCase add(FlagEvent falsePositiveFlag, String trajectoryRef, String fixRef, String note) {
        Optional<RegressionCase> existing = cases.stream()
                .filter(c -> c.trajectoryRef().equals(trajectoryRef))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        RegressionCase regressionCase = new RegressionCase("reg-" + String.format("%05d", cases.size() + 1),
                falsePositiveFlag.id(), trajectoryRef, fixRef, note, Instant.now());
        cases.add(regressionCase);
        persist();
        return regressionCase;
    }

    public List<RegressionCase> all() {
        return List.copyOf(cases);
    }

    public boolean hasTrajectory(String trajectoryRef) {
        return cases.stream().anyMatch(c -> c.trajectoryRef().equals(trajectoryRef));
    }

    public int size() {
        return cases.size();
    }

    private List<RegressionCase> load() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return List.of();
            }
            return List.of(Json.read(content, RegressionCase[].class));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void persist() {
        Json.writeFile(file, cases);
    }
}
