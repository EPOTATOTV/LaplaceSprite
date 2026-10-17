package io.laplace.sprite.replay;

/**
 * 某一 tick 的玩家位姿。
 *
 * @param tick      tick
 * @param x         坐标 x
 * @param y         坐标 y
 * @param z         坐标 z
 * @param yaw       偏航角
 * @param pitch     俯仰角
 * @param onGround  是否着地
 */
public record MovementSample(int tick, double x, double y, double z, float yaw, float pitch,
                             boolean onGround) {
}
