package com.xuejiai.aaf.module.system.notify.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.xuejiai.aaf.config.JwtHandshakeInterceptor;
import com.xuejiai.aaf.module.system.notify.ws.NotificationWebSocketHandler;

import lombok.RequiredArgsConstructor;

/**
 * 通知模块 WebSocket 配置，注册通知推送端点。
 *
 * <p>随模块自治，不在全局配置类中集中注册，避免全局配置反向依赖业务模块。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class NotificationWebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationHandler, "/ws/notifications")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
