package com.xuejiai.aaf.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket 容器级配置。
 *
 * <p>各业务模块的 WebSocket 端点（通知、对话、AIGC 语音/多模态等）随模块自治，各自在自己的
 * {@code config} 包下实现 {@code WebSocketConfigurer} 注册端点，不在此集中注册，避免全局配置反向依赖业务模块。
 */
@Configuration
public class WebSocketConfig {

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
