package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.gametest.GameTestBridge;
import io.laplace.sprite.adapters.gametest.GameTestLoader;
import io.laplace.sprite.core.model.PlatformFamily;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 四加载器 GameTest 入口覆盖（升级实施文档 L-02）。 */
class GameTestLoadersTest {

    @Test
    void allFourModLoadersHaveAnEntryPoint() {
        assertTrue(GameTestLoader.coversAllLoaders());
        assertEquals(4, GameTestLoader.values().length);
        for (PlatformFamily family : new PlatformFamily[]{
                PlatformFamily.FABRIC, PlatformFamily.QUILT, PlatformFamily.FORGE, PlatformFamily.NEOFORGE}) {
            GameTestLoader loader = GameTestLoader.of(family);
            assertEquals(family, loader.family());
            assertEquals("/test runall", loader.nativeCommand());
        }
    }

    @Test
    void bridgeUsesTheLoaderSuiteName() {
        GameTestBridge forge = GameTestBridge.forLoader(GameTestLoader.FORGE);
        GameTestBridge neoForge = GameTestBridge.forLoader(GameTestLoader.NEOFORGE);

        assertEquals("forge-gametest", forge.suite());
        assertEquals("neoforge-gametest", neoForge.suite());
    }

    @Test
    void twentySixVersionsReuseTheNativeGameTestEntry() {
        assertTrue(GameTestLoader.supportsMinecraft("26.3"));
        assertTrue(GameTestLoader.supportsMinecraft("26.4"));
        assertTrue(GameTestLoader.supportsMinecraft("1.21.5"));
        assertFalse(GameTestLoader.supportsMinecraft("1.21.4"));
        assertFalse(GameTestLoader.supportsMinecraft("1.22.0"), "未发布线不能默认支持");
        assertFalse(GameTestLoader.supportsMinecraft(null));
        assertEquals("/test runall", GameTestLoader.NATIVE_COMMAND);
    }

    @Test
    void nonLoaderPlatformsHaveNoGameTestEntry() {
        assertNull(GameTestLoader.of(PlatformFamily.PAPER));
        assertNull(GameTestLoader.of(PlatformFamily.VELOCITY));
    }
}
