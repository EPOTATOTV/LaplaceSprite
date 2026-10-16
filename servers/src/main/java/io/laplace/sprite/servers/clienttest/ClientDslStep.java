package io.laplace.sprite.servers.clienttest;

/**
 * 声明式用例的一步（15.7）。
 *
 * @param op    步骤类型
 * @param value 参数；{@link ClientDslOp#WAIT_FOR_RENDER} / {@link ClientDslOp#SCREENSHOT} 不需要参数
 * @param note  给非代码用户的说明
 */
public record ClientDslStep(ClientDslOp op, String value, String note) {

    public ClientDslStep {
        if (op == null) {
            throw new IllegalArgumentException("步骤类型 op 不能为空");
        }
    }

    public static ClientDslStep of(ClientDslOp op, String value) {
        return new ClientDslStep(op, value, null);
    }

    /** 失败信息里用的一行描述。 */
    public String describe() {
        return value == null || value.isBlank() ? op.name() : op.name() + " " + value;
    }
}
