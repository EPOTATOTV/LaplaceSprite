package io.laplace.sprite.sandbox;

/**
 * 沙箱测试等级（15.4 一次性容器）：内存 / CPU 配额与 OOM 重启上限按等级取预设值。
 */
public enum SandboxTier {

    /** 冒烟：单用例、小世界，只验证能启动能进服。 */
    SMOKE(1024, 1.0, 1),
    /** 标准：默认等级，覆盖常规场景。 */
    STANDARD(3072, 2.0, 2),
    /** 重载：压测与大规模世界，配额放宽、退避重启名额给足。 */
    HEAVY(8192, 4.0, 3);

    private final int memoryMb;
    private final double cpus;
    private final int oomRestartLimit;

    SandboxTier(int memoryMb, double cpus, int oomRestartLimit) {
        this.memoryMb = memoryMb;
        this.cpus = cpus;
        this.oomRestartLimit = oomRestartLimit;
    }

    /** 容器内存上限（docker --memory）。 */
    public int memoryMb() {
        return memoryMb;
    }

    /** CPU 配额（docker --cpus）。 */
    public double cpus() {
        return cpus;
    }

    /** OOM 指数退避重启的次数上限。 */
    public int oomRestartLimit() {
        return oomRestartLimit;
    }
}
