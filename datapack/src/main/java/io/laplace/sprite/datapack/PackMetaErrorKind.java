package io.laplace.sprite.datapack;

/**
 * pack.mcmeta 失败类别，与 Mojira 三条高频特征一一对应。
 */
public enum PackMetaErrorKind {

    /** JSON 语法错误：对应 MC-123460 的 JsonSyntaxException / MalformedJsonException。 */
    JSON_SYNTAX,
    /** 找不到 pack.mcmeta：对应 MC-123459 的 Couldn't find pack meta。 */
    MISSING_META,
    /** 含无法解析 JSON 的数据包导致世界加载失败：MC-310240。 */
    WORLD_LOAD_FAILURE,
    /** pack 对象存在但缺 pack_format。 */
    MISSING_PACK_FORMAT,
    /** JSON 能解析，但结构不是合法的 pack.mcmeta。 */
    INVALID_STRUCTURE,
    /** 文件读不出来（权限、编码、压缩包损坏）。 */
    UNREADABLE
}
