package io.laplace.sprite.adapters.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import io.laplace.sprite.core.model.Artifact;

/**
 * 落到目录的写入实现（14.5 install）。
 *
 * <p>只写一份占位内容：平台不做下载器，产物字节由真实轨道的解析器补齐；这里负责
 * 「有东西落到 plugins/ · mods/ · datapacks/ 下，并且摘要可核」。</p>
 *
 * <p>签名只做「声明即落盘」的存在性对齐，不做 PGP / JARSec 真实验签——验签需要公钥环，
 * 属于真实轨道的外部输入。</p>
 */
public final class DirectoryFileSink implements InstallFileSink {

    /** 产物元数据里声明签名的键。 */
    static final String SIGNATURE_KEY = "signature";

    @Override
    public WrittenFile write(Path targetDirectory, Artifact artifact) {
        try {
            Files.createDirectories(targetDirectory);
            String fileName = fileNameOf(artifact);
            byte[] content = "%s:%s".formatted(artifact.id(), artifact.version())
                    .getBytes(StandardCharsets.UTF_8);
            Files.write(targetDirectory.resolve(fileName), content);

            String signature = blankToNull(artifact.metadata().get(SIGNATURE_KEY));
            if (signature != null) {
                Files.writeString(targetDirectory.resolve(fileName + ".sig"), signature);
            }
            return new WrittenFile(fileName, content.length, sha512(content), signature);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入装配目录失败: " + targetDirectory, ex);
        }
    }

    @Override
    public boolean hasEulaDeclaration(Path workingRoot) {
        // itzg/minecraft-server 系镜像用 EULA=TRUE 环境变量代替工作根下的 eula.txt。
        return Files.exists(workingRoot.resolve("eula.txt"))
                || "TRUE".equalsIgnoreCase(System.getenv("EULA"));
    }

    private static String fileNameOf(Artifact artifact) {
        int colon = artifact.id().lastIndexOf(':');
        String tail = colon < 0 ? artifact.id() : artifact.id().substring(colon + 1);
        return tail + "-" + artifact.version() + ".jar";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String sha512(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("运行时不支持 SHA-512", ex);
        }
    }
}
