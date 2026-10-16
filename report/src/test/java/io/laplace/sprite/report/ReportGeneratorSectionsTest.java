package io.laplace.sprite.report;

import com.fasterxml.jackson.databind.JsonNode;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.DetectionBreakdown;
import io.laplace.sprite.core.model.MetricSet;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.RunStatus;
import io.laplace.sprite.core.model.TestRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 报告附加节（十五）：三参 write 把功能轨结果并入报告，二参形态保持不变。 */
class ReportGeneratorSectionsTest {

    private final ReportGenerator generator = new ReportGenerator();

    @Test
    void sectionsAreMergedIntoReportAndWrittenToSectionDirectory(@TempDir Path dir) throws IOException {
        ReportSection section = new ReportSection() {
            @Override
            public String id() {
                return "crash diag";
            }

            @Override
            public String title() {
                return "崩溃诊断";
            }

            @Override
            public String toMarkdown() {
                return "- Mixin apply failed";
            }

            @Override
            public String toJson() {
                return "{\"category\":\"mixin\"}";
            }
        };

        Path out = generator.write(dir, runs(), List.of(section));

        JsonNode root = Json.jsonMapper().readTree(Files.readString(out.resolve("report.json"),
                StandardCharsets.UTF_8));
        assertEquals(1, root.get("runs").size(), "运行明细应保留在 runs 里");
        assertEquals("crash diag", root.get("sections").get(0).get("id").asText());
        assertEquals("mixin", root.get("sections").get(0).get("json").get("category").asText());

        assertTrue(Files.readString(out.resolve("report.md"), StandardCharsets.UTF_8).contains("## 崩溃诊断"));
        assertTrue(Files.readString(out.resolve("report.html"), StandardCharsets.UTF_8).contains("崩溃诊断"));
        assertTrue(Files.exists(out.resolve("sections").resolve("crash_diag.md")), "节 id 应被安全化为文件名");
        assertTrue(Files.exists(out.resolve("sections").resolve("crash_diag.json")));
    }

    @Test
    void twoArgFormStaysPlainArrayForGate(@TempDir Path dir) throws IOException {
        Path out = generator.write(dir, runs());

        String json = Files.readString(out.resolve("report.json"), StandardCharsets.UTF_8);
        assertTrue(json.stripLeading().startsWith("["), "二参写法必须保持顶层数组，laplace gate 才读得动");
        assertFalse(Files.exists(out.resolve("sections")), "没有附加节时不应建 sections 目录");
    }

    private static List<TestRun> runs() {
        MetricSet metrics = new MetricSet(10, 0, 5, 5, 0, 0, true, 0.8);
        TestRun run = new TestRun("run-1", "detection-engine", "cheat",
                PlatformKey.of(PlatformFamily.PAPER, "1.21.1"), metrics, DetectionBreakdown.empty(),
                List.of(), List.of(), RunStatus.PASSED, 1L, Instant.now(), 10L);
        return List.of(run);
    }
}
