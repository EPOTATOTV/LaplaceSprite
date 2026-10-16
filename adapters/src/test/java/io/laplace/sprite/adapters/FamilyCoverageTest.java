package io.laplace.sprite.adapters;

import io.laplace.sprite.core.model.PlatformFamily;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 家族覆盖（§9 验收 7）：适配器体系至少覆盖 5 类家族端到端可跑。
 *
 * <p>家族分类不硬编码清单，直接扫 {@link AdapterRegistryFactory#allAdapters()} 的 family 推导，
 * 新增适配器漏登记家族时这里会掉。</p>
 */
class FamilyCoverageTest {

    private static final int MIN_FAMILIES = 5;

    private static final Set<String> EXPECTED = Set.of(
            "bukkit", "proxy", "bedrock", "modloader", "sponge", "hybrid");

    @Test
    void registryCoversRequiredFamilies() {
        Set<String> families = new LinkedHashSet<>();
        AdapterRegistryFactory.allAdapters().forEach(adapter -> families.add(category(adapter.family())));

        assertTrue(families.size() >= MIN_FAMILIES, "家族覆盖不足: " + families);
        assertEquals(EXPECTED, families, "家族分类与 14.1 分层表不一致: " + families);
    }

    /** 一个 family 只归一类，判定顺序与分层表一致：混合端 → Sponge → 代理 → 基岩 → 模组加载器 → Bukkit。 */
    private static String category(PlatformFamily family) {
        if (family.isHybrid()) {
            return "hybrid";
        }
        if (family.isSponge()) {
            return "sponge";
        }
        if (family.isProxy()) {
            return "proxy";
        }
        if (family.isBedrock()) {
            return "bedrock";
        }
        if (family.isModLoader()) {
            return "modloader";
        }
        if (family.isPluginPlatform()) {
            return "bukkit";
        }
        return "other";
    }
}
