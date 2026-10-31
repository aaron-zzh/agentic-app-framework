package com.xuejiai.aaf.module.device.api;

/** 实时会话核心访问设备模块的唯一 API 边界。 */
public interface DeviceRealtimeContextClient {

    /** 重新验证设备绑定、场景启用与场景主体，并生成受控上下文。 */
    RealtimeDeviceContext resolve(RealtimeDeviceAuthentication authentication);

    /** 仅由已解析的上下文打开场景工具范围，调用方必须在会话关闭时释放。 */
    AutoCloseable openToolScope(RealtimeDeviceContext context);
}
