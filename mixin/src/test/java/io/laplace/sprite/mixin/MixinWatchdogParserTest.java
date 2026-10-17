package io.laplace.sprite.mixin;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.io.Json;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Watchdog 归因（15.11 P1）：JSON → 归因表 → 类反查索引。 */
class MixinWatchdogParserTest {

    private static final String JSON = """
            {
              "mixins": [
                {
                  "modId": "questmod",
                  "targetClass": "net.minecraft.world.entity.Entity",
                  "mixins": ["com.example.quest.EntityMixin"]
                },
                {
                  "mod": "qolmod",
                  "target": "net.minecraft.client.Minecraft",
                  "mixins": "com.example.qol.MinecraftMixin"
                }
              ]
            }
            """;

    @Test
    void parsesAttributionTable() {
        List<MixinAttribution> attributions = MixinWatchdogParser.parse(JSON);

        assertEquals(2, attributions.size());
        assertEquals("questmod", attributions.get(0).modId());
        assertEquals("net.minecraft.world.entity.Entity", attributions.get(0).targetClass());
        assertEquals(List.of("com.example.quest.EntityMixin"), attributions.get(0).mixins());
        assertEquals(List.of("com.example.qol.MinecraftMixin"), attributions.get(1).mixins());
    }

    @Test
    void indexResolvesWovenClassAndMixinClassToMod() {
        MixinAttributionIndex index = MixinWatchdogParser.index(MixinWatchdogParser.parse(JSON));

        assertEquals("questmod", index.modFor("net.minecraft.world.entity.Entity").orElseThrow());
        assertEquals("questmod", index.modFor("com.example.quest.EntityMixin").orElseThrow());
        assertEquals("qolmod", index.modFor("net.minecraft.client.Minecraft").orElseThrow());
        assertTrue(index.modFor("net.minecraft.server.MinecraftServer").isEmpty());
    }

    @Test
    void classIndexJsonKeepsPrefixModShape() throws IOException {
        MixinAttributionIndex index = MixinWatchdogParser.index(MixinWatchdogParser.parse(JSON));

        JsonNode root = Json.jsonMapper().readTree(index.toClassIndexJson());

        assertEquals(4, root.path("entries").size());
        assertTrue(index.toClassIndexJson().contains("\"modId\" : \"questmod\"")
                        || index.toClassIndexJson().contains("\"modId\":\"questmod\""),
                index.toClassIndexJson());
    }

    @Test
    void emptyOrMissingEntriesYieldEmptyTable() {
        assertTrue(MixinWatchdogParser.parse("").isEmpty());
        assertTrue(MixinWatchdogParser.parse("{\"mixins\":[]}").isEmpty());
    }
}
