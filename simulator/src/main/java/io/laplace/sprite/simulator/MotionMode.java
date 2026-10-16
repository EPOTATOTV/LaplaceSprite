package io.laplace.sprite.simulator;

/** 运动介质：决定摩擦/阻力与地面接触规则。 */
enum MotionMode {

    /** 陆地：原版重力与地面摩擦，脚踩地面即 onGround。 */
    GROUND,

    /** 水中：0.8 水平阻力，浮力抵消重力，不产生地面接触。 */
    SWIM,

    /** 鞘翅滑翔：0.99 阻尼叠加俯仰升力，全程离地。 */
    ELYTRA,

    /** 载具（船/坐骑）：掠过水面或地面，不做重力，全程离地。 */
    VEHICLE
}
