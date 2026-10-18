package io.laplace.sprite.datapack;

import io.laplace.sprite.core.spi.VersionOrder;

import java.util.List;
import java.util.OptionalInt;

/**
 * 原版数据包 pack_format 与 Minecraft 版本的对照表（15.8）。
 *
 * <p>数据来自 Minecraft Wiki 的「Data pack version」历史表：每个条目记该版本起生效的 pack_format，
 * 快照号跳过不记，因此 1.19.3 → 10、1.19.4 → 12 这类断号是原版本身的取值。表覆盖 1.13 到
 * 26.2；26.x 的数值来自升级实施文档给出的 86.2 口径（26.2 为 86，26.1 为上一档 85），
 * 更远的版本没有核实过的数值，宁可判为未知也不猜。</p>
 */
public final class PackFormatTable {

    /**
     * 一个对照条目。
     *
     * @param sinceVersion 该 pack_format 开始生效的版本
     * @param packFormat   数据包格式号
     */
    public record Entry(String sinceVersion, int packFormat) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("1.13", 4),
            new Entry("1.15", 5),
            new Entry("1.16.2", 6),
            new Entry("1.17", 7),
            new Entry("1.18", 8),
            new Entry("1.19", 9),
            new Entry("1.19.3", 10),
            new Entry("1.19.4", 12),
            new Entry("1.20", 15),
            new Entry("1.20.2", 18),
            new Entry("1.20.3", 26),
            new Entry("1.20.5", 41),
            new Entry("1.21", 48),
            new Entry("1.21.2", 57),
            new Entry("1.21.4", 61),
            new Entry("1.21.5", 71),
            new Entry("1.21.6", 80),
            new Entry("1.21.7", 81),
            new Entry("26.1", 85),
            new Entry("26.2", 86));

    /** 表内最高版本：更新版本的数值没有核实过，超过它就返回未知。 */
    private static final String MAX_VERSION = "26.2";

    public List<Entry> entries() {
        return ENTRIES;
    }

    /**
     * 取某版本的数据包格式号。低于 1.13、落在未发布线（如 1.22+ / 2.x）或高于表内最高版本时返回空，
     * 表示无法判定。
     */
    public OptionalInt formatFor(String mcVersion) {
        if (mcVersion == null || mcVersion.isBlank() || !inCoveredLines(mcVersion)) {
            return OptionalInt.empty();
        }
        Entry matched = null;
        for (Entry entry : ENTRIES) {
            if (VersionOrder.compare(entry.sinceVersion(), mcVersion) <= 0) {
                matched = entry;
            } else {
                break;
            }
        }
        return matched == null ? OptionalInt.empty() : OptionalInt.of(matched.packFormat());
    }

    /**
     * 只认 1.13–1.21.8 与 26.1–26.2 两条已发布线。1.22+ / 2.x 从未发布，
     * 不能因为「比表尾大」就被当作已覆盖。
     */
    private static boolean inCoveredLines(String version) {
        if (version.startsWith("1.")) {
            return VersionOrder.compare(version, "1.21.8") <= 0 && VersionOrder.compare(version, "1.13") >= 0;
        }
        return version.startsWith("26.") && VersionOrder.compare(version, MAX_VERSION) <= 0;
    }

    /**
     * 目标版本能否加载该 pack.mcmeta。
     *
     * <p>版本不在表内直接判不兼容：没有可信的期望值时不给出「通过」。</p>
     */
    public boolean compatible(String mcVersion, PackMeta meta) {
        if (meta == null) {
            return false;
        }
        OptionalInt expected = formatFor(mcVersion);
        return expected.isPresent() && meta.supports(expected.getAsInt());
    }
}
