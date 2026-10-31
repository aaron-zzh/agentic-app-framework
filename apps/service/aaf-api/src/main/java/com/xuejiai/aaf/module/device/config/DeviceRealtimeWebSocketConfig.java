package com.xuejiai.aaf.module.device.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.xuejiai.aaf.module.device.realtime.ws.DeviceRealtimeConversationWebSocketHandler;
import com.xuejiai.aaf.module.device.security.DeviceCredentialHandshakeInterceptor;

/** 注册设备凭据专用的 Omni Realtime v1 WebSocket 入口。 */
@Configuration
@EnableWebSocket
public class DeviceRealtimeWebSocketConfig implements WebSocketConfigurer {

    private final DeviceRealtimeConversationWebSocketHandler handler;
    private final DeviceCredentialHandshakeInterceptor credentialHandshakeInterceptor;

    public DeviceRealtimeWebSocketConfig(
            DeviceRealtimeConversationWebSocketHandler handler,
            DeviceCredentialHandshakeInterceptor credentialHandshakeInterceptor) {
        this.handler = handler;
        this.credentialHandshakeInterceptor = credentialHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/device/realtime-conversation")
                .addInterceptors(credentialHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
