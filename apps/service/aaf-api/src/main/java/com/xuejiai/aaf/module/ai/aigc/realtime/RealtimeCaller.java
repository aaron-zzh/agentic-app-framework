package com.xuejiai.aaf.module.ai.aigc.realtime;

import java.util.Objects;

import com.xuejiai.aaf.module.device.api.RealtimeDeviceContext;

/** WebSocket transport 已认证后交给实时会话的可信调用者。 */
public sealed interface RealtimeCaller permits RealtimeCaller.Web, RealtimeCaller.Device {

    /** 浏览器 JWT 已验证的用户。 */
    record Web(Long userId) implements RealtimeCaller {
        public Web {
            Objects.requireNonNull(userId, "userId 不能为空");
        }
    }

    /** 设备模块已重新校验并脱敏后的设备上下文，不携带 DevicePrincipal。 */
    record Device(RealtimeDeviceContext context) implements RealtimeCaller {
        public Device {
            Objects.requireNonNull(context, "context 不能为空");
        }
    }
}
