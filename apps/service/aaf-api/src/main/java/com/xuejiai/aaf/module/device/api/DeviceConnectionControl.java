package com.xuejiai.aaf.module.device.api;

/** 设备连接控制端口。解绑或吊销凭据后用于立即断开 Broker 连接。 */
public interface DeviceConnectionControl {

    void disconnect(String deviceKey);
}
