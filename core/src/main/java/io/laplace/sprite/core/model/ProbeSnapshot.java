package io.laplace.sprite.core.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 探针桩快照（第五章 ProbeSnapshot）：玩家端样本注入，与 SystemProbe 接口对齐。
 *
 * @param processes      进程指纹（ProcessScanner 80+ 项）
 * @param modules        模块 / DLL 列表
 * @param memoryPatterns 内存模式
 * @param usbDevices     USB 设备（MCU VID）
 * @param registryHits   注册表命中
 * @param networkHits    网络连接命中
 * @param signatures     数字签名
 * @param drivers        驱动
 * @param injections     注入痕迹
 * @param supported      能力位：该维度是否可查（区分「没检出」与「查不了」）
 */
public record ProbeSnapshot(
        List<String> processes,
        List<String> modules,
        List<String> memoryPatterns,
        List<UsbDevice> usbDevices,
        List<String> registryHits,
        List<String> networkHits,
        List<String> signatures,
        List<String> drivers,
        Map<String, Object> injections,
        Map<String, Boolean> supported) {

    public ProbeSnapshot {
        processes = processes == null ? List.of() : List.copyOf(processes);
        modules = modules == null ? List.of() : List.copyOf(modules);
        memoryPatterns = memoryPatterns == null ? List.of() : List.copyOf(memoryPatterns);
        usbDevices = usbDevices == null ? List.of() : List.copyOf(usbDevices);
        registryHits = registryHits == null ? List.of() : List.copyOf(registryHits);
        networkHits = networkHits == null ? List.of() : List.copyOf(networkHits);
        signatures = signatures == null ? List.of() : List.copyOf(signatures);
        drivers = drivers == null ? List.of() : List.copyOf(drivers);
        injections = injections == null ? Map.of() : Map.copyOf(injections);
        supported = supported == null ? fullCapabilities() : Map.copyOf(supported);
    }

    /** 兼容构造：未显式声明能力位时视为全维可查（合成样本默认如此）。 */
    public ProbeSnapshot(List<String> processes, List<String> modules, List<String> memoryPatterns,
                         List<UsbDevice> usbDevices, List<String> registryHits, List<String> networkHits,
                         List<String> signatures, List<String> drivers, Map<String, Object> injections) {
        this(processes, modules, memoryPatterns, usbDevices, registryHits, networkHits, signatures,
                drivers, injections, fullCapabilities());
    }

    /** 全维可查。 */
    public static Map<String, Boolean> fullCapabilities() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (ProbeCapabilities capability : ProbeCapabilities.values()) {
            map.put(capability.key(), true);
        }
        return Map.copyOf(map);
    }

    /** 干净基线：无任何作弊相关痕迹，全维可查。 */
    public static ProbeSnapshot clean() {
        return new ProbeSnapshot(List.of("javaw.exe", "minecraft.exe"), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    /** 该维度是否可查；未声明视为不可查（保守）。 */
    public boolean supports(ProbeCapabilities capability) {
        return Boolean.TRUE.equals(supported.get(capability.key()));
    }

    /** 明确声明为「查不了」的维度。 */
    public List<ProbeCapabilities> unsupportedCapabilities() {
        return java.util.Arrays.stream(ProbeCapabilities.values())
                .filter(capability -> !supports(capability))
                .toList();
    }

    public boolean isEmpty() {
        return processes.isEmpty() && modules.isEmpty() && memoryPatterns.isEmpty()
                && usbDevices.isEmpty() && registryHits.isEmpty() && networkHits.isEmpty()
                && signatures.isEmpty() && drivers.isEmpty() && injections.isEmpty();
    }
}
