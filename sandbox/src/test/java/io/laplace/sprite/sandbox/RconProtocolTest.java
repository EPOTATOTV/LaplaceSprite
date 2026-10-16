package io.laplace.sprite.sandbox;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RCON 控制面（15.4）：帧编解码往返、命令收敛、认证失败处理，全程无真实服务端。 */
class RconProtocolTest {

    @Test
    void frameRoundTrips() {
        RconPacket packet = new RconPacket(7, RconPacket.Type.COMMAND, "spark tps");
        byte[] frame = packet.encode();

        assertEquals(4 + 4 + 4 + "spark tps".length() + 2, frame.length);
        assertEquals(19, frame[0], "长度字段是 little-endian，低字节在前");
        assertEquals(0, frame[1]);
        assertEquals("spark tps", new String(frame, 12, 9, java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(0, frame[frame.length - 2], "body 后是 NUL 结尾");
        assertEquals(0, frame[frame.length - 1]);

        RconPacket decoded = RconPacket.decode(frame);
        assertEquals(7, decoded.id());
        assertEquals(RconPacket.Type.COMMAND, decoded.type());
        assertEquals("spark tps", decoded.body());
        assertEquals(RconPacket.Type.LOGIN, RconPacket.decode(
                new RconPacket(1, RconPacket.Type.LOGIN, "pw").encode()).type());
        assertEquals(RconPacket.Type.RESPONSE_VALUE, RconPacket.decode(
                new RconPacket(1, RconPacket.Type.RESPONSE_VALUE, "done").encode()).type());
    }

    @Test
    void decodeRejectsBrokenFrames() {
        assertThrows(IllegalArgumentException.class, () -> RconPacket.decode(null));
        assertThrows(IllegalArgumentException.class, () -> RconPacket.decode(new byte[4]));

        byte[] unknownType = new RconPacket(1, RconPacket.Type.COMMAND, "x").encode();
        unknownType[8] = 99;
        assertThrows(IllegalArgumentException.class, () -> RconPacket.decode(unknownType));

        byte[] badLength = new RconPacket(1, RconPacket.Type.COMMAND, "x").encode();
        badLength[0] = 0;
        badLength[1] = 0;
        badLength[2] = 0;
        badLength[3] = 0;
        assertThrows(IllegalArgumentException.class, () -> RconPacket.decode(badLength));
    }

    @Test
    void loginThenCommandStripsLeadingSlash() {
        FakeTransport transport = new FakeTransport();
        RconConsole console = RconConsole.over("127.0.0.1", 25575, "pw", transport);

        console.login();

        assertTrue(console.authenticated());
        assertEquals("pw", transport.loginBody);
        assertEquals("ok", console.commandText("/spark tps"));
        assertEquals(List.of("spark tps"), transport.commands, "RCON 不接受前导斜杠");
    }

    @Test
    void controlPlaneHelpersSendExpectedCommands() {
        FakeTransport transport = new FakeTransport();
        RconConsole console = RconConsole.over("127.0.0.1", 25575, "pw", transport);
        console.login();

        console.sparkTps();
        console.sparkHealth();
        console.reloadDataPacks();
        console.triggerCase("case-9");

        assertEquals(List.of("spark tps", "spark health", "minecraft:reload", "laplace run case-9"),
                transport.commands);
    }

    @Test
    void authFailureAndMisuseAreRejected() {
        FakeTransport failing = new FakeTransport();
        failing.authFails = true;
        RconConsole console = RconConsole.over("127.0.0.1", 25575, "bad", failing);
        IllegalStateException authError = assertThrows(IllegalStateException.class, console::login);
        assertTrue(authError.getMessage().contains("认证失败"));

        FakeTransport transport = new FakeTransport();
        RconConsole unauthenticated = RconConsole.over("127.0.0.1", 25575, "pw", transport);
        assertThrows(IllegalStateException.class, () -> unauthenticated.command("spark tps"));
        unauthenticated.login();
        assertThrows(IllegalArgumentException.class, () -> unauthenticated.command("  "));
        assertThrows(IllegalArgumentException.class, () -> unauthenticated.command(null));
    }

    @Test
    void consoleUsesSpecPortAndDerivedPassword() {
        FakeTransport transport = new FakeTransport();
        SandboxSpec spec = SandboxSpec.forTier("case-rcon", SandboxTier.SMOKE, "img", "PAPER", "1.21.11",
                List.of(), java.nio.file.Path.of("build", "sandbox-test", "data"), 25575);
        RconConsole console = RconConsole.forSpec(spec, transport);

        assertEquals(25575, console.port());
        console.login();
        assertEquals(spec.rconPassword(), transport.loginBody);
        console.close();
        assertTrue(transport.closed);
    }

    /** 内存替身：只按协议回应，不碰网络。 */
    private static final class FakeTransport implements RconTransport {

        private final List<String> commands = new ArrayList<>();
        private String loginBody = "";
        private String reply = "ok";
        private boolean authFails;
        private boolean closed;

        @Override
        public byte[] exchange(byte[] request) {
            RconPacket packet = RconPacket.decode(request);
            if (packet.type() == RconPacket.Type.LOGIN) {
                loginBody = packet.body();
                int id = authFails ? -1 : packet.id();
                return new RconPacket(id, RconPacket.Type.RESPONSE_VALUE, "").encode();
            }
            commands.add(packet.body());
            return new RconPacket(packet.id(), RconPacket.Type.RESPONSE_VALUE, reply).encode();
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
