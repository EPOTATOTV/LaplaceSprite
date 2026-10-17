package io.laplace.sprite.core.model;

/**
 * 被测目标的 12 种类型（13.6 全类型模组测试方法矩阵 / 9 验收 11）。
 *
 * <p>检测引擎只是其中一行的被测对象，不是默认或唯一的行；每类目标有自己的测试重点、
 * 工具链与典型风险。</p>
 */
public enum TargetType {

    CONTENT("内容型模组（新增物品/生物/维度/玩法）"),
    TECH_AUTOMATION("科技/自动化模组"),
    OPTIMIZATION_RENDER("优化/渲染类模组"),
    CLIENT_QOL("客户端增强/QoL模组"),
    SERVER_PLUGIN("服务端专用模组/插件"),
    CROSS_LOADER_LIBRARY("跨加载器库/Architectury 多平台"),
    RESOURCE_PACK_SHADER("资源包与光影"),
    DATAPACK("数据包"),
    MODPACK("整合包"),
    BEDROCK_ADDON("基岩版 Addon"),
    NETWORK_MULTIPLAYER("网络/多人模组"),
    WORLDGEN("世界生成类模组");

    private final String displayName;

    TargetType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
