package io.laplace.sprite.sandbox;

import java.util.List;
import java.util.Locale;

/**
 * 沙箱网络策略（15.4 网络隔离）：每轮独立 docker network，默认拒绝全部出站，仅放行依赖白名单。
 *
 * <p>白名单本身只是声明：docker 不提供按域名放行，真正生效要靠承载该网络的宿主防火墙 / 出口代理
 * 读 {@link #allowlistLabel()}。测试服端口一律只绑 127.0.0.1。</p>
 *
 * @param networkName  网络名
 * @param denyOutbound 是否默认拒绝全部出站
 * @param allowlist    允许出站的域名白名单
 */
public record SandboxNetworkPolicy(String networkName, boolean denyOutbound, List<String> allowlist) {

    public static final String NETWORK_PREFIX = "laplace-sandbox-";

    private static final List<String> DEFAULT_ALLOWLIST = List.of(
            "libraries.minecraft.net",
            "maven.fabricmc.net",
            "repo.papermc.io",
            "repo.maven.apache.org",
            "maven.neoforged.net",
            "files.minecraftforge.net",
            "cdn.modrinth.com",
            "api.modrinth.com");

    public SandboxNetworkPolicy {
        if (networkName == null || networkName.isBlank()) {
            throw new IllegalArgumentException("网络名不能为空");
        }
        allowlist = allowlist == null ? DEFAULT_ALLOWLIST : List.copyOf(allowlist);
    }

    /** 每轮用例一个独立网络：名字带用例 id，默认拒绝出站，白名单取默认集。 */
    public static SandboxNetworkPolicy isolate(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("用例 id 不能为空");
        }
        return new SandboxNetworkPolicy(NETWORK_PREFIX + caseId, true, DEFAULT_ALLOWLIST);
    }

    public static List<String> defaultAllowlist() {
        return DEFAULT_ALLOWLIST;
    }

    /** 白名单串成一行，供 --label 消费（label 值不能带换行）。 */
    public String allowlistLabel() {
        return String.join(",", allowlist);
    }

    public boolean allows(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return allowlist.stream().anyMatch(entry -> entry.toLowerCase(Locale.ROOT).equals(normalized));
    }
}
