package com.xuejiai.aaf.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.xuejiai.aaf.module.ai.chat.ws.ChatWebSocketHandler;
import com.xuejiai.aaf.module.system.notify.ws.NotificationWebSocketHandler;
import com.xuejiai.aaf.module.ai.aigc.voice.ws.AsrWebSocketHandler;
import com.xuejiai.aaf.module.ai.aigc.omni.ws.OmniRealtimeWebSocketHandler;

import lombok.RequiredArgsConstructor;

/** WebSocket 配置，注册通知推送、聊天和 ASR 端点。所有端点统一 JWT 握手认证。 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationHandler;
    private final ChatWebSocketHandler chatHandler;
    private final AsrWebSocketHandler asrHandler;
    private final OmniRealtimeWebSocketHandler omniRealtimeHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationHandler, "/ws/notifications")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");
        registry.addHandler(chatHandler, "/ws/chat")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");
        registry.addHandler(asrHandler, "/ws/asr")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");
        registry.addHandler(omniRealtimeHandler, "/ws/omni-realtime")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
