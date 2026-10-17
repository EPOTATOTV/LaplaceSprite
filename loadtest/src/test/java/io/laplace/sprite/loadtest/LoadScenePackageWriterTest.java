package io.laplace.sprite.loadtest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadScenePackageWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesScenarioTimelineAndBreakingPoint() throws IOException {
        LoadScenario scenario = new LoadScenario("breach/case", BotBackend.MINEFLAYER,
                List.of(BotAction.JOIN, BotAction.WALK), 100, RampCurve.single(10), "");
        LoadTimeline timeline = new LoadTimeline(20);
        for (int tick = 0; tick < 100; tick++) {
            timeline.add(new LoadSample(tick, 10, 19.0, 10.0, 55.0, 60.0, 10, 400, 20));
        }
        LoadVerdict verdict = new LoadGate().evaluate(scenario, timeline);
        LoadScenePackage scenePackage = LoadScenePackage.of(scenario, verdict, timeline);

        LoadScenePackageWriter writer = new LoadScenePackageWriter();
        Path written = writer.write(tempDir, scenePackage);

        assertTrue(Files.isRegularFile(written));
        assertTrue(written.getFileName().toString().startsWith(LoadScenePackageWriter.PREFIX));
        assertTrue(written.getFileName().toString().endsWith(".json"));
        String json = Files.readString(written);
        assertTrue(json.contains("\"scenario\""));
        assertTrue(json.contains("\"breakingConcurrency\" : 10"));
        assertTrue(json.contains("\"violations\""));
        assertTrue(json.contains("\"samples\""));
        assertTrue(json.contains("\"msptP95\""));
        assertTrue(writer.writeOnBreach(tempDir, scenario, verdict, timeline).isPresent());

        LoadVerdict passed = new LoadGate().evaluate(scenario, passingTimeline());
        assertTrue(writer.writeOnBreach(tempDir, scenario, passed, passingTimeline()).isEmpty());
    }

    private static LoadTimeline passingTimeline() {
        LoadTimeline timeline = new LoadTimeline(20);
        for (int tick = 0; tick < 100; tick++) {
            timeline.add(new LoadSample(tick, 10, 20.0, 10.0, 12.0, 15.0, 10, 400, 20));
        }
        return timeline;
    }
}
