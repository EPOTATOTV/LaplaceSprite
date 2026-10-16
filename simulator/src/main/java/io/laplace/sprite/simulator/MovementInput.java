package io.laplace.sprite.simulator;

/**
 * 一个 tick 的控制输入：行为模型只表达"想怎么动"，不直接给位移。
 *
 * @param mode              运动介质
 * @param desiredSpeed      期望稳态水平速度（格/tick）
 * @param sprinting         是否按疾跑键（原版移动输入 ×1.3）
 * @param sneaking          是否潜行（原版移动输入 ×0.3）
 * @param jumpImpulse       本 tick 的竖直冲量（格/tick），0 表示不起跳；只有落地时才被采纳
 * @param horizontalImpulse 本 tick 沿视线的水平冲量（格/tick），0 表示无；击退与珍珠推进用它
 */
record MovementInput(MotionMode mode, double desiredSpeed, boolean sprinting, boolean sneaking,
                     double jumpImpulse, double horizontalImpulse) {
}
