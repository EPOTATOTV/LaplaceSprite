package io.laplace.sprite.adapters.ecosystem;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.io.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 矩阵健康度（14.9）：按格记录连续通过 / 失败次数，落 JSON 后重启仍可读回。
 *
 * <p>阈值是「达到即触发」：连续通过 3 次降级成 1/3 抽样，连续失败 2 次升回每次必跑。
 * 抽样只影响该格是否排进本轮，不影响失败记录的累计。</p>
 */
public final class MatrixHealthRegistry {

    /** 连续通过达到这个数就降级为抽样。 */
    public static final int DOWNGRADE_AFTER_PASSES = 3;
    /** 连续失败达到这个数就升回每次必跑。 */
    public static final int PROMOTE_AFTER_FAILURES = 2;
    /** 抽样比例的分母：每 3 轮跑 1 轮。 */
    public static final int SAMPLE_EVERY = 3;

    public enum Mode {
        EVERY_RUN,
        SAMPLE_ONE_THIRD
    }

    /**
     * 一格的健康度快照。
     *
     * @param cellId       格标识
     * @param consecutivePasses 连续通过次数
     * @param consecutiveFailures 连续失败次数
     * @param mode         当前调度模式
     * @param coordinates  该格携带的产物坐标
     */
    public record CellHealth(String cellId, int consecutivePasses, int consecutiveFailures,
                             Mode mode, List<String> coordinates) {

        public CellHealth {
            coordinates = coordinates == null ? List.of() : List.copyOf(coordinates);
        }
    }

    private static final class State {
        private int passes;
        private int failures;
        private Mode mode = Mode.EVERY_RUN;
        private final LinkedHashSet<String> coordinates = new LinkedHashSet<>();
    }

    private final Path file;
    private final Map<String, State> cells = new LinkedHashMap<>();

    public MatrixHealthRegistry() {
        this(null);
    }

    public MatrixHealthRegistry(Path file) {
        this.file = file;
        if (file != null && Files.isRegularFile(file)) {
            read(file);
        }
    }

    public synchronized void registerCell(String cellId, List<String> coordinates) {
        Objects.requireNonNull(cellId, "cellId");
        State state = cells.computeIfAbsent(cellId, key -> new State());
        if (coordinates != null) {
            state.coordinates.addAll(coordinates);
        }
        persist();
    }

    public synchronized void record(String cellId, boolean passed) {
        record(cellId, passed, List.of());
    }

    /**
     * 写回一次结果。
     *
     * <p>通过会清零失败计数，失败会清零通过计数；模式只在达到阈值时切换，
     * 因此「抽样中失败一次」不会立刻升回必跑。</p>
     */
    public synchronized void record(String cellId, boolean passed, List<String> coordinates) {
        Objects.requireNonNull(cellId, "cellId");
        State state = cells.computeIfAbsent(cellId, key -> new State());
        if (coordinates != null) {
            state.coordinates.addAll(coordinates);
        }
        if (passed) {
            state.passes++;
            state.failures = 0;
            if (state.passes >= DOWNGRADE_AFTER_PASSES) {
                state.mode = Mode.SAMPLE_ONE_THIRD;
            }
        } else {
            state.failures++;
            state.passes = 0;
            if (state.failures >= PROMOTE_AFTER_FAILURES) {
                state.mode = Mode.EVERY_RUN;
            }
        }
        persist();
    }

    public synchronized Mode mode(String cellId) {
        State state = cells.get(cellId);
        return state == null ? Mode.EVERY_RUN : state.mode;
    }

    /** 抽样格每 3 轮跑第 1 轮。 */
    public synchronized boolean shouldRun(String cellId, int sampleIndex) {
        if (mode(cellId) == Mode.EVERY_RUN) {
            return true;
        }
        return Math.floorMod(sampleIndex, SAMPLE_EVERY) == 0;
    }

    /**
     * 模组 / 插件升级只重跑携带该坐标的格，不触发全量。
     *
     * <p>命中多格说明升级的是跨格基线包，此时仍然只跑命中的格。</p>
     */
    public synchronized List<String> cellsAffectedByUpgrade(String coordinate) {
        List<String> affected = new ArrayList<>();
        for (Map.Entry<String, State> entry : cells.entrySet()) {
            if (entry.getValue().coordinates.contains(coordinate)) {
                affected.add(entry.getKey());
            }
        }
        return List.copyOf(affected);
    }

    public synchronized List<CellHealth> health() {
        return cells.entrySet().stream()
                .map(entry -> new CellHealth(entry.getKey(), entry.getValue().passes,
                        entry.getValue().failures, entry.getValue().mode,
                        List.copyOf(entry.getValue().coordinates)))
                .toList();
    }

    public Path file() {
        return file;
    }

    private void persist() {
        if (file == null) {
            return;
        }
        JsonNode root = toJson();
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Json.jsonMapper().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入矩阵健康度失败: " + file, ex);
        }
    }

    private JsonNode toJson() {
        var root = Json.jsonMapper().createObjectNode();
        var array = root.putArray("cells");
        for (Map.Entry<String, State> entry : cells.entrySet()) {
            State state = entry.getValue();
            var node = array.addObject();
            node.put("cellId", entry.getKey());
            node.put("passes", state.passes);
            node.put("failures", state.failures);
            node.put("mode", state.mode.name());
            var coordinates = node.putArray("coordinates");
            state.coordinates.forEach(coordinates::add);
        }
        return root;
    }

    private void read(Path source) {
        try {
            JsonNode root = Json.jsonMapper().readTree(Files.readString(source, StandardCharsets.UTF_8));
            for (JsonNode node : root.path("cells")) {
                State state = new State();
                state.passes = node.path("passes").asInt();
                state.failures = node.path("failures").asInt();
                state.mode = Mode.valueOf(node.path("mode").asText(Mode.EVERY_RUN.name()));
                node.path("coordinates").forEach(coordinate -> state.coordinates.add(coordinate.asText()));
                cells.put(node.path("cellId").asText(), state);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("读取矩阵健康度失败: " + source, ex);
        }
    }
}
