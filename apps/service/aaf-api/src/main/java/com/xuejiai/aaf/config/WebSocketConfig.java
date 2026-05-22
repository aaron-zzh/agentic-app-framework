package com.xuejiai.aaf.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.xuejiai.aaf.module.system.chat.ws.ChatWebSocketHandler;
import com.xuejiai.aaf.module.system.notify.ws.NotificationWebSocketHandler;
import com.xuejiai.aaf.module.system.voice.ws.AsrWebSocketHandler;

import lombok.RequiredArgsConstructor;

/** WebSocket 配置，注册通知推送、聊天和 ASR 端点。 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationHandler;
    private final ChatWebSocketHandler chatHandler;
    private final AsrWebSocketHandler asrHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationHandler, "/ws/notifications").setAllowedOrigins("*");
        registry.addHandler(chatHandler, "/ws/chat").setAllowedOrigins("*");
        registry.addHandler(asrHandler, "/ws/asr").setAllowedOrigins("*");
    }
}
