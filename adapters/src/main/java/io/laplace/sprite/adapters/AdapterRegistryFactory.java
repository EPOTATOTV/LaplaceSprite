package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.bedrock.AllayAdapter;
import io.laplace.sprite.adapters.bedrock.GeyserAdapter;
import io.laplace.sprite.adapters.bedrock.NukkitAdapter;
import io.laplace.sprite.adapters.bedrock.PowerNukkitXAdapter;
import io.laplace.sprite.adapters.bukkit.BukkitAdapter;
import io.laplace.sprite.adapters.bukkit.FoliaAdapter;
import io.laplace.sprite.adapters.bukkit.PaperAdapter;
import io.laplace.sprite.adapters.bukkit.PufferfishAdapter;
import io.laplace.sprite.adapters.bukkit.PurpurAdapter;
import io.laplace.sprite.adapters.bukkit.SpigotAdapter;
import io.laplace.sprite.adapters.hybrid.ArclightAdapter;
import io.laplace.sprite.adapters.hybrid.CardboardAdapter;
import io.laplace.sprite.adapters.hybrid.MagmaNeoAdapter;
import io.laplace.sprite.adapters.hybrid.MohistAdapter;
import io.laplace.sprite.adapters.hybrid.YouerAdapter;
import io.laplace.sprite.adapters.modloader.FabricAdapter;
import io.laplace.sprite.adapters.modloader.ForgeAdapter;
import io.laplace.sprite.adapters.modloader.NeoForgeAdapter;
import io.laplace.sprite.adapters.modloader.QuiltAdapter;
import io.laplace.sprite.adapters.proxy.BungeeCordAdapter;
import io.laplace.sprite.adapters.proxy.VelocityAdapter;
import io.laplace.sprite.adapters.proxy.WaterfallAdapter;
import io.laplace.sprite.adapters.sponge.SpongeForgeAdapter;
import io.laplace.sprite.adapters.sponge.SpongeNeoAdapter;
import io.laplace.sprite.adapters.sponge.SpongeVanillaAdapter;
import io.laplace.sprite.core.spi.AdapterProvider;
import io.laplace.sprite.core.spi.AdapterRegistry;
import io.laplace.sprite.core.spi.LaplaceAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 适配器注册表工厂（14.2 / 14.3 / 14.9 全量接入矩阵）。
 *
 * <p>Bukkit 家族 6 + 代理 3 + 基岩 4 + 模组加载器 4 + Sponge 系 3 + 混合端 5，共 25 个一次登记完；
 * 矩阵不匹配时由 {@link AdapterRegistry} 走兼容链回退并输出告警。第三方适配器经
 * {@link AdapterProvider} 注入，id 与内置重复时跳过并记告警，避免静默覆盖。</p>
 */
public final class AdapterRegistryFactory {

    private AdapterRegistryFactory() {
    }

    /** 全部适配器，覆盖 6 类家族。 */
    public static List<AbstractLaplaceAdapter> allAdapters() {
        return List.of(
                new BukkitAdapter(),
                new SpigotAdapter(),
                new PaperAdapter(),
                new PurpurAdapter(),
                new PufferfishAdapter(),
                new FoliaAdapter(),
                new BungeeCordAdapter(),
                new WaterfallAdapter(),
                new VelocityAdapter(),
                new NukkitAdapter(),
                new PowerNukkitXAdapter(),
                new AllayAdapter(),
                new GeyserAdapter(),
                new ForgeAdapter(),
                new NeoForgeAdapter(),
                new FabricAdapter(),
                new QuiltAdapter(),
                new SpongeVanillaAdapter(),
                new SpongeNeoAdapter(),
                new SpongeForgeAdapter(),
                new ArclightAdapter(),
                new YouerAdapter(),
                new MohistAdapter(),
                new MagmaNeoAdapter(),
                new CardboardAdapter());
    }

    public static AdapterRegistry defaultRegistry() {
        AdapterRegistry registry = new AdapterRegistry();
        allAdapters().forEach(registry::register);
        for (LaplaceAdapter adapter : providerAdapters()) {
            if (registry.byId(adapter.id()).isPresent()) {
                registry.warning("第三方适配器 id 与内置重复，已跳过: " + adapter.id());
                continue;
            }
            registry.register(adapter);
        }
        return registry;
    }

    /** ServiceLoader 发现到的第三方适配器（去重前），测试与诊断用。 */
    public static List<LaplaceAdapter> providerAdapters() {
        List<LaplaceAdapter> provided = new ArrayList<>();
        for (AdapterProvider provider : ServiceLoader.load(AdapterProvider.class)) {
            List<LaplaceAdapter> adapters = provider.adapters();
            if (adapters != null) {
                provided.addAll(adapters);
            }
        }
        return List.copyOf(provided);
    }
}
