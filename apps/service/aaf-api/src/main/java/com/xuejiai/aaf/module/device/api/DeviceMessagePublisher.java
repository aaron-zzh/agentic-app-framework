package com.xuejiai.aaf.module.device.api;

import com.xuejiai.aaf.module.device.domain.DeviceMessage;

/** 可靠设备消息传输端口。实现负责签名、主题解析和 MQTT 发布。 */
public interface DeviceMessagePublisher {

    /** 发布并等待 Broker PUBACK；返回只代表 Broker 已接收，不代表设备已接受。 */
    void publish(DeviceMessage message);
}
