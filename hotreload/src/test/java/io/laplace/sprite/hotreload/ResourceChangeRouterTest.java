package io.laplace.sprite.hotreload;

import io.laplace.sprite.hotreload.ResourceChangeRouter.RouteDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 资源变更路由表（15.2）：assets / data / mods 三类各走各的，别混机制。 */
class ResourceChangeRouterTest {

    private final ResourceChangeRouter router = new ResourceChangeRouter();

    @Test
    void assetsChangeTriggersF3tEquivalent() {
        RouteDecision decision = router.route("src/main/resources/assets/example/textures/block/dirt.png");

        assertEquals(ResourceChangeRouter.ACTION_ASSETS_RELOAD, decision.action());
        assertEquals(HotReloadTrack.DEV_HOT_LOOP, decision.track());
        assertTrue(decision.reason().contains("F3+T"));
        assertFalse(decision.track() == HotReloadTrack.COLD_LIFECYCLE);
    }

    @Test
    void dataChangeUsesVanillaDataPackReload() {
        RouteDecision decision = router.route("src\\main\\resources\\data\\example\\recipe\\craft.json");

        assertEquals(ResourceChangeRouter.ACTION_DATAPACK_RELOAD, decision.action());
        assertEquals(HotReloadTrack.DEV_HOT_LOOP, decision.track());
        assertTrue(decision.reason().contains("minecraft:reload"));
        assertTrue(decision.reason().contains("不是 Bukkit 插件 reload"), "必须写清两种 reload 的区别");
        assertTrue(new ReloadPolicy().rejectionReason("minecraft:reload").isEmpty());
    }

    @Test
    void modsAndPluginsForceColdRestart() {
        RouteDecision mods = router.route("server/mods/example-mod.jar");
        assertEquals(ResourceChangeRouter.ACTION_FORCED_RESTART, mods.action());
        assertEquals(HotReloadTrack.COLD_LIFECYCLE, mods.track());

        RouteDecision plugins = router.route("server/plugins/ExamplePlugin-1.0.0.jar");
        assertEquals(ResourceChangeRouter.ACTION_FORCED_RESTART, plugins.action());
        assertEquals(HotReloadTrack.COLD_LIFECYCLE, plugins.track());
        assertTrue(plugins.reason().contains("冷重启"));
    }

    @Test
    void unrecognizedPathFallsBackAndBlankPathIsRejected() {
        RouteDecision decision = router.route("docs/DEVELOPMENT.md");

        assertEquals(ResourceChangeRouter.ACTION_NONE, decision.action());
        assertEquals(HotReloadTrack.DEV_HOT_LOOP, decision.track());
        assertThrows(IllegalArgumentException.class, () -> router.route("  "));
        assertThrows(IllegalArgumentException.class, () -> router.route(null));
    }
}
