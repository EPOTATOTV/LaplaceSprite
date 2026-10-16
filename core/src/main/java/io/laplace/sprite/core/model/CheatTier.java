package io.laplace.sprite.core.model;

import java.util.Map;

/**
 * 配置档位：模块参数 + 预期检出。
 *
 * @param tier   档位名
 * @param params 模块参数（键值对，如 {@code speed=1.35}）
 * @param expected 预期检出
 */
public record CheatTier(CheatTierName tier, Map<String, Object> params, ExpectedDetection expected) {

    public CheatTier {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public double doubleParam(String key, double fallback) {
        Object v = params.get(key);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    public int intParam(String key, int fallback) {
        Object v = params.get(key);
        return v instanceof Number n ? n.intValue() : fallback;
    }

    public boolean boolParam(String key, boolean fallback) {
        Object v = params.get(key);
        return v instanceof Boolean b ? b : fallback;
    }
}
