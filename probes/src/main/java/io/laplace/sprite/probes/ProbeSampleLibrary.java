package io.laplace.sprite.probes;

import io.laplace.sprite.core.model.ProbeCapabilities;
import io.laplace.sprite.core.model.ProbeSnapshot;
import io.laplace.sprite.core.model.UsbDevice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 探针桩样本库（§9 验收 2：探针桩样本正反例全覆盖）。
 *
 * <p>正例按 {@link CheatClientSignatures#clients()} 每个客户端至少一份，另含一份硬件外挂样本；
 * 反例覆盖不同启动器、合法模组、高端硬件等不应被误判的形态。</p>
 *
 * <p>另含一份「查不了」的退化样本：沙箱禁用 USB 与注册表枚举，这两个维度在快照里显式标为不支持，
 * 用来把「没检出」与「查不了」分开（第五章 {@code ProbeSnapshot.supported}）。</p>
 */
public final class ProbeSampleLibrary {

    private static final String HARDWARE_CHEAT = "hardware-cheat";

    private final ProcessScannerStub scanner = new ProcessScannerStub();
    private final CheatClientSignatures signatures = new CheatClientSignatures();
    private final Map<String, List<ProbeSnapshot>> samplesByClient = new LinkedHashMap<>();
    private final ProbeSnapshot baseline;
    private final ProbeSnapshot degraded;
    private final List<ProbeSnapshot> negatives;

    public ProbeSampleLibrary() {
        baseline = new ProbeSnapshot(scanner.fingerprints(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), Map.of());
        degraded = degradedSample();
        negatives = buildNegatives();
        for (String client : signatures.clients()) {
            samplesByClient.put(client, List.of(clientSample(client)));
        }
        samplesByClient.put(HARDWARE_CHEAT, List.of(hardwareCheatSample()));
    }

    /** 干净基线：仅原版进程指纹。 */
    public ProbeSnapshot baseline() {
        return baseline;
    }

    /** 退化样本：USB 与注册表枚举被禁用，两个维度标为「查不了」。 */
    public ProbeSnapshot degradedSample() {
        return new ProbeSnapshot(
                List.of("javaw.exe", "Minecraft.exe"),
                List.of(), List.of(),
                List.of(),
                List.of(),
                List.of("play.example.net:25565"),
                List.of(), List.of(), Map.of(),
                capabilitiesWithout(ProbeCapabilities.USB, ProbeCapabilities.REGISTRY));
    }

    /** 合法变体：不得被判定为注入（含退化样本）。 */
    public List<ProbeSnapshot> negativeSamples() {
        return negatives;
    }

    /** 作弊样本：每个客户端至少一份，另含硬件外挂样本。 */
    public List<ProbeSnapshot> positiveSamples() {
        List<ProbeSnapshot> all = new ArrayList<>();
        samplesByClient.values().forEach(all::addAll);
        return List.copyOf(all);
    }

    public List<ProbeSnapshot> byClient(String client) {
        return samplesByClient.getOrDefault(client, List.of());
    }

    /** 各 profile（客户端 / hardware-cheat）的样本数。 */
    public Map<String, Integer> sampleCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        samplesByClient.forEach((client, samples) -> counts.put(client, samples.size()));
        return counts;
    }

    private ProbeSnapshot clientSample(String client) {
        return new ProbeSnapshot(
                List.of(client.toLowerCase(Locale.ROOT) + ".exe", "javaw.exe"),
                signatures.moduleNames(client),
                signatures.memoryPatterns(client),
                List.of(),
                signatures.registryKeys(client),
                signatures.networkEndpoints(client),
                signatures.signatureSubjects(client),
                List.of(),
                Map.of());
    }

    private ProbeSnapshot hardwareCheatSample() {
        return new ProbeSnapshot(
                List.of("javaw.exe", "Minecraft.exe", "arduino-cli.exe"),
                List.of("hw-cheat-serial.jar"),
                List.of(),
                signatures.mcuDevices(),
                List.of("HKCU\\Software\\Arduino"),
                List.of(),
                List.of(),
                List.of("CH341SER.SYS"),
                Map.of());
    }

    private List<ProbeSnapshot> buildNegatives() {
        List<ProbeSnapshot> list = new ArrayList<>();
        // 原版启动器
        list.add(new ProbeSnapshot(
                List.of("MinecraftLauncher.exe", "javaw.exe", "java.exe", "explorer.exe"),
                List.of(), List.of(), List.of(),
                List.of("HKCU\\Software\\Mojang"), List.of(), List.of(), List.of(), Map.of()));
        // 第三方启动器 + 独显 / 声卡驱动
        list.add(new ProbeSnapshot(
                List.of("MultiMC.exe", "PrismLauncher.exe", "javaw.exe"),
                List.of(), List.of(), List.of(),
                List.of("HKCU\\Software\\PrismLauncher"),
                List.of("play.hypixel.net:25565"),
                List.of(),
                List.of("nvlddmkm.sys", "RTKVHD64.sys"), Map.of()));
        // 合法模组（Fabric 生态）
        list.add(new ProbeSnapshot(
                List.of("javaw.exe", "Minecraft.exe"),
                List.of("fabric-loader-0.15.11.jar", "sodium-fabric.jar", "lithium.jar",
                        "iris-shaders.jar", "xaeros-minimap.jar"),
                List.of(), List.of(),
                List.of("HKCU\\Software\\Fabric"),
                List.of("play.example.net:25565"), List.of(), List.of(), Map.of()));
        // 高端硬件 / RGB 外设（USB 设备 mcu=false）
        list.add(new ProbeSnapshot(
                List.of("javaw.exe", "Minecraft.exe", "RazerSynapse.exe", "MSIAfterburner.exe",
                        "Corsair.Service.exe"),
                List.of(), List.of(),
                List.of(new UsbDevice("1532", "0253", "Razer 鼠标（HID）", false)),
                List.of("HKCU\\Software\\Razer"), List.of(), List.of(),
                List.of("RzCommon.sys"), Map.of()));
        // Forge 生态合法模组
        list.add(new ProbeSnapshot(
                List.of("javaw.exe"),
                List.of("forge-1.20.1.jar", "optifine-1.20.1.jar", "jei-1.20.1.jar"),
                List.of(), List.of(),
                List.of("HKCU\\Software\\Forge"), List.of(), List.of(), List.of(), Map.of()));
        // 锁定环境：USB 与注册表查不了，不能据此认定清白
        list.add(degraded);
        return List.copyOf(list);
    }

    /** 在全维可查的基础上把指定维度标为查不了。 */
    private static Map<String, Boolean> capabilitiesWithout(ProbeCapabilities... unsupported) {
        Map<String, Boolean> capabilities = new LinkedHashMap<>(ProbeSnapshot.fullCapabilities());
        for (ProbeCapabilities capability : unsupported) {
            capabilities.put(capability.key(), false);
        }
        return Map.copyOf(capabilities);
    }
}
