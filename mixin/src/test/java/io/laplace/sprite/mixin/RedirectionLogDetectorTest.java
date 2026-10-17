package io.laplace.sprite.mixin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 重定向日志（15.11 P2）：重定向记录即兼容性风险用例。 */
class RedirectionLogDetectorTest {

    @Test
    void redirectionRecordsBecomeCompatibilityRisks() {
        String log = "[MixinSquared/INFO] Redirecting mixin com.example.quest.EntityMixin for "
                + "questmod.mixins.json\n"
                + "[qolmod] Redirecting mixin com.example.qol.MinecraftMixin\n"
                + "[main/INFO] Redirecting player to lobby\n";

        List<CompatibilityRisk> risks = RedirectionLogDetector.detect(log);

        assertEquals(2, risks.size());
        assertEquals("questmod", risks.get(0).modId());
        assertEquals(1, risks.get(0).markers().size());
        assertTrue(risks.get(0).risk().contains("兼容性风险"), risks.get(0).risk());
        assertTrue(risks.get(1).markers().get(0).contains("Redirecting"));
    }

    @Test
    void ordinaryRedirectWithoutMixinContextIsIgnored() {
        assertTrue(RedirectionLogDetector.detect("[main/INFO] Redirecting player to lobby").isEmpty());
        assertTrue(RedirectionLogDetector.detect("").isEmpty());
    }
}
