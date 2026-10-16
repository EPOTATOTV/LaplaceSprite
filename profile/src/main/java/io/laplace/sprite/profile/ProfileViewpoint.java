package io.laplace.sprite.profile;

/**
 * 剖析视角（15.3）：同一份报告，不同角色先看的东西不一样。
 */
public enum ProfileViewpoint {

    /** 反作弊开发者：先看数据包处理线程与网络 IO，再看服务端 tick。 */
    DETECTION_DEVELOPER,

    /** 模组作者：先看服务端 tick 主线程。 */
    MOD_AUTHOR
}
