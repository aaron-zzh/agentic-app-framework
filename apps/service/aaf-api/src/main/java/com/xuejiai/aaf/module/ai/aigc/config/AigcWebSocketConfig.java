package com.xuejiai.aaf.module.ai.aigc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.xuejiai.aaf.config.JwtHandshakeInterceptor;
import com.xuejiai.aaf.module.ai.aigc.realtime.ws.JwtRealtimeConversationWebSocketHandler;
import com.xuejiai.aaf.module.ai.aigc.realtime.ws.QueryTokenJwtHandshakeInterceptor;
import com.xuejiai.aaf.module.ai.aigc.voice.ws.AsrWebSocketHandler;

import lombok.RequiredArgsConstructor;

/**
 * AIGC 模块 WebSocket 配置，注册语音识别和实时多模态对话端点。
 *
 * <p>与全局 {@code config.WebSocketConfig} 分离：aigc 下所有 WebSocket handler 均在本模块内实现，
 * 注册逻辑随模块自治，避免全局配置类反向依赖业务模块。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class AigcWebSocketConfig implements WebSocketConfigurer {

    private final AsrWebSocketHandler asrHandler;
    private final JwtRealtimeConversationWebSocketHandler realtimeConversationHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final QueryTokenJwtHandshakeInterceptor queryTokenJwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(asrHandler, "/ws/asr")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");
        registry.addHandler(realtimeConversationHandler, "/ws/realtime-conversation")
                .addInterceptors(queryTokenJwtHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
