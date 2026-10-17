/**
 * 安全守护子域：维护预警事件与响应记录、用药提醒闭环。
 *
 * <p>设备配对、认证、MQTT 通道与语音网关属于通用设备层，见 {@code com.xuejiai.aaf.module.device}；本子域通过其 {@code api}
 * 与场景扩展点接入， 并通过长者中心公开的 {@code api} 获取档案和用药数据，不直接访问其实体与仓储。
 */
package com.xuejiai.aaf.module.elder.guard;
