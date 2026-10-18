package io.laplace.sprite.datapack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * PackTest 约定（升级实施文档 L-02）：数据包内 {@code data/<namespace>/test/*.mcfunction} 即测试用例。
 *
 * <p>PackTest 用原版 GameTest 后端跑这些函数，因此用例统一标 {@link AssertBackend#ASSERT_GAMETEST}，
 * 不再依赖函数回显的日志后缀。套件名取数据包目录名，方便报告回溯到具体包。</p>
 */
public final class PackTestConvention {

    /** 命名空间目录。 */
    public static final String DATA_DIR = "data";
    /** 测试函数目录。 */
    public static final String TEST_DIR = "test";
    /** 函数后缀。 */
    public static final String FUNCTION_SUFFIX = ".mcfunction";

    private final Path packRoot;

    public PackTestConvention(Path packRoot) {
        this.packRoot = packRoot;
    }

    /** 扫描数据包目录，按命名空间与文件名排序生成冒烟套件；没有测试函数时返回空用例套件。 */
    public DatapackSuite scan() {
        String packName = packRoot.getFileName() == null ? "datapack" : packRoot.getFileName().toString();
        Path dataDir = packRoot.resolve(DATA_DIR);
        List<DatapackCase> cases = new ArrayList<>();
        if (!Files.isDirectory(dataDir)) {
            return new DatapackSuite(packName, "PackTest:" + packName, cases);
        }
        try (Stream<Path> namespaces = Files.list(dataDir)) {
            List<Path> sortedNamespaces = namespaces
                    .filter(Files::isDirectory)
                    .sorted()
                    .toList();
            for (Path namespaceDir : sortedNamespaces) {
                cases.addAll(scanNamespace(packName, namespaceDir));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("扫描数据包测试函数失败: " + dataDir, ex);
        }
        return new DatapackSuite(packName, "PackTest:" + packName, cases);
    }

    private static List<DatapackCase> scanNamespace(String packName, Path namespaceDir) throws IOException {
        Path testDir = namespaceDir.resolve(TEST_DIR);
        if (!Files.isDirectory(testDir)) {
            return List.of();
        }
        String namespace = namespaceDir.getFileName().toString();
        try (Stream<Path> functions = Files.list(testDir)) {
            return functions
                    .filter(Files::isRegularFile)
                    .filter(PackTestConvention::isTestFunction)
                    .sorted()
                    .map(path -> toCase(packName, namespace, path))
                    .toList();
        }
    }

    private static DatapackCase toCase(String packName, String namespace, Path path) {
        String fileName = path.getFileName().toString();
        String functionName = fileName.substring(0, fileName.length() - FUNCTION_SUFFIX.length());
        return new DatapackCase(namespace + "." + functionName, namespace, functionName, packName,
                "", AssertBackend.ASSERT_GAMETEST);
    }

    /** 是否为约定中的测试函数文件。 */
    public static boolean isTestFunction(Path path) {
        return path != null && path.getFileName() != null
                && path.getFileName().toString().endsWith(FUNCTION_SUFFIX)
                && path.getParent() != null
                && path.getParent().getFileName() != null
                && TEST_DIR.equals(path.getParent().getFileName().toString());
    }
}
