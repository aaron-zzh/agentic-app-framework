package com.xuejiai.aaf.module.device.security;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** 将已由设备安全链认证的设备主体复制到 WebSocket 会话属性，不重解析凭据。 */
@Component
public class DeviceCredentialHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_DEVICE_PRINCIPAL = "devicePrincipal";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof DevicePrincipal principal)) {
            return false;
        }
        attributes.put(ATTR_DEVICE_PRINCIPAL, principal);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {}
}
