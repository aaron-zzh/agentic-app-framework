package com.xuejiai.aaf.module.ai.aigc.omni.ws;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.ai.omni.OmniRealtimeService;
import com.xuejiai.aaf.framework.intelligent.ai.omni.OmniRealtimeService.OmniSession;
import com.xuejiai.aaf.framework.intelligent.ai.omni.OmniRealtimeService.SessionConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Omni Realtime 双向 WebSocket 处理器。
 *
 * <p>协议（JSON text frame）：
 *
 * <ul>
 *   <li>客户端 → 服务端：
 *       <ul>
 *         <li>{"type":"audio","data":"base64..."} — 发送音频
 *         <li>{"type":"video","data":"base64..."} — 发送视频帧
 *         <li>{"type":"commit"} — 手动提交
 *         <li>{"type":"create_response"} — 手动触发响应
 *         <li>{"type":"cancel_response"} — 取消响应
 *       </ul>
 *   <li>服务端 → 客户端：
 *       <ul>
 *         <li>{"type":"audio_transcript_delta","text":"..."} — 增量转录
 *         <li>{"type":"audio_delta","audioData":"base64..."} — 增量音频
 *         <li>{"type":"transcript_done","text":"..."} — 转录完成
 *         <li>{"type":"audio_done"} — 音频完成
 *         <li>{"type":"error","text":"..."} — 错误
 *       </ul>
 * </ul>
 *
 * <p>连接参数（query string）：model, voice, vad(true/false), instructions
 *
 * <p>端点：/ws/omni-realtime
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OmniRealtimeWebSocketHandler extends TextWebSocketHandler {

    private final ObjectProvider<OmniRealtimeService> omniRealtimeServiceProvider;

    private final Map<String, OmniSession> sessionMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) {
        var omniRealtimeService = omniRealtimeServiceProvider.getIfAvailable();
        if (omniRealtimeService == null) {
            sendError(wsSession, "Omni Realtime 服务未启用，请配置 spring.ai.dashscope.api-key");
            closeQuietly(wsSession);
            return;
        }

        var model = extractParam(wsSession, "model", null);
        var voice = extractParam(wsSession, "voice", null);
        var vad = !"false".equals(extractParam(wsSession, "vad", "true"));
        var instructions = extractParam(wsSession, "instructions", null);

        var config =
                new SessionConfig(model, voice, vad, true, instructions, List.of("text", "audio"));

        try {
            var omniSession =
                    omniRealtimeService.createSession(
                            config,
                            event -> {
                                sendEvent(wsSession, event);
                            });
            sessionMap.put(wsSession.getId(), omniSession);
            log.info(
                    "Omni Realtime 连接建立: wsSessionId={}, omniSessionId={}",
                    wsSession.getId(),
                    omniSession.getSessionId());
        } catch (Exception e) {
            log.error("Omni Realtime 会话创建失败", e);
            sendError(wsSession, e.getMessage());
            closeQuietly(wsSession);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        var omniSession = sessionMap.get(wsSession.getId());
        if (omniSession == null) return;

        try {
            var node = JsonUtils.readTree(message.getPayload());
            var type = node.has("type") ? node.get("type").asText() : "";

            switch (type) {
                case "audio" -> omniSession.sendAudio(node.get("data").asText());
                case "video" -> omniSession.sendVideo(node.get("data").asText());
                case "commit" -> omniSession.commit();
                case "create_response" -> omniSession.createResponse();
                case "cancel_response" -> omniSession.cancelResponse();
                default -> log.warn("未知消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("处理 Omni 消息失败: sessionId={}", wsSession.getId(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        var omniSession = sessionMap.remove(wsSession.getId());
        if (omniSession != null) {
            omniSession.close();
        }
        log.info("Omni Realtime 连接关闭: sessionId={}, status={}", wsSession.getId(), status);
    }

    private void sendEvent(WebSocketSession wsSession, OmniRealtimeService.OmniEvent event) {
        if (!wsSession.isOpen()) return;
        try {
            var map = new java.util.HashMap<String, Object>();
            map.put("type", event.type());
            if (event.text() != null) map.put("text", event.text());
            if (event.audioData() != null) map.put("audioData", event.audioData());
            var json = JsonUtils.toJsonString(map);
            wsSession.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("推送 Omni 事件失败: sessionId={}", wsSession.getId(), e);
        }
    }

    private void sendError(WebSocketSession wsSession, String message) {
        sendEvent(wsSession, new OmniRealtimeService.OmniEvent("error", message, null, null));
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) session.close();
        } catch (Exception ignored) {
        }
    }

    private String extractParam(WebSocketSession session, String param, String defaultValue) {
        URI uri = session.getUri();
        if (uri == null) return defaultValue;
        var value = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(param);
        return value != null ? value : defaultValue;
    }
}
