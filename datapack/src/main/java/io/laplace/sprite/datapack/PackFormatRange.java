package io.laplace.sprite.datapack;

/**
 * pack.mcmeta 的 supported_formats 区间（1.20.2 起可选字段）。
 *
 * @param minInclusive 下界（含）
 * @param maxInclusive 上界（含）
 */
public record PackFormatRange(int minInclusive, int maxInclusive) {

    public PackFormatRange {
        if (minInclusive <= 0 || maxInclusive <= 0) {
            throw new IllegalArgumentException("pack_format 必须为正数: " + minInclusive + ".." + maxInclusive);
        }
        if (minInclusive > maxInclusive) {
            throw new IllegalArgumentException("supported_formats 下界不能大于上界: "
                    + minInclusive + ".." + maxInclusive);
        }
    }

    /** 单值声明，如 {@code "supported_formats": 61}。 */
    public static PackFormatRange single(int format) {
        return new PackFormatRange(format, format);
    }

    public boolean contains(int format) {
        return format >= minInclusive && format <= maxInclusive;
    }
}
