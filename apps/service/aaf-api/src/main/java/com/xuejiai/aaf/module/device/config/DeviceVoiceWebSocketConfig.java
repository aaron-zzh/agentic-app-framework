package com.xuejiai.aaf.module.device.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.xuejiai.aaf.module.device.controller.DeviceVoiceSessionHandler;

/** 注册设备凭据专用的语音会话 WebSocket（协议 v3）。 */
@Configuration
@EnableWebSocket
public class DeviceVoiceWebSocketConfig implements WebSocketConfigurer {

    private final DeviceVoiceSessionHandler sessionHandler;

    public DeviceVoiceWebSocketConfig(DeviceVoiceSessionHandler sessionHandler) {
        this.sessionHandler = sessionHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(sessionHandler, "/api/device/voice/session").setAllowedOrigins("*");
    }
}
