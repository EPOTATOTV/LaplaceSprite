package io.laplace.sprite.debug;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 条件断点模板库（15.1）：覆盖面、id 唯一、场景查询。 */
class BreakpointLibraryTest {

    @Test
    void libraryCoversHighFrequencyScenes() {
        BreakpointLibrary library = BreakpointLibrary.defaults();

        assertTrue(library.size() >= 8, "至少覆盖 8 个 MC 高频场景");
        assertEquals(library.size(),
                library.all().stream().map(BreakpointTemplate::id).distinct().count(), "模板 id 必须唯一");
        assertEquals(library.size(),
                library.all().stream().map(BreakpointTemplate::scene).distinct().count(), "场景不要重复");
        assertTrue(library.all().stream().allMatch(template -> !template.expression().isBlank()));
        assertTrue(library.all().stream().allMatch(template -> !template.notes().isBlank()), "映射差异要写进 notes");

        List<String> scenes = library.all().stream().map(BreakpointTemplate::scene).toList();
        assertTrue(scenes.containsAll(List.of("player-interact", "block-break", "entity-tick", "tick-loop",
                "chat", "damage", "chunk-load", "inventory")));
    }

    @Test
    void templatesCarryUsableExpressions() {
        BreakpointLibrary library = BreakpointLibrary.defaults();

        assertTrue(library.byId("item-in-hand").orElseThrow().expression().contains("getMainHandItem"));
        assertTrue(library.byId("block-tag").orElseThrow().expression().contains("BlockTags"));
        assertTrue(library.byId("entity-type").orElseThrow().expression().contains("EntityType.CREEPER"));
        assertTrue(library.byId("tick-number").orElseThrow().expression().contains("tick"));
        assertTrue(library.byId("chat-contains").orElseThrow().expression().contains("getString"));
        assertTrue(library.byId("damage-event").orElseThrow().expression().contains("amount"));
        assertTrue(library.byId("chunk-load").orElseThrow().expression().contains("pos"));
        assertTrue(library.byId("inventory-slot").orElseThrow().expression().contains("slot"));
        assertTrue(library.byId("no-such-template").isEmpty());
        assertEquals(1, library.byScene("chat").size());
    }

    @Test
    void invalidDefinitionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BreakpointLibrary(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new BreakpointLibrary(null));
        assertThrows(IllegalArgumentException.class, () -> new BreakpointTemplate(" ", "n", "s", "e", ""));
        assertThrows(IllegalArgumentException.class, () -> new BreakpointTemplate("id", " ", "s", "e", ""));
        assertThrows(IllegalArgumentException.class, () -> new BreakpointTemplate("id", "n", " ", "e", ""));
        assertThrows(IllegalArgumentException.class, () -> new BreakpointTemplate("id", "n", "s", " ", ""));
    }
}
