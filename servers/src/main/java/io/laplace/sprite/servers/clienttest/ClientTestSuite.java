package io.laplace.sprite.servers.clienttest;

import java.util.List;

/**
 * 客户端测试套件（15.7）：一组声明式用例。
 *
 * @param id    套件 id
 * @param name  套件名
 * @param cases 用例列表
 */
public record ClientTestSuite(String id, String name, List<ClientTestCase> cases) {

    public ClientTestSuite {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
