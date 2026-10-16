package io.laplace.sprite.cheats;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.CheatSpec;
import io.laplace.sprite.core.model.CheatType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 声明式样本库的加载与查询。 */
class CheatCatalogTest {

    private static final Path SAMPLE_DIR = Paths.get("src", "main", "resources", "cheats", "samples");

    @Test
    void defaultsCoverAllTypesWithUniqueIds() {
        List<CheatSpec> specs = new CheatCatalog().loadDefaults();
        assertTrue(specs.size() >= 30, "样本数不足: " + specs.size());
        Set<CheatType> types = specs.stream().map(CheatSpec::type).collect(Collectors.toSet());
        assertEquals(EnumSet.allOf(CheatType.class), types);
        assertEquals(specs.size(), specs.stream().map(CheatSpec::id).distinct().count());
        assertTrue(specs.stream().allMatch(spec -> "detection-engine".equals(spec.target())));
        assertTrue(specs.stream().allMatch(spec -> !spec.tiers().isEmpty()));
        assertTrue(specs.stream().allMatch(spec -> spec.source() != null && !spec.source().isBlank()));
    }

    @Test
    void everyBundledYamlParses() throws IOException {
        List<Path> files;
        try (Stream<Path> listing = Files.list(SAMPLE_DIR)) {
            files = listing.filter(path -> path.getFileName().toString().endsWith(".yaml")).toList();
        }
        assertFalse(files.isEmpty());
        for (Path file : files) {
            CheatSpec[] specs = Json.readYaml(Files.readString(file, StandardCharsets.UTF_8), CheatSpec[].class);
            assertTrue(specs.length > 0, file.getFileName().toString());
        }
    }

    @Test
    void directoryLoadMatchesBundledDefaults() {
        CheatCatalog catalog = new CheatCatalog();
        assertEquals(catalog.loadDefaults().size(), catalog.load(SAMPLE_DIR).size());
    }

    @Test
    void lookupByIdTypeAndSource() {
        CheatCatalog catalog = new CheatCatalog();
        assertTrue(catalog.byId("speed.wurst.default").isPresent());
        assertTrue(catalog.byId("missing.spec").isEmpty());
        assertFalse(catalog.byType(CheatType.XRAY).isEmpty());
        assertFalse(catalog.bySource("Wurst").isEmpty());
        assertTrue(catalog.bySource("Nope").isEmpty());
    }

    @Test
    void everySpecDeclaresABoundaryTier() {
        for (CheatSpec spec : new CheatCatalog().loadDefaults()) {
            assertTrue(spec.tiers().stream()
                    .anyMatch(tier -> tier.expected() != null && tier.expected().boundary()), spec.id());
        }
    }
}
