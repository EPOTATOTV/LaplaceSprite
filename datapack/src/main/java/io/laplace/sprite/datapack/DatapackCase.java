package io.laplace.sprite.datapack;

/**
 * 数据包冒烟用例（15.8）。
 *
 * @param id                用例 id
 * @param namespace         函数所在命名空间
 * @param functionName      函数名（不含 .mcfunction 后缀）
 * @param packName          数据包名，用于在 {@code /datapack list enabled} 里认领
 * @param expectedLogSuffix ENDS_WITH 断言的期望后缀
 * @param assertBackend     断言后端；为空时按 ENDS_WITH
 */
public record DatapackCase(String id, String namespace, String functionName, String packName,
                           String expectedLogSuffix, AssertBackend assertBackend) {

    public DatapackCase {
        requireText(id, "id");
        requireText(namespace, "namespace");
        requireText(functionName, "functionName");
        requireText(packName, "packName");
        expectedLogSuffix = expectedLogSuffix == null ? "" : expectedLogSuffix;
        assertBackend = assertBackend == null ? AssertBackend.ENDS_WITH : assertBackend;
    }

    /** 命令里用的 {@code namespace:function} 引用。 */
    public String functionReference() {
        return namespace + ":" + functionName;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
