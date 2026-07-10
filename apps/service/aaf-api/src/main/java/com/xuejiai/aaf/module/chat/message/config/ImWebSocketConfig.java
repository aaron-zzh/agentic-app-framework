package com.xuejiai.aaf.module.chat.message.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.xuejiai.aaf.config.JwtHandshakeInterceptor;
import com.xuejiai.aaf.module.chat.message.ws.ImWebSocketHandler;

import lombok.RequiredArgsConstructor;

/**
 * IM 模块 WebSocket 配置，注册用户间聊天推送端点。
 *
 * <p>随模块自治，不在全局配置类中集中注册，避免全局配置反向依赖业务模块。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ImWebSocketConfig implements WebSocketConfigurer {

    private final ImWebSocketHandler imHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(imHandler, "/ws/im")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
