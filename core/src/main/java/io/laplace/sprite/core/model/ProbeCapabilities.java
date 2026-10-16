package io.laplace.sprite.core.model;

import java.util.Locale;

/**
 * 探针能力位（5. ProbeSnapshot.supported）。
 *
 * <p>用来区分「查了但没检出」与「根本查不了」——把「查不了」算成「没检出」会让
 * 客户端扫描器的正反例验收失真（9. 验收 2）。</p>
 */
public enum ProbeCapabilities {
    PROCESSES,
    MODULES,
    MEMORY,
    USB,
    REGISTRY,
    NETWORK,
    SIGNATURES,
    DRIVERS,
    INJECTION;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
