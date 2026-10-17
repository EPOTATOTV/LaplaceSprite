package io.laplace.sprite.adapters.ecosystem;

import io.laplace.sprite.core.model.PlatformFamily;

import java.util.ArrayList;
import java.util.List;

/**
 * 必装基线包（14.9「客户端性能/UI 基线四件套」）。
 *
 * <p>每一格先注入基线包，再装被测目标；基线包本身出问题就说明不是被测目标的锅，
 * 因此它同时也是失败归因的对照格。</p>
 */
public final class BaselineBundle {

    public static final String FABRIC_API = "fabric-api";
    public static final String JEI = "jei";
    public static final String SODIUM = "sodium";
    public static final String LITHIUM = "lithium";
    public static final String IRIS = "iris";

    /**
     * 一条基线模组。
     *
     * @param id        模组标识（同时也是 Modrinth 项目 slug）
     * @param coordinate 产物坐标
     * @param note      为什么要装在基线里
     */
    public record BaselineMod(String id, String coordinate, String note) {
    }

    private final PlatformFamily family;
    private final List<BaselineMod> mods;

    private BaselineBundle(PlatformFamily family, List<BaselineMod> mods) {
        this.family = family;
        this.mods = List.copyOf(mods);
    }

    /**
     * 服务端格子的基线包。
     *
     * <p>Fabric 四件套 = Fabric API + JEI + Sodium + Lithium；NeoForge / Forge 格按文档只要
     * JEI + Lithium，渲染管线断言仍在客户端格单独做。</p>
     */
    public static BaselineBundle forLoader(PlatformFamily family) {
        return forLoader(family, false);
    }

    /**
     * @param clientProxyRuntime 客户端代理运行时：Fabric 格追加 Iris，且 Iris 必须与 Sodium 同装
     */
    public static BaselineBundle forLoader(PlatformFamily family, boolean clientProxyRuntime) {
        PlatformFamily loader = LoaderSupport.requireModLoader(family);
        List<BaselineMod> mods = new ArrayList<>();
        if (loader == PlatformFamily.FABRIC) {
            mods.add(new BaselineMod(FABRIC_API, coordinate(FABRIC_API),
                    "Fabric 生态的公共 API 与事件总线，绝大多数 Fabric 模组把它声明为硬依赖"));
        }
        mods.add(new BaselineMod(JEI, coordinate(JEI),
                "配方页能否打开是第二项冒烟的观测点，物品注册数也从 JEI 的注册表读取"));
        if (loader == PlatformFamily.FABRIC) {
            mods.add(new BaselineMod(SODIUM, coordinate(SODIUM),
                    "替换区块渲染管线，第三项冒烟要确认管线确实由它接管"));
        }
        mods.add(new BaselineMod(LITHIUM, coordinate(LITHIUM),
                "tick 优化模组，用来确认服务端侧行为与空包基线一致"));
        if (loader == PlatformFamily.FABRIC && clientProxyRuntime) {
            mods.add(new BaselineMod(IRIS, coordinate(IRIS),
                    "光影加载器强依赖 Sodium，只在客户端代理运行时注入；单独装 Iris 属于非法组合"));
        }
        return new BaselineBundle(loader, mods);
    }

    public PlatformFamily family() {
        return family;
    }

    public List<BaselineMod> mods() {
        return mods;
    }

    public List<String> projectIds() {
        return mods.stream().map(BaselineMod::id).toList();
    }

    public boolean includes(String id) {
        return mods.stream().anyMatch(mod -> mod.id().equals(id));
    }

    private static String coordinate(String projectId) {
        return "modrinth:" + projectId;
    }
}
