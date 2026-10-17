package io.laplace.sprite.core.engine.replay;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.PlayerType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 事件流回放会话（4.6）：把一个 flag / 断言失败绑定到完整事件流，支持整段回放、区间回放、
 * 断点分段、单步与导出。
 *
 * <p>本类不依赖 report 模块；导出结果由调用方决定是否归档进 EvidenceVault。</p>
 */
public final class EventReplaySession {

    private static final int MARKDOWN_EVENT_LIMIT = 50;

    private final FlagEvent flag;
    private final long seed;
    private final List<ReplayEvent> snapshots;
    private final List<Breakpoint> breakpoints;
    private int cursor;

    private EventReplaySession(FlagEvent flag, long seed, List<ReplayEvent> events,
                               List<Breakpoint> breakpoints) {
        this.flag = Objects.requireNonNull(flag, "flag");
        this.seed = seed;
        // sorted 是稳定排序：同 tick 的事件保持事件流里的原始先后，回放因此可重复。
        this.snapshots = events == null ? List.of()
                : events.stream().sorted(Comparator.comparingLong(ReplayEvent::tick)).toList();
        this.breakpoints = mergeBreakpoints(breakpoints);
    }

    /** 绑定一个检测 flag。 */
    public static EventReplaySession bind(FlagEvent flag, EventStream stream, long seed) {
        Objects.requireNonNull(stream, "stream");
        List<ReplayEvent> snapshots = stream.events().stream().map(ReplayEvent::from).toList();
        return new EventReplaySession(flag, seed, snapshots, List.of());
    }

    /**
     * 绑定一次断言失败或异常。
     *
     * <p>失败 / 异常没有作弊类型，用最小 FlagEvent 承载定位信息；{@code falsePositive=false}
     * 表示它不是误报计数项，{@code PlayerType.LEGIT} 只是占位。</p>
     */
    public static EventReplaySession forAssertionFailure(String runId, String playerId, String checkName,
                                                         long tick, String trajectoryRef, long seed,
                                                         EventStream stream) {
        FlagEvent flag = new FlagEvent("assert-" + runId + "-" + tick + "-" + checkName, runId,
                playerId, PlayerType.LEGIT, checkName, null, null, 1.0, tick, trajectoryRef, null,
                false);
        return bind(flag, stream, seed);
    }

    /** 追加断点，返回新会话；同一 tick 的断点以后加入的为准。 */
    public EventReplaySession withBreakpoint(Breakpoint breakpoint) {
        Objects.requireNonNull(breakpoint, "breakpoint");
        List<Breakpoint> merged = new ArrayList<>(breakpoints);
        merged.add(breakpoint);
        return new EventReplaySession(flag, seed, snapshots, merged);
    }

    /** 按 tick 顺序重放全部事件。 */
    public List<Event> replay() {
        return snapshots.stream().map(ReplayEvent::toEvent).toList();
    }

    /** 闭区间回放：fromTick、toTick 两端都包含。 */
    public List<Event> replayRange(long fromTick, long toTick) {
        if (fromTick > toTick) {
            throw new IllegalArgumentException("回放区间上界小于下界: " + fromTick + ".." + toTick);
        }
        return eventsIn(fromTick, toTick);
    }

    /**
     * 按断点把事件流切成段。
     *
     * <p>断点 tick N 是新段的起点：上一段到 N-1 结束，下一段从 N 开始；落在无事件 tick 上的
     * 断点不会产生空段。</p>
     */
    public List<Segment> segments() {
        if (snapshots.isEmpty()) {
            return List.of();
        }
        long firstTick = snapshots.get(0).tick();
        long lastTick = snapshots.get(snapshots.size() - 1).tick();
        List<Segment> segments = new ArrayList<>();
        int index = 0;
        for (int i = 0; i <= breakpoints.size(); i++) {
            long start = i == 0 ? firstTick : breakpoints.get(i - 1).tick();
            long end = i == breakpoints.size() ? lastTick : breakpoints.get(i).tick() - 1;
            start = Math.max(start, firstTick);
            end = Math.min(end, lastTick);
            if (start > end) {
                continue;
            }
            List<Event> window = eventsIn(start, end);
            if (window.isEmpty()) {
                continue;
            }
            String label = i == 0 ? null : breakpoints.get(i - 1).label();
            segments.add(new Segment(index++, start, end, label, window));
        }
        return List.copyOf(segments);
    }

    /** 单步取下一条事件；越过末尾返回空，不抛异常。 */
    public synchronized Optional<Event> step() {
        if (cursor >= snapshots.size()) {
            return Optional.empty();
        }
        return Optional.of(snapshots.get(cursor++).toEvent());
    }

    /** 单步游标回到起点。 */
    public synchronized void reset() {
        cursor = 0;
    }

    public synchronized int cursor() {
        return cursor;
    }

    public int eventCount() {
        return snapshots.size();
    }

    public long seed() {
        return seed;
    }

    public FlagEvent flag() {
        return flag;
    }

    public List<Breakpoint> breakpoints() {
        return breakpoints;
    }

    /**
     * 导出整段事件流为自包含 JSON，并生成 Markdown 摘要。
     *
     * @param directory 输出目录，文件名取 flag id
     */
    public ReplayExport export(Path directory) {
        Path dir = directory.toAbsolutePath();
        String base = safeFileName(flag.id());
        Path jsonPath = dir.resolve(base + ".json");
        Path markdownPath = dir.resolve(base + ".md");
        String json = Json.write(document());
        String markdown = markdown();
        try {
            Files.createDirectories(dir);
            Files.writeString(jsonPath, json, StandardCharsets.UTF_8);
            Files.writeString(markdownPath, markdown, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new ReplayExport(jsonPath, markdownPath, json, markdown, snapshots.size());
    }

    /** 从导出 JSON 还原会话；断点不随导出保存，需要时重新添加。 */
    public static EventReplaySession read(Path jsonPath) {
        try {
            JsonNode root = Json.jsonMapper().readTree(Files.readString(jsonPath, StandardCharsets.UTF_8));
            // 导出格式只保留定位字段，不含 playerType；回读用于重放，按 LEGIT 占位。
            FlagEvent flag = new FlagEvent(text(root, "flagId"), text(root, "runId"),
                    text(root, "playerId"), PlayerType.LEGIT, text(root, "checkName"), null, null,
                    1.0, root.path("tick").asLong(), text(root, "trajectoryRef"), null, false);
            long seed = root.path("seed").asLong();
            JsonNode eventsNode = root.get("events");
            List<ReplayEvent> events = eventsNode == null ? List.of()
                    : Json.jsonMapper().convertValue(eventsNode,
                            new TypeReference<List<ReplayEvent>>() {
                            });
            return new EventReplaySession(flag, seed, events, List.of());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private List<Event> eventsIn(long fromTick, long toTick) {
        return snapshots.stream()
                .filter(snapshot -> snapshot.tick() >= fromTick && snapshot.tick() <= toTick)
                .map(ReplayEvent::toEvent)
                .toList();
    }

    private Map<String, Object> document() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("flagId", flag.id());
        document.put("runId", flag.runId());
        document.put("playerId", flag.playerId());
        document.put("checkName", flag.checkName());
        document.put("tick", flag.tick());
        document.put("seed", seed);
        document.put("trajectoryRef", flag.trajectoryRef());
        document.put("events", snapshots);
        return document;
    }

    private String markdown() {
        StringBuilder md = new StringBuilder();
        md.append("# 事件流回放 ").append(flag.id()).append("\n\n");
        md.append("| 字段 | 值 |\n| --- | --- |\n");
        md.append("| 运行 | ").append(value(flag.runId())).append(" |\n");
        md.append("| 玩家 | ").append(value(flag.playerId())).append(" |\n");
        md.append("| 检测项 | ").append(value(flag.checkName())).append(" |\n");
        md.append("| 命中 tick | ").append(flag.tick()).append(" |\n");
        md.append("| 种子 | ").append(seed).append(" |\n");
        md.append("| 轨迹 | ").append(value(flag.trajectoryRef())).append(" |\n");
        md.append("| 事件数 | ").append(snapshots.size()).append(" |\n\n");

        md.append("## 断点\n\n");
        if (breakpoints.isEmpty()) {
            md.append("未设置断点，整段连续回放。\n\n");
        } else {
            md.append("| tick | 标签 | 谓词 |\n| ---: | --- | --- |\n");
            for (Breakpoint breakpoint : breakpoints) {
                md.append("| ").append(breakpoint.tick())
                        .append(" | ").append(value(breakpoint.label()))
                        .append(" | ").append(value(breakpoint.predicate()))
                        .append(" |\n");
            }
            md.append('\n');
        }

        md.append("## 事件（前 ").append(MARKDOWN_EVENT_LIMIT).append(" 条）\n\n");
        md.append("| # | tick | 类型 | 玩家 |\n| ---: | ---: | --- | --- |\n");
        int limit = Math.min(MARKDOWN_EVENT_LIMIT, snapshots.size());
        for (int i = 0; i < limit; i++) {
            ReplayEvent snapshot = snapshots.get(i);
            md.append("| ").append(i + 1)
                    .append(" | ").append(snapshot.tick())
                    .append(" | ").append(snapshot.type())
                    .append(" | ").append(value(snapshot.playerId()))
                    .append(" |\n");
        }
        if (snapshots.size() > MARKDOWN_EVENT_LIMIT) {
            md.append("\n完整事件流见同名 JSON。\n");
        }
        return md.toString();
    }

    private static List<Breakpoint> mergeBreakpoints(List<Breakpoint> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        Map<Long, Breakpoint> byTick = new TreeMap<>();
        for (Breakpoint breakpoint : requested) {
            if (breakpoint != null) {
                byTick.put(breakpoint.tick(), breakpoint);
            }
        }
        return List.copyOf(byTick.values());
    }

    private static String safeFileName(String id) {
        String value = id == null || id.isBlank() ? "replay" : id;
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String value(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    /**
     * 断点切出的一段事件流。
     *
     * @param index           段序号（从 0 开始）
     * @param fromTick        段起点（闭）
     * @param toTick          段终点（闭）
     * @param breakpointLabel 开启本段的断点标签；首段为空
     * @param events          段内事件
     */
    public record Segment(int index, long fromTick, long toTick, String breakpointLabel,
                          List<Event> events) {

        public Segment {
            events = events == null ? List.of() : List.copyOf(events);
        }
    }
}
