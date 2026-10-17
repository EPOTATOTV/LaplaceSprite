package io.laplace.sprite.mixin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MixinSquared 重定向日志检测（15.11 P2）。
 *
 * <p>Forge/NeoForge 启动日志出现重定向记录，说明 MixinSquared 实际改动了加载顺序；
 * 同一 mod 的多条记录合并成一个风险用例，原始行全部留在 {@code markers} 里。</p>
 */
public final class RedirectionLogDetector {

    private RedirectionLogDetector() {
    }

    /** 扫描启动日志，按 mod 聚合重定向记录。 */
    public static List<CompatibilityRisk> detect(String launchLog) {
        if (launchLog == null || launchLog.isBlank()) {
            return List.of();
        }
        Map<String, List<String>> markersByMod = new LinkedHashMap<>();
        for (String rawLine : launchLog.split("\\R")) {
            if (!isRedirectionRecord(rawLine)) {
                continue;
            }
            markersByMod.computeIfAbsent(LaunchLogScanner.modIdOf(rawLine), key -> new ArrayList<>())
                    .add(rawLine.trim());
        }
        List<CompatibilityRisk> risks = new ArrayList<>(markersByMod.size());
        markersByMod.forEach((modId, markers) ->
                risks.add(new CompatibilityRisk(modId, markers, CompatibilityRisk.COMPATIBILITY_RISK)));
        return List.copyOf(risks);
    }

    /** 同时出现重定向词与 mixin 上下文才算记录，避免普通日志里的 redirect 误报。 */
    private static boolean isRedirectionRecord(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        boolean redirection = lower.contains("redirect");
        boolean mixinContext = lower.contains("mixin") || lower.contains("mixinsquared");
        return redirection && mixinContext;
    }
}
