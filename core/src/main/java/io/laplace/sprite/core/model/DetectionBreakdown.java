package io.laplace.sprite.core.model;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 检出率三视图明细（4.2 输出：按检测项 / 按客户端 / 按档位）。
 *
 * <p>命中数按 (检测项 × 档位) 与 (作弊类型) 两个维度统计；分母是各档位 / 各类型的注入样本数，
 * 由 Runner 在会话执行时累计，避免前端二次取数（11.1 设计原则 2）。</p>
 */
public record DetectionBreakdown(
        Map<String, Map<CheatTierName, Integer>> hitsByCheckTier,
        Map<CheatTierName, Integer> samplesByTier,
        Map<CheatType, Integer> hitsByCheatType,
        Map<CheatType, Integer> samplesByCheatType) {

    public DetectionBreakdown {
        hitsByCheckTier = deepCopy(hitsByCheckTier);
        samplesByTier = samplesByTier == null || samplesByTier.isEmpty()
                ? new EnumMap<>(CheatTierName.class)
                : new EnumMap<>(samplesByTier);
        hitsByCheatType = hitsByCheatType == null || hitsByCheatType.isEmpty()
                ? new EnumMap<>(CheatType.class)
                : new EnumMap<>(hitsByCheatType);
        samplesByCheatType = samplesByCheatType == null || samplesByCheatType.isEmpty()
                ? new EnumMap<>(CheatType.class)
                : new EnumMap<>(samplesByCheatType);
    }

    public static DetectionBreakdown empty() {
        return new DetectionBreakdown(Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static Map<String, Map<CheatTierName, Integer>> deepCopy(Map<String, Map<CheatTierName, Integer>> src) {
        Map<String, Map<CheatTierName, Integer>> out = new LinkedHashMap<>();
        if (src != null) {
            src.forEach((k, v) -> out.put(k, new EnumMap<>(v)));
        }
        return out;
    }
}
