package com.xuejiai.aaf.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.xuejiai.aaf.module.ai.aigc.omni.ws.OmniRealtimeWebSocketHandler;
import com.xuejiai.aaf.module.ai.aigc.voice.ws.AsrWebSocketHandler;
import com.xuejiai.aaf.module.ai.chat.ws.ChatWebSocketHandler;
import com.xuejiai.aaf.module.system.notify.ws.NotificationWebSocketHandler;

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

    /**
     * 调大 WebSocket 消息缓冲区。
     *
     * <p>Tomcat 默认文本/二进制消息缓冲区仅 8KB，而 Omni/ASR 的音频帧（base64 PCM）单帧可达 ~11KB， 会触发 1009「message too
     * big」直接断连。统一放大到 512KB 以容纳实时音视频分片。
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        var container = new ServletServerContainerFactoryBean();
        int bufferSize = 512 * 1024;
        container.setMaxTextMessageBufferSize(bufferSize);
        container.setMaxBinaryMessageBufferSize(bufferSize);
        return container;
    }
}
