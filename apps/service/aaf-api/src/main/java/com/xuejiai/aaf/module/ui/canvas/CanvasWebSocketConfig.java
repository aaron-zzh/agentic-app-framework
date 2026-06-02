package com.xuejiai.aaf.module.ui.canvas;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import lombok.RequiredArgsConstructor;

/** 画板 WebSocket 端点注册。 */
@Configuration
@RequiredArgsConstructor
public class CanvasWebSocketConfig implements WebSocketConfigurer {

    private final CanvasWebSocketHandler canvasWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(canvasWebSocketHandler, "/ws/canvas/{entitySlug}/{recordId}")
                .setAllowedOrigins("*");
    }
}
