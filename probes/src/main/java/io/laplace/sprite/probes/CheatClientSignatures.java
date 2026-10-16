package io.laplace.sprite.probes;

import io.laplace.sprite.core.model.UsbDevice;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 社区作弊客户端指纹库（4.2 玩家端样本）。
 *
 * <p>每个客户端给出一组可观测痕迹：模块 / 内存模式 / 注册表 / 网络端点 / 数字签名；
 * 另附硬件外挂常用的 MCU 设备。</p>
 */
public final class CheatClientSignatures {

    private record Signature(List<String> modules, List<String> memory, List<String> registry,
                             List<String> endpoints, List<String> signatures) {
    }

    private static final Signature EMPTY = new Signature(List.of(), List.of(), List.of(), List.of(), List.of());

    private static final Map<String, Signature> LIBRARY = buildLibrary();

    private static final List<UsbDevice> MCU_DEVICES = List.of(
            new UsbDevice("2341", "0043", "Arduino Uno R3（硬件外挂 MCU）", true),
            new UsbDevice("1A86", "7523", "CH340 USB 串口（硬件外挂桥）", true),
            new UsbDevice("10C4", "EA60", "CP2102 USB-UART（硬件外挂 MCU）", true),
            new UsbDevice("0483", "5740", "STM32 Virtual COM（硬件外挂 MCU）", true),
            new UsbDevice("1B4F", "9203", "SparkFun Pro Micro（硬件外挂 MCU）", true));

    /** 库中登记的客户端名。 */
    public Set<String> clients() {
        return Collections.unmodifiableSet(LIBRARY.keySet());
    }

    public List<String> moduleNames(String client) {
        return sig(client).modules();
    }

    public List<String> memoryPatterns(String client) {
        return sig(client).memory();
    }

    public List<String> registryKeys(String client) {
        return sig(client).registry();
    }

    public List<String> networkEndpoints(String client) {
        return sig(client).endpoints();
    }

    public List<String> signatureSubjects(String client) {
        return sig(client).signatures();
    }

    /** 硬件外挂常用的 USB MCU 设备。 */
    public List<UsbDevice> mcuDevices() {
        return MCU_DEVICES;
    }

    private static Signature sig(String client) {
        return LIBRARY.getOrDefault(client, EMPTY);
    }

    private static Map<String, Signature> buildLibrary() {
        Map<String, Signature> map = new LinkedHashMap<>();
        map.put("Wurst", new Signature(
                List.of("wurst.dll", "wurst-client.jar", "wurst.injector.dll"),
                List.of("wurst.mods.Killaura", "wurst.mods.Fly", "net.wurstclient."),
                List.of("HKCU\\Software\\Wurst", "HKCU\\Software\\WurstClient\\Settings"),
                List.of("wurstclient.net:443", "api.wurstclient.net:443"),
                List.of("CN=Wurst Client", "CN=Alexander Wurst")));
        map.put("LiquidBounce", new Signature(
                List.of("liquidbounce.jar", "lb-modules.jar", "cc.paimon.liquidbounce.jar"),
                List.of("net.ccbluex.liquidbounce.", "liquidbounce.module."),
                List.of("HKCU\\Software\\LiquidBounce"),
                List.of("liquidbounce.net:443", "legacy.liquidbounce.net:443"),
                List.of("CN=LiquidBounce", "CN=CCBlueX")));
        map.put("Meteor", new Signature(
                List.of("meteor-client.jar", "meteor.mixin.jar", "meteordevelopment.jar"),
                List.of("meteordevelopment.meteorclient.", "meteor-client.modules."),
                List.of("HKCU\\Software\\MeteorClient"),
                List.of("meteorclient.com:443", "meteordevelopment.github.io:443"),
                List.of("CN=Meteor Development", "CN=Meteor Client")));
        map.put("ThunderHack", new Signature(
                List.of("thunderhack.jar", "thunderhack-client.dll", "th-modules.jar"),
                List.of("thunderhack.modules.", "thunderhack.util."),
                List.of("HKCU\\Software\\ThunderHack"),
                List.of("thunderhack.ru:443", "api.thunderhack.ru:443"),
                List.of("CN=ThunderHack", "CN=TH Team")));
        map.put("BleachHack", new Signature(
                List.of("bleachhack.jar", "bleachhack-modules.dll"),
                List.of("bleachhack.module.", "bleachhack.util."),
                List.of("HKCU\\Software\\BleachHack"),
                List.of("bleachhack.org:443"),
                List.of("CN=BleachHack")));
        map.put("Lambda", new Signature(
                List.of("lambda.jar", "lambda-client.dll", "lambda-agent.jar"),
                List.of("lambda.modules.", "org.lambda."),
                List.of("HKCU\\Software\\Lambda"),
                List.of("lambda-client.com:443"),
                List.of("CN=Lambda Client")));
        return map;
    }
}
