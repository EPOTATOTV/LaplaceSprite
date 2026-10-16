package io.laplace.sprite.core.model;

/**
 * USB 设备指纹（4.2 玩家端样本 / MCU VID）。
 */
public record UsbDevice(String vid, String pid, String description, boolean mcu) {
}
