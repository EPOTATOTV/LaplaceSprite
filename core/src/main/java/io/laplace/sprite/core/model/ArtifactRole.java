package io.laplace.sprite.core.model;

/**
 * 产物角色，决定装配目录（plugins/ · mods/ · datapacks/）。
 */
public enum ArtifactRole {
    PLUGIN,
    MOD,
    DATAPACK,
    ADDON,
    LIBRARY,
    DETECTION_ENGINE,
    SERVER,
    MODPACK
}
