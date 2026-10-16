package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.bukkit.FoliaAdapter;
import io.laplace.sprite.adapters.bukkit.FoliaAdapter.TaskKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Folia 四调度器路由（14.2）。 */
class FoliaRoutingTest {

    private final FoliaAdapter adapter = new FoliaAdapter();

    @Test
    void routesEachTaskKindToItsScheduler() {
        assertEquals("GlobalRegionScheduler", adapter.routeTask(TaskKind.GLOBAL).scheduler());
        assertEquals("RegionScheduler", adapter.routeTask(TaskKind.REGION).scheduler());
        assertEquals("EntityScheduler", adapter.routeTask(TaskKind.ENTITY).scheduler());
        assertEquals("AsyncScheduler", adapter.routeTask(TaskKind.IO).scheduler());
        assertEquals(4, adapter.schedulers().size());
    }

    @Test
    void crossRegionAccessIsQueuedInsteadOfDirect() {
        FoliaAdapter.RouteDecision crossRegion = adapter.routeTask(TaskKind.REGION, "region-0-0",
                "region-0-1");
        assertTrue(crossRegion.crossRegionQueued(), "跨区域必须排队");
        FoliaAdapter.RouteDecision sameRegion = adapter.routeTask(TaskKind.REGION, "region-0-0",
                "region-0-0");
        assertFalse(sameRegion.crossRegionQueued(), "同区域可直连");
    }

    @Test
    void entityTasksFollowTheSameRegionRule() {
        assertTrue(adapter.routeTask(TaskKind.ENTITY, "region-0-0", "region-1-4").crossRegionQueued());
        assertFalse(adapter.routeTask(TaskKind.ENTITY, "region-0-0", "region-0-0").crossRegionQueued());
    }

    @Test
    void globalAndAsyncTasksAreNotRegionBound() {
        assertFalse(adapter.routeTask(TaskKind.GLOBAL, "region-0-0", "region-0-1").crossRegionQueued());
        assertFalse(adapter.routeTask(TaskKind.IO, "region-0-0", "region-0-1").crossRegionQueued());
    }
}
