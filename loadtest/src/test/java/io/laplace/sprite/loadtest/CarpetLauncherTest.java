package io.laplace.sprite.loadtest;

import io.laplace.sprite.profile.CommandChannel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarpetLauncherTest {

    @Test
    void rendersSpawnThenActionsThenStopForEveryPlayer() {
        CarpetLauncher launcher = new CarpetLauncher(new RecordingChannel());
        LoadScenario scenario = new LoadScenario("carpet", BotBackend.CARPET,
                List.of(BotAction.JOIN, BotAction.WALK, BotAction.MINE, BotAction.PLACE,
                        BotAction.ATTACK, BotAction.CHAT, BotAction.IDLE),
                400, new RampCurve(2, 2, 100, 3), "");

        List<String> commands = launcher.commands(scenario);

        assertEquals(3, commands.stream().filter(command -> command.endsWith(" spawn")).count());
        assertEquals(3, commands.stream().filter(command -> command.endsWith(" stop")).count());
        assertEquals("/player lp-bot-1 spawn", commands.get(0));
        assertEquals("/player lp-bot-3 stop", commands.get(commands.size() - 1));
        int firstAction = commands.indexOf("/player lp-bot-1 move forward");
        int firstStop = commands.indexOf("/player lp-bot-1 stop");
        assertTrue(firstAction > 2);
        assertTrue(firstStop > firstAction);
        assertTrue(commands.stream().noneMatch(command -> command.contains("chat")));
        assertTrue(commands.stream().noneMatch(command -> command.contains("idle")));
    }

    @Test
    void sendsCommandsThroughChannelInOrder() {
        RecordingChannel channel = new RecordingChannel();
        CarpetLauncher launcher = new CarpetLauncher(channel);
        LoadScenario scenario = new LoadScenario("carpet", BotBackend.CARPET,
                List.of(BotAction.JOIN, BotAction.WALK, BotAction.MINE), 200,
                new RampCurve(1, 1, 100, 2), "");

        List<String> commands = launcher.execute(scenario);

        assertEquals(commands, channel.commands);
        assertEquals("/player lp-bot-1 spawn", channel.commands.get(0));
        assertEquals("/player lp-bot-2 stop", channel.commands.get(channel.commands.size() - 1));
    }

    @Test
    void mapsActionsToCarpetSubcommands() {
        assertEquals(Optional.of("move forward"), CarpetLauncher.carpetAction(BotAction.WALK));
        assertEquals(Optional.of("attack continuous"), CarpetLauncher.carpetAction(BotAction.MINE));
        assertEquals(Optional.of("attack continuous"), CarpetLauncher.carpetAction(BotAction.ATTACK));
        assertEquals(Optional.of("use continuous"), CarpetLauncher.carpetAction(BotAction.PLACE));
        assertTrue(CarpetLauncher.carpetAction(BotAction.JOIN).isEmpty());
        assertTrue(CarpetLauncher.carpetAction(BotAction.CHAT).isEmpty());
        assertTrue(CarpetLauncher.carpetAction(BotAction.IDLE).isEmpty());
    }

    @Test
    void entityAndChunkLoadsRenderRealServerCommands() {
        CarpetLauncher launcher = new CarpetLauncher(new RecordingChannel());
        LoadScenario scenario = new LoadScenario("load-26.2", BotBackend.CARPET,
                List.of(BotAction.JOIN, BotAction.ENTITY_LOAD, BotAction.CHUNK_LOAD), 600,
                RampCurve.single(2), "");

        List<String> commands = launcher.commands(scenario);

        assertEquals(2, commands.stream().filter(command -> command.contains("summon minecraft:armor_stand"))
                .count());
        assertEquals(2, commands.stream().filter(command -> command.contains("forceload add"))
                .count());
        assertTrue(commands.stream().anyMatch(command -> command.contains("Tags:[\"laplace-load\"]")));
        assertTrue(commands.stream().anyMatch(command -> command.contains("forceload add ~-2 ~-2 ~2 ~2")));
        assertTrue(CarpetLauncher.carpetAction(BotAction.ENTITY_LOAD).isEmpty());
        assertTrue(CarpetLauncher.carpetAction(BotAction.CHUNK_LOAD).isEmpty());
    }

    private static final class RecordingChannel implements CommandChannel {

        private final List<String> commands = new ArrayList<>();

        @Override
        public String send(String command) {
            commands.add(command);
            return "ok";
        }
    }
}
