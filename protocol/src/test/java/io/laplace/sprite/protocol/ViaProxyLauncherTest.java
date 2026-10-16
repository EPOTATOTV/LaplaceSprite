package io.laplace.sprite.protocol;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 统一入口渲染（15.6）：jar 缺失时降级为 dry-run，不抛异常也不假装已拉起。 */
class ViaProxyLauncherTest {

    @Test
    void rendersStartCommandAndConfigWhenJarAbsent() {
        Path jar = Path.of("build", "missing-viaproxy.jar");
        ViaProxyLauncher launcher = new ViaProxyLauncher(jar, 25568, "127.0.0.1", 25565);

        assertFalse(launcher.available());
        assertEquals(List.of("java", "-jar", jar.toString(),
                "--bind-address", "0.0.0.0",
                "--bind-port", "25568",
                "--target-address", "127.0.0.1",
                "--target-port", "25565"), launcher.startCommand());
        assertEquals("0.0.0.0:25568", launcher.listenAddress());
        assertEquals("127.0.0.1:25565", launcher.targetAddress());
        assertTrue(launcher.configYaml().contains("target-port: 25565"));
        assertEquals(1, launcher.notes().size());
        assertTrue(launcher.notes().get(0).contains("未找到 ViaProxy jar"));
    }

    @Test
    void writesConfigWhenJarIsPresent() throws IOException {
        Path dir = Files.createTempDirectory("viaproxy");
        Path jar = Files.writeString(dir.resolve("ViaProxy.jar"), "stub");
        ViaProxyLauncher launcher = new ViaProxyLauncher(jar, 25568, "127.0.0.1", 25565);

        assertTrue(launcher.available());
        assertTrue(launcher.notes().isEmpty());

        Path config = launcher.writeConfig(dir.resolve("conf"));
        assertEquals(dir.resolve("conf").resolve("viaproxy.yml"), config);
        assertTrue(Files.readString(config).contains("bind-port: 25568"));
    }

    @Test
    void bindsCustomAddressAndBinary() {
        ViaProxyLauncher launcher = new ViaProxyLauncher(Path.of("build", "ViaProxy.jar"), 25570, "paper-1", 25565,
                "127.0.0.1", "/opt/jdk/bin/java");
        assertEquals("127.0.0.1:25570", launcher.listenAddress());
        assertEquals("/opt/jdk/bin/java", launcher.startCommand().get(0));
    }

    @Test
    void geyserBridgeRendersFloodgateConfigWhenJarsAbsent() {
        GeyserBridgeSpec spec = GeyserBridgeSpec.standard(Path.of("build", "Geyser-Spigot.jar"),
                Path.of("build", "floodgate.jar"), 25565);

        assertFalse(spec.available());
        assertFalse(spec.geyserAvailable());
        assertFalse(spec.floodgateAvailable());
        assertEquals("floodgate", spec.authType());
        assertTrue(spec.configYaml().contains("auth-type: floodgate"));
        assertTrue(spec.configYaml().contains("port: 19132"));
        assertTrue(spec.configYaml().contains("port: 25565"));
        assertEquals(List.of("plugins/Geyser-Spigot.jar", "plugins/floodgate.jar"), spec.installTargets());
        assertTrue(spec.notes().get(0).contains("基岩格只能渲染配置"));
    }

    @Test
    void geyserBridgeIsAvailableWhenBothJarsExist() throws IOException {
        Path dir = Files.createTempDirectory("geyser");
        Path geyser = Files.writeString(dir.resolve("Geyser-Spigot.jar"), "stub");
        Path floodgate = Files.writeString(dir.resolve("floodgate.jar"), "stub");

        GeyserBridgeSpec spec = new GeyserBridgeSpec(geyser, floodgate, 19132, 25565, null);
        assertTrue(spec.available());
        assertTrue(spec.notes().isEmpty());
        assertEquals(GeyserBridgeSpec.FLOODGATE_AUTH, spec.authType(), "authType 为空时回落到 floodgate");
    }
}
