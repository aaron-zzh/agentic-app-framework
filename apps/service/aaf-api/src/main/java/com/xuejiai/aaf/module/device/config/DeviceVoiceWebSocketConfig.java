package com.xuejiai.aaf.module.device.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.xuejiai.aaf.module.device.controller.DeviceAsrWebSocketHandler;

/** 注册设备凭据专用的语音识别 WebSocket。 */
@Configuration
@EnableWebSocket
public class DeviceVoiceWebSocketConfig implements WebSocketConfigurer {

    private final DeviceAsrWebSocketHandler asrHandler;

    public DeviceVoiceWebSocketConfig(DeviceAsrWebSocketHandler asrHandler) {
        this.asrHandler = asrHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(asrHandler, "/api/device/voice/asr").setAllowedOrigins("*");
    }
}
