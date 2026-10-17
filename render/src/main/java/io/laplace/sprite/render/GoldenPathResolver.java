package io.laplace.sprite.render;

import java.nio.file.Path;
import java.util.Objects;

/**
 * golden 目录布局（15.12 P0/P1）：按 MC 版本 × 渲染后端分目录。
 *
 * <p>后端差异会导致同一场景的像素不同，混在一个目录里会让 diff 门禁永远失败；
 * 光影只做「不崩 + 出图」冒烟，不进像素门禁，所以目录里不加光影维度。</p>
 */
public final class GoldenPathResolver {

    private final Path goldenRoot;

    public GoldenPathResolver(Path goldenRoot) {
        this.goldenRoot = Objects.requireNonNull(goldenRoot, "goldenRoot");
    }

    public Path directory(RenderMatrixCell cell) {
        return goldenRoot.resolve(cell.mcVersion()).resolve(cell.backend().id());
    }

    public Path goldenPng(RenderMatrixCell cell, String caseId) {
        return directory(cell).resolve(sanitize(caseId) + ".png");
    }

    public Path goldenRoot() {
        return goldenRoot;
    }

    private static String sanitize(String caseId) {
        Objects.requireNonNull(caseId, "caseId");
        return caseId.replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
