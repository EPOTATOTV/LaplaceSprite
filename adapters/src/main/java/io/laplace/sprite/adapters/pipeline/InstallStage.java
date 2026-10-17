package io.laplace.sprite.adapters.pipeline;

/**
 * 装配流水线五步（14.5）。
 *
 * <p>枚举顺序即执行顺序，失败即中止，后面的阶段不再跑：装配问题在装配期暴露，
 * 比留到运行期便宜得多。</p>
 */
public enum InstallStage {

    /** resolve：解析产物坐标与依赖闭包（Maven / Modrinth / CurseForge / packwiz.toml）。 */
    RESOLVE("resolve：ArtifactResolver 解析产物坐标与依赖闭包（Maven / Modrinth / CurseForge / packwiz.toml）"),

    /** layout：决定目标目录（plugins/ · mods/ · datapacks/）与依赖三语义加载顺序。 */
    LAYOUT("layout：InstallLayout 决定目标目录（plugins/ · mods/ · datapacks/）与加载顺序（依赖三语义）"),

    /** install：写入目标目录，校验哈希 / 签名与 EULA 声明。 */
    INSTALL("install：写入目标目录，校验哈希 / 签名与 EULA 声明"),

    /** launch：LifecycleHook 拉起目标平台（真实环境轨走 Docker Compose / 独立 8h8g 服务器）。 */
    LAUNCH("launch：LifecycleHook 拉起目标平台（真实环境轨：Docker Compose 或独立 8h8g 服务器）"),

    /** verify：健康检查（加载成功、端口就绪、日志无 ERROR、无版本降级告警）。 */
    VERIFY("verify：健康检查（加载成功、端口就绪、日志无 ERROR、无版本降级告警）");

    private final String description;

    InstallStage(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
