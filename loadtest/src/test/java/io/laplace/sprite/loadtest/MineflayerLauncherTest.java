package io.laplace.sprite.loadtest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineflayerLauncherTest {

    @TempDir
    Path tempDir;

    @Test
    void rendersInvocationWithHostPortCountAndActions() throws IOException {
        Path script = tempDir.resolve("bots.js");
        Files.writeString(script, "// stub");
        InMemoryBotRunner runner = new InMemoryBotRunner();
        MineflayerLauncher launcher = new MineflayerLauncher(script, "node", "127.0.0.1", 25565,
                runner, executable -> true);
        LoadScenario scenario = scenario();

        List<String> command = launcher.command(scenario);

        assertEquals("node", command.get(0));
        assertEquals(script.toString(), command.get(1));
        assertEquals("127.0.0.1", valueAfter(command, "--host"));
        assertEquals("25565", valueAfter(command, "--port"));
        assertEquals("30", valueAfter(command, "--count"));
        assertEquals("join,walk,mine", valueAfter(command, "--actions"));
        assertEquals("1200", valueAfter(command, "--duration-ticks"));
        assertTrue(launcher.available());
    }

    @Test
    void startsInMemorySessionsWithoutClaimingLiveConnections() throws IOException {
        Path script = tempDir.resolve("bots.js");
        Files.writeString(script, "// stub");
        InMemoryBotRunner runner = new InMemoryBotRunner();
        MineflayerLauncher launcher = new MineflayerLauncher(script, "node", "127.0.0.1", 25565,
                runner, executable -> true);

        List<BotSession> sessions = launcher.start(scenario());

        assertEquals(30, sessions.size());
        assertFalse(sessions.get(0).live());
        assertEquals(1, runner.started().size());
        assertTrue(launcher.note().contains("Node"));
    }

    @Test
    void degradesWhenNodeIsMissing() throws IOException {
        Path script = tempDir.resolve("bots.js");
        Files.writeString(script, "// stub");
        MineflayerLauncher launcher = new MineflayerLauncher(script, "definitely-not-node", "127.0.0.1",
                25565, new InMemoryBotRunner(), executable -> false);

        assertFalse(launcher.available());
        assertTrue(launcher.start(scenario()).isEmpty());
        assertTrue(launcher.note().contains("node"));
        assertTrue(launcher.note().contains("未启动"));
    }

    @Test
    void degradesWhenScriptIsMissing() {
        Path missing = tempDir.resolve("missing.js");
        MineflayerLauncher launcher = new MineflayerLauncher(missing, "node", "127.0.0.1", 25565,
                new InMemoryBotRunner(), executable -> true);

        assertFalse(launcher.available());
        assertTrue(launcher.start(scenario()).isEmpty());
        assertTrue(launcher.note().contains("脚本不存在"));
    }

    private static LoadScenario scenario() {
        return new LoadScenario("net", BotBackend.MINEFLAYER,
                List.of(BotAction.JOIN, BotAction.WALK, BotAction.MINE), 1200,
                new RampCurve(10, 10, 200, 30), "");
    }

    private static String valueAfter(List<String> command, String option) {
        int index = command.indexOf(option);
        assertTrue(index >= 0, "缺少参数: " + option);
        return command.get(index + 1);
    }
}
