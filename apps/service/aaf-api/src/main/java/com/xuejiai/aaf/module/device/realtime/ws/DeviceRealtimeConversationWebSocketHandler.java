package com.xuejiai.aaf.module.device.realtime.ws;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeCaller;
import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeConversationSessionFactory;
import com.xuejiai.aaf.module.ai.aigc.realtime.ws.AbstractRealtimeConversationWebSocketHandler;
import com.xuejiai.aaf.module.device.api.DeviceRealtimeContextClient;
import com.xuejiai.aaf.module.device.api.RealtimeDeviceAuthentication;
import com.xuejiai.aaf.module.device.security.DeviceAuthFilter;
import com.xuejiai.aaf.module.device.security.DeviceCredentialHandshakeInterceptor;
import com.xuejiai.aaf.module.device.security.DevicePrincipal;

/** 设备凭据专用的 Omni Realtime v1 入口，不暴露用户 JWT 或 Assistant 配置。 */
@Component
public class DeviceRealtimeConversationWebSocketHandler
        extends AbstractRealtimeConversationWebSocketHandler {

    private final DeviceRealtimeContextClient deviceContextClient;

    public DeviceRealtimeConversationWebSocketHandler(
            RealtimeConversationSessionFactory sessionFactory,
            DeviceRealtimeContextClient deviceContextClient) {
        super(sessionFactory, Endpoint.DEVICE);
        this.deviceContextClient = deviceContextClient;
    }

    @Override
    protected RealtimeCaller resolveCaller(WebSocketSession session) {
        var value =
                session.getAttributes()
                        .get(DeviceCredentialHandshakeInterceptor.ATTR_DEVICE_PRINCIPAL);
        if (!(value instanceof DevicePrincipal principal)) {
            throw new IllegalArgumentException("设备实时会话缺少可信设备身份");
        }
        var authentication =
                new RealtimeDeviceAuthentication(
                        principal.deviceId(),
                        principal.deviceKey(),
                        principal.ownerUserId(),
                        principal.scenarioKey(),
                        principal.subjectId(),
                        principal.roleKey(),
                        principal.orgId(),
                        principal.workspaceId());
        return new RealtimeCaller.Device(deviceContextClient.resolve(authentication));
    }

    @Override
    protected void validateHandshake(WebSocketSession session) {
        if (!queryNames(session).isEmpty()) {
            throw new IllegalArgumentException("设备入口不接受 query 参数");
        }
        var headers = session.getHandshakeHeaders();
        if (headers.getFirst(DeviceAuthFilter.DEVICE_KEY_HEADER) == null
                || headers.getFirst(DeviceAuthFilter.DEVICE_CREDENTIAL_HEADER) == null
                || headers.getFirst(HttpHeaders.AUTHORIZATION) != null
                || headers.getFirst(HttpHeaders.COOKIE) != null) {
            throw new IllegalArgumentException("设备入口只接受设备凭据头");
        }
    }
}
