package io.laplace.sprite.datapack;

import java.util.List;

/**
 * 数据包冒烟套件（15.8）：同一份数据包的一组函数用例。
 *
 * @param id    套件 id
 * @param name  套件名
 * @param cases 用例列表
 */
public record DatapackSuite(String id, String name, List<DatapackCase> cases) {

    public DatapackSuite {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("套件 id 不能为空");
        }
        name = name == null ? "" : name;
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
