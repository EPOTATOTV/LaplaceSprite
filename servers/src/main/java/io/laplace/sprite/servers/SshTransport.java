package io.laplace.sprite.servers;

/**
 * SSH 传输（12.2）：平台端独立部署后，经 SSH 驱动 8h8g 测试节点的命令通道。
 *
 * <p>可注入：测试用 {@link InMemorySshTransport}，真实环境用 {@link ProcessSshTransport}。未配置或
 * ssh 不可用时 {@link #available()} 为 false，调用方据此降级，绝不伪造执行结果。</p>
 */
public interface SshTransport {

    /** 远程命令执行结果；失败以非 0 exitCode 表达，不抛异常。 */
    record ExecResult(int exitCode, String stdout, String stderr) {

        public boolean success() {
            return exitCode == 0;
        }

        public static ExecResult ok(String stdout) {
            return new ExecResult(0, stdout == null ? "" : stdout, "");
        }

        public static ExecResult failed(String stderr) {
            return new ExecResult(255, "", stderr == null ? "" : stderr);
        }
    }

    ExecResult exec(String command);

    /** 通道是否可用（ssh 存在且已配置）。 */
    default boolean available() {
        return true;
    }

    /** 人类可读说明，不可用时写明原因。 */
    default String describe() {
        return getClass().getSimpleName();
    }
}
