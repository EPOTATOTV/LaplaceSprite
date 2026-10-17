package io.laplace.sprite.adapters.ecosystem;

import java.util.List;

/**
 * 热门插件四件套共存冒烟（14.9）。
 *
 * <p>加载顺序按官方依赖关系固定：Vault → LuckPerms → PlaceholderAPI → EssentialsX。
 * Vault 先提供经济 / 权限桥，LuckPerms 注册权限与 Vault 实现，PlaceholderAPI 再挂扩展，
 * EssentialsX 最后启动，才能读到前三者提供的服务。</p>
 */
public final class TopPluginBundle {

    /** 四件套的官方依赖顺序，测试与报告都按这个字符串比对。 */
    public static final List<String> LOAD_ORDER =
            List.of("Vault", "LuckPerms", "PlaceholderAPI", "EssentialsX");

    /**
     * BOM 里的一条插件。
     *
     * @param id         插件名
     * @param coordinate 产物坐标（适配层 BOM 维护版本）
     * @param loadOrder  从 0 开始的加载位次
     * @param commands   该插件提供的验证命令
     */
    public record PluginArtifact(String id, String coordinate, int loadOrder, List<String> commands) {
    }

    /**
     * 一层断言。
     *
     * @param id         层标识
     * @param title      层名称
     * @param assertions 该层的断言
     */
    public record AssertionLayer(String id, String title, List<BundleAssertion> assertions) {
    }

    private static final List<PluginArtifact> BOM = List.of(
            new PluginArtifact("Vault", "hangar:Vault", 0, List.of("/vault-info")),
            new PluginArtifact("LuckPerms", "hangar:LuckPerms", 1, List.of("/lp user", "/lp group")),
            new PluginArtifact("PlaceholderAPI", "hangar:PlaceholderAPI", 2, List.of("/papi parse")),
            new PluginArtifact("EssentialsX", "hangar:EssentialsX", 3, List.of("/balance", "/spawn")));

    private static final List<AssertionLayer> LAYERS = List.of(
            new AssertionLayer("startup", "启动无冲突", List.of(
                    new BundleAssertion("no-conflicting-plugin", "启动无 ConflictingPlugin"),
                    new BundleAssertion("no-noclassdef", "启动无 NoClassDefFoundError"),
                    new BundleAssertion("no-duplicate-command", "无命令重复注册"))),
            new AssertionLayer("commands", "命令返回码", List.of(
                    new BundleAssertion("balance-command", "/balance 返回码正常"),
                    new BundleAssertion("luckperms-command", "/lp user 返回码正常"),
                    new BundleAssertion("plugins-command", "/pl 返回码正常"),
                    new BundleAssertion("papi-command", "/papi parse 返回码正常"))),
            new AssertionLayer("permission", "权限门控", List.of(
                    new BundleAssertion("spawn-permission",
                            "测试组授予 essentials.spawn 后 /spawn 可用"))),
            new AssertionLayer("placeholders", "占位符解析", List.of(
                    new BundleAssertion("essentials-balance", "%essentials_balance% 真实解析出余额"),
                    new BundleAssertion("luckperms-prefix", "%luckperms_prefix% 真实解析出前缀"))));

    private static final TopPluginBundle INSTANCE = new TopPluginBundle();

    private TopPluginBundle() {
    }

    public static TopPluginBundle bom() {
        return INSTANCE;
    }

    public List<PluginArtifact> artifacts() {
        return BOM;
    }

    public List<String> loadOrder() {
        return LOAD_ORDER;
    }

    public List<AssertionLayer> assertionLayers() {
        return LAYERS;
    }

    public List<BundleAssertion> assertions() {
        return LAYERS.stream().flatMap(layer -> layer.assertions().stream()).toList();
    }

    /** 加载位次必须与 {@link #LOAD_ORDER} 逐位一致。 */
    public boolean loadOrderMatchesBom() {
        for (int i = 0; i < BOM.size(); i++) {
            if (!BOM.get(i).id().equals(LOAD_ORDER.get(i)) || BOM.get(i).loadOrder() != i) {
                return false;
            }
        }
        return BOM.size() == LOAD_ORDER.size();
    }
}
