package io.laplace.sprite.hotreload;

import io.laplace.sprite.hotreload.ColdStartLifecycle.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 冷启动轨与 reload 策略（15.2）：Bukkit /reload 被拒，数据包 reload 不在禁令内。 */
class ColdStartLifecycleTest {

    private final ColdStartLifecycle lifecycle = new ColdStartLifecycle();

    private final ReloadPolicy policy = new ReloadPolicy();

    @Test
    void coldTrackRejectsBukkitReloadWith1217Reason() {
        Validation validation = lifecycle.validate(List.of("clean-container", "/reload", "smoke"));

        assertFalse(validation.accepted());
        assertEquals(List.of("/reload"), validation.rejectedSteps());
        assertTrue(validation.reason().contains("1.21.7"), "拒绝理由必须点名 1.21.7，实际：" + validation.reason());
        assertTrue(validation.reason().contains("完整重启"));
    }

    @Test
    void everyBukkitReloadVariantIsRejected() {
        for (String step : List.of("/reload", "reload", "/reload confirm", "plugin-reload", "bukkit-reload")) {
            assertTrue(policy.isBukkitReload(step), step + " 应被识别为 Bukkit reload");
            assertTrue(policy.rejectionReason(step).isPresent());
        }
        assertTrue(policy.rejectionReason("clean-container").isEmpty());
        assertTrue(policy.rejectionReason(null).isEmpty());
        assertTrue(policy.rejectionReason("  ").isEmpty());
        assertFalse(policy.reason().isBlank());
    }

    @Test
    void vanillaDataPackReloadIsNotTheBannedOne() {
        assertTrue(policy.isVanillaDataPackReload("minecraft:reload"));
        assertTrue(policy.isVanillaDataPackReload("/datapack-reload"));
        assertFalse(policy.isBukkitReload("minecraft:reload"));
        assertTrue(policy.rejectionReason("minecraft:reload").isEmpty(),
                "原版数据包 reload 与 Bukkit 插件 reload 不是一套机制");
        assertTrue(lifecycle.validate(List.of("clean-container", "cold-start", "smoke")).accepted());
    }

    @Test
    void canonicalSequenceIsAccepted() {
        Validation validation = lifecycle.validate(ColdStartLifecycle.SEQUENCE);

        assertTrue(validation.accepted());
        assertTrue(validation.rejectedSteps().isEmpty());
        assertEquals("", validation.reason());
        assertEquals(HotReloadTrack.COLD_LIFECYCLE, lifecycle.track());
        assertEquals(List.of("clean-container", "cold-start", "smoke"), lifecycle.plan());
    }

    @Test
    void wrongOrderAndEmptyPlanAreRejected() {
        assertFalse(lifecycle.validate(List.of("cold-start", "smoke")).accepted());
        assertFalse(lifecycle.validate(List.of("smoke", "clean-container", "cold-start")).accepted());
        assertTrue(lifecycle.validate(List.of()).reason().contains("不能为空"));
        assertTrue(lifecycle.validate(null).reason().contains("不能为空"));
        assertTrue(new ColdStartLifecycle(null).validate(ColdStartLifecycle.SEQUENCE).accepted());
    }
}
