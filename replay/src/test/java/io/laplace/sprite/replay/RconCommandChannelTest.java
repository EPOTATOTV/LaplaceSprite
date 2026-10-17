package io.laplace.sprite.replay;

import io.laplace.sprite.loadtest.BotAction;
import io.laplace.sprite.loadtest.BotBackend;
import io.laplace.sprite.loadtest.CarpetLauncher;
import io.laplace.sprite.loadtest.LoadScenario;
import io.laplace.sprite.loadtest.RampCurve;
import io.laplace.sprite.sandbox.RconConsole;
import io.laplace.sprite.sandbox.RconPacket;
import io.laplace.sprite.sandbox.RconTransport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RconCommandChannelTest {

    @Test
    void carpetLauncherReachesRconConsoleThroughAdapter() {
        RecordingTransport transport = new RecordingTransport();
        RconConsole console = RconConsole.over("127.0.0.1", 25575, "pwd", transport);
        CarpetLauncher launcher = new CarpetLauncher(new RconCommandChannel(console));
        LoadScenario scenario = new LoadScenario("carpet", BotBackend.CARPET,
                List.of(BotAction.JOIN, BotAction.MINE), 100, RampCurve.single(1), "");

        launcher.execute(scenario);

        assertTrue(console.authenticated());
        assertEquals(List.of("player lp-bot-1 spawn", "player lp-bot-1 attack continuous",
                "player lp-bot-1 stop"), transport.commands);
    }

    private static final class RecordingTransport implements RconTransport {

        private final List<String> commands = new ArrayList<>();

        @Override
        public byte[] exchange(byte[] request) {
            RconPacket packet = RconPacket.decode(request);
            if (packet.type() == RconPacket.Type.LOGIN) {
                return new RconPacket(packet.id(), RconPacket.Type.RESPONSE_VALUE, "").encode();
            }
            commands.add(packet.body());
            return new RconPacket(packet.id(), RconPacket.Type.RESPONSE_VALUE, "ok").encode();
        }
    }
}
