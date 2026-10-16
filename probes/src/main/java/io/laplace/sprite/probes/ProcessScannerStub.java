package io.laplace.sprite.probes;

import io.laplace.sprite.core.model.ProbeCapabilities;
import io.laplace.sprite.core.model.ProbeSnapshot;
import io.laplace.sprite.core.model.UsbDevice;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 探针桩：进程指纹扫描（4.2 玩家端样本 / 第五章 ProbeSnapshot）。
 *
 * <p>指纹为原版基线，不含任何作弊相关痕迹；判定注入时命中任一作弊关键字即视为可疑。</p>
 *
 * <p>能力位决定一个维度「能不能查」：查不了的维度在判定里跳过。这里的取舍是——查不了<b>不等于</b>干净，
 * 也不等于命中，只是这一维度的证据缺失；因此 {@code false} 的判定在有维度查不了时是不完整的，
 * 调用方必须看 {@link #unsupportedDimensions(ProbeSnapshot)} / {@link #detectionIsComplete(ProbeSnapshot)}
 * 才知道结论覆盖到哪，不能把「查不了」当成「没检出」的清白证据。</p>
 */
public final class ProcessScannerStub {

    /** 作弊关键字：进程 / 模块 / 内存 / 注册表 / 网络 / 签名任一命中即判定注入。 */
    private static final Set<String> CHEAT_MARKERS = Set.of(
            "wurst", "liquidbounce", "meteor", "thunderhack", "bleachhack", "lambda",
            "xray", "x-ray", "injector", "cheat", "salhack", "konas", "aristois",
            "impact", "future", "kami", "cortex", "weepcraft", "ploow", "sigma");

    private static final List<String> FINGERPRINTS = List.of(
            // JVM 与启动器
            "java.exe", "javaw.exe", "javac.exe", "jps.exe", "jcmd.exe", "jstack.exe", "jconsole.exe",
            "Minecraft.exe", "MinecraftLauncher.exe", "minecraft-launcher.exe", "launcher.exe",
            "MultiMC.exe", "PrismLauncher.exe", "ATLauncher.exe", "TechnicLauncher.exe", "HMCL.exe",
            "PCLauncher.exe", "CurseForge.exe", "Overwolf.exe", "LunarClient.exe", "BadlionClient.exe",
            // Windows 核心
            "explorer.exe", "svchost.exe", "lsass.exe", "csrss.exe", "wininit.exe", "winlogon.exe",
            "services.exe", "smss.exe", "dwm.exe", "taskhostw.exe", "sihost.exe", "ctfmon.exe",
            "RuntimeBroker.exe", "conhost.exe", "dllhost.exe", "backgroundTaskHost.exe", "audiodg.exe",
            "fontdrvhost.exe", "spoolsv.exe", "taskmgr.exe", "cmd.exe", "powershell.exe",
            "WindowsTerminal.exe", "wscript.exe", "cscript.exe", "notepad.exe", "mspaint.exe",
            "SearchApp.exe", "SearchIndexer.exe", "SearchProtocolHost.exe", "StartMenuExperienceHost.exe",
            "ShellExperienceHost.exe", "TextInputHost.exe", "SystemSettings.exe", "ApplicationFrameHost.exe",
            "Widgets.exe", "PhoneExperienceHost.exe", "MoUsoCoreWorker.exe", "AggregatorHost.exe",
            "CompatTelRunner.exe", "wermgr.exe", "WerFault.exe", "MsMpEng.exe", "NisSrv.exe",
            "SecurityHealthService.exe", "SecurityHealthSystray.exe", "MpCmdRun.exe",
            // 常见桌面软件
            "chrome.exe", "msedge.exe", "firefox.exe", "Discord.exe", "Spotify.exe", "steam.exe",
            "steamwebhelper.exe", "obs64.exe", "Teams.exe", "ms-teams.exe", "OneDrive.exe",
            "OUTLOOK.EXE", "WINWORD.EXE", "EXCEL.EXE", "iTunesHelper.exe", "CCXProcess.exe",
            "Adobe Desktop Service.exe",
            // 显卡与外设
            "NVDisplay.Container.exe", "nvidia-smi.exe", "RadeonSoftware.exe", "igfxEM.exe",
            "RtkAudUService64.exe", "RazerSynapse.exe", "MSIAfterburner.exe", "Corsair.Service.exe");

    private static final List<String> CHEAT_PROCESSES = List.of(
            "wurst.exe", "wurst-client.exe", "liquidbounce.exe", "liquidbounce-injector.exe",
            "meteor-client.exe", "thunderhack.exe", "bleachhack.exe", "lambda.exe", "lambda-client.exe",
            "xray.exe", "xray-mod.exe", "chunk-xray.exe", "dllinjector.exe", "cheatloader.exe",
            "injector.exe");

    /** 一个可查维度：内容 + 对应能力位。 */
    private record Dimension(List<String> values, ProbeCapabilities capability) {
    }

    public List<String> fingerprints() {
        return FINGERPRINTS;
    }

    public List<String> cheatProcessNames() {
        return CHEAT_PROCESSES;
    }

    public int fingerprintCount() {
        return FINGERPRINTS.size();
    }

    /** 本桩实际能评估的能力位（全维度）。 */
    public Set<ProbeCapabilities> evaluableCapabilities() {
        return EnumSet.allOf(ProbeCapabilities.class);
    }

    /** 快照里查不了的维度；这些维度的内容在判定中被跳过。 */
    public List<ProbeCapabilities> unsupportedDimensions(ProbeSnapshot snapshot) {
        Set<ProbeCapabilities> evaluable = evaluableCapabilities();
        return Arrays.stream(ProbeCapabilities.values())
                .filter(evaluable::contains)
                .filter(capability -> !snapshot.supports(capability))
                .toList();
    }

    /** 判定是否完整：有维度查不了即为不完整，`false` 只能读作「未发现」，不能读作「清白」。 */
    public boolean detectionIsComplete(ProbeSnapshot snapshot) {
        return unsupportedDimensions(snapshot).isEmpty();
    }

    /**
     * 启发式注入判定：注入痕迹 / MCU 设备 / 任一可查文本域命中作弊关键字。
     */
    public boolean looksInjected(ProbeSnapshot snapshot) {
        if (snapshot.supports(ProbeCapabilities.INJECTION) && !snapshot.injections().isEmpty()) {
            return true;
        }
        if (snapshot.supports(ProbeCapabilities.USB)
                && snapshot.usbDevices().stream().anyMatch(UsbDevice::mcu)) {
            return true;
        }
        for (Dimension dimension : dimensions(snapshot)) {
            if (!snapshot.supports(dimension.capability())) {
                continue;
            }
            for (String value : dimension.values()) {
                if (containsMarker(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Dimension> dimensions(ProbeSnapshot snapshot) {
        return List.of(
                new Dimension(snapshot.processes(), ProbeCapabilities.PROCESSES),
                new Dimension(snapshot.modules(), ProbeCapabilities.MODULES),
                new Dimension(snapshot.memoryPatterns(), ProbeCapabilities.MEMORY),
                new Dimension(snapshot.registryHits(), ProbeCapabilities.REGISTRY),
                new Dimension(snapshot.networkHits(), ProbeCapabilities.NETWORK),
                new Dimension(snapshot.signatures(), ProbeCapabilities.SIGNATURES),
                new Dimension(snapshot.drivers(), ProbeCapabilities.DRIVERS));
    }

    private static boolean containsMarker(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        for (String marker : CHEAT_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
