package io.laplace.sprite.core.model;

import java.util.List;

/**
 * 全类型模组测试方法矩阵（13.6 / 9 验收 11）。
 *
 * <p>12 类目标各有一行画像，覆盖测试重点、框架与工具、典型风险、代表项目与真实来源。
 * 「检测引擎」不是默认行，也不是唯一行；矩阵的目的正是让不同目标按各自的方法测试。</p>
 */
public final class TargetTypeMatrix {

    private static final List<TargetTypeProfile> PROFILES = List.of(
            new TargetTypeProfile(TargetType.CONTENT,
                    List.of("注册表条目可加载且无 ID 冲突", "物品 / 方块 / 生物属性、掉落与交互",
                            "维度传送与存档读写的向后兼容", "玩法主流程的 GameTest 断言"),
                    List.of("Fabric GameTest API（Fabric Loom 的 runGameTest）",
                            "原版 /test 命令与 GameTest 结构", "spark 观察实体与区块 tick 开销"),
                    List.of("注册表 ID 冲突或跨版本漂移", "NBT 结构变更导致旧存档读不出",
                            "生物 AI 或维度 tick 造成 MSPT 尖峰"),
                    List.of("Cobblemon", "Create"),
                    List.of("https://gitlab.com/cable-mc/cobblemon",
                            "https://github.com/Creators-of-Create/Create",
                            "https://docs.fabricmc.net/1.21.1/develop/automatic-testing")),

            new TargetTypeProfile(TargetType.TECH_AUTOMATION,
                    List.of("方块实体 tick 与能量 / 物流网络状态", "大规模机器阵列下的 TPS / MSPT",
                            "合成表与配方同步", "区块卸载后机器状态回收"),
                    List.of("spark（tick 热点与分配采样）", "JFR（对象分配与锁竞争）",
                            "GameTest 验证配方与机器产出"),
                    List.of("方块实体或区块卸载不回收导致内存泄漏", "配方 ID 冲突覆盖其他模组",
                            "高频 tick 把 MSPT 推过 50ms", "网络包风暴拖慢主线程"),
                    List.of("Create", "Mekanism"),
                    List.of("https://github.com/Creators-of-Create/Create",
                            "https://github.com/mekanism/Mekanism",
                            "https://github.com/lucko/spark")),

            new TargetTypeProfile(TargetType.OPTIMIZATION_RENDER,
                    List.of("替换渲染管线后行为等价", "帧率 / 帧时间相对基线不劣化",
                            "光影与资源包组合可加载", "不同 GPU 与驱动下不崩溃"),
                    List.of("spark（服务端 tick 与客户端帧耗时）", "JFR",
                            "Sodium 自带调试 HUD 与帧时间采样",
                            "原版 / sodium 对照组的 A/B 运行"),
                    List.of("mixin 注入点与其他优化模组冲突", "渲染线程与逻辑线程竞争",
                            "驱动差异导致画面错误但无异常", "优化改变可见性影响检测判定"),
                    List.of("Sodium", "Lithium"),
                    List.of("https://github.com/CaffeineMC/sodium",
                            "https://github.com/CaffeineMC/lithium",
                            "https://github.com/lucko/spark")),

            new TargetTypeProfile(TargetType.CLIENT_QOL,
                    List.of("客户端 UI、按键绑定与配置持久化", "与服务端模组 / 原版协议的兼容",
                            "单人、多人、代理环境切换", "分辨率与无障碍适配"),
                    List.of("Fabric / NeoForge 客户端 GameTest", "Loom runClient 手动与脚本化检查",
                            "Mod Menu 与 Cloth Config 集成验证"),
                    List.of("客户端崩溃或黑屏", "客户端与服务端模组版本不匹配",
                            "配置迁移丢失", "GUI 缩放或模组列表加载顺序异常"),
                    List.of("JEI", "Jade", "Xaero's Minimap", "Mod Menu", "Cloth Config"),
                    List.of("https://github.com/mezz/JustEnoughItems",
                            "https://github.com/Snownee/Jade",
                            "https://www.curseforge.com/minecraft/mc-mods/xaeros-minimap",
                            "https://github.com/TerraformersMC/ModMenu",
                            "https://github.com/shedaniel/cloth-config")),

            new TargetTypeProfile(TargetType.SERVER_PLUGIN,
                    List.of("命令、权限与事件监听语义", "插件启停、重载与多实例部署",
                            "代理转发与跨服状态一致", "并发玩家下的状态不丢失"),
                    List.of("MockBukkit（单元测试）", "WatchWolf（Spigot 集成测试）",
                            "Plugwright（Paper 插件端到端无头客户端）", "spark（主线程热点）"),
                    List.of("主线程阻塞或同步 IO", "事件优先级与取消语义错误",
                            "跨版本 Bukkit API 差异", "监听器未注销造成内存泄漏"),
                    List.of("MockBukkit", "WatchWolf", "Plugwright", "spark"),
                    List.of("https://github.com/MockBukkit/MockBukkit",
                            "https://github.com/rogermiranda1000/WatchWolf-Tester",
                            "https://github.com/Drownek/plugwright",
                            "https://github.com/lucko/spark")),

            new TargetTypeProfile(TargetType.CROSS_LOADER_LIBRARY,
                    List.of("同一 API 在 Fabric / Forge / NeoForge / Quilt 上行为一致",
                            "版本范围与传递依赖解析", "平台适配层无缺失", "下游模组装配后可启动"),
                    List.of("Architectury API / Architectury Loom", "多加载器构建矩阵",
                            "各加载器各自的 GameTest", "Cloth Config 配置层联调"),
                    List.of("映射差异（intermediary / official / mojmap）", "加载器 API 变更",
                            "依赖被降级或重复打包", "平台分支行为漂移"),
                    List.of("Architectury API", "Cloth Config", "TerraBlender"),
                    List.of("https://github.com/architectury/architectury-api",
                            "https://github.com/shedaniel/cloth-config",
                            "https://github.com/Glitchfiend/TerraBlender")),

            new TargetTypeProfile(TargetType.RESOURCE_PACK_SHADER,
                    List.of("pack.mcmeta 的 pack_format 与版本匹配", "纹理、模型、动画路径可解析",
                            "光影与渲染模组组合", "加载失败时回退到原版资源"),
                    List.of("Blockbench（模型与动画校验）", "原版资源包加载日志",
                            "Iris + Sodium 光影矩阵", "资源包覆盖顺序与 A/B 截图对比"),
                    List.of("pack_format 不匹配被静默忽略", "纹理图集溢出或内存暴涨",
                            "光影与 Sodium / Iris 版本冲突", "动画帧定义错误导致实体不可见"),
                    List.of("Fresh Animations", "Complementary Shaders", "Blockbench"),
                    List.of("https://www.curseforge.com/minecraft/texture-packs/fresh-animations",
                            "https://www.complementary.dev/shaders/",
                            "https://github.com/JannisX11/blockbench")),

            new TargetTypeProfile(TargetType.DATAPACK,
                    List.of("pack_format 与 data 命名空间正确", "函数、进度、战利品表、结构可加载",
                            "/reload 幂等且不重复注册", "GameTest 驱动数据包行为"),
                    List.of("PackTest（数据包内写 GameTest）", "原版 /test 与 GameTest 结构",
                            "官方 pack format 对照表"),
                    List.of("pack_format 跨版本失效", "函数递归或每 tick 执行造成卡顿",
                            "标签覆盖导致其他数据包失效", "结构生成越界或死循环"),
                    List.of("Dungeons and Taverns", "Terralith", "PackTest"),
                    List.of("https://github.com/misode/packtest",
                            "https://modrinth.com/datapack/dungeons-and-taverns",
                            "https://modrinth.com/datapack/terralith")),

            new TargetTypeProfile(TargetType.MODPACK,
                    List.of("依赖解析与版本锁定可复现", "启动时间、内存与包体大小",
                            "模组组合冲突与默认配置", "客户端 / 服务端清单一致性"),
                    List.of("packwiz（依赖与版本清单）", "Prism / MultiMC 实例导出",
                            "spark 启动与 tick 分析", "干净实例的重复装配"),
                    List.of("传递依赖冲突或重复模组", "客户端专用模组混进服务端",
                            "默认配置导致启动崩溃", "版本漂移使问题无法复现"),
                    List.of("Fabulously Optimized", "packwiz"),
                    List.of("https://github.com/Fabulously-Optimized/fabulously-optimized",
                            "https://github.com/packwiz/packwiz")),

            new TargetTypeProfile(TargetType.BEDROCK_ADDON,
                    List.of("manifest UUID、版本与 min_engine_version", "行为包 / 资源包配对",
                            "实体、方块、物品组件 schema", "与 Java 跨服（Geyser）行为一致"),
                    List.of("bridge. v2（清单与脚本构建）", "Blockbench（基岩模型与动画）",
                            "Minecraft Creator 的 Content Log", "Geyser + Floodgate 跨服联调"),
                    List.of("manifest 版本不匹配导致包不加载", "组件 schema 跨版本变更",
                            "行为包与资源包 UUID 冲突", "跨版本 API 差异造成脚本报错"),
                    List.of("bridge.", "Blockbench", "Geyser"),
                    List.of("https://github.com/bridge-core/bridge.",
                            "https://github.com/JannisX11/blockbench",
                            "https://github.com/GeyserMC/Geyser")),

            new TargetTypeProfile(TargetType.NETWORK_MULTIPLAYER,
                    List.of("协议版本、握手与登录流程", "代理转发（Velocity / BungeeCord）",
                            "语音或自定义通道", "延迟、丢包与重排下的状态一致",
                            "Java 与基岩跨服"),
                    List.of("NetworkModel 网络条件注入", "Geyser / Floodgate 跨服",
                            "Simple Voice Chat 自定义通道联调", "代理端与后端的多跳测试"),
                    List.of("协议版本不兼容", "代理转发丢失玩家信息",
                            "UDP 端口或防火墙未放行", "跨版本包结构变化导致解码失败"),
                    List.of("Simple Voice Chat", "Geyser", "Floodgate"),
                    List.of("https://github.com/henkelmax/simple-voice-chat",
                            "https://github.com/GeyserMC/Geyser",
                            "https://github.com/GeyserMC/Floodgate")),

            new TargetTypeProfile(TargetType.WORLDGEN,
                    List.of("生物群系 / 结构注册与 seed 可复现", "区块生成耗时分位",
                            "多模组 biome 与结构冲突", "结构与维度数据包兼容"),
                    List.of("spark（区块生成与 tick 热点）", "TerraBlender（biome 注入）",
                            "Chunky（区块预生成）", "固定 seed 的生成结果回归"),
                    List.of("世界生成死循环或卡区块", "biome / 结构覆盖冲突",
                            "结构间距越界", "预生成时内存暴涨"),
                    List.of("Terralith", "Biomes O' Plenty", "TerraBlender", "Dungeons and Taverns"),
                    List.of("https://modrinth.com/datapack/terralith",
                            "https://github.com/Glitchfiend/BiomesOPlenty",
                            "https://github.com/Glitchfiend/TerraBlender",
                            "https://github.com/pop4959/Chunky"))
    );

    public List<TargetTypeProfile> profiles() {
        return PROFILES;
    }

    /** 已覆盖的目标类型数；9 验收 11 要求 ≥10，这里覆盖全部 12 类。 */
    public int coveredCount() {
        return PROFILES.size();
    }
}
