package com.xuejiai.aaf.module.system.voice.ws;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;

/**
 * ASR 双向流式 WebSocket 处理器。
 *
 * <p>协议：
 *
 * <ul>
 *   <li>客户端 → 服务端：binary frame，每帧为一段 PCM/WAV 音频字节
 *   <li>服务端 → 客户端：text frame，JSON {"text":"识别结果","final":true}
 * </ul>
 *
 * <p>连接参数（query string）：{@code lang}，默认 zh-CN
 *
 * <p>端点：{@code /ws/asr}
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsrWebSocketHandler extends BinaryWebSocketHandler {

    private final SpeechService speechService;

    /** 每个 session 对应一个音频 sink */
    private final Map<String, Sinks.Many<byte[]>> sinkMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        var lang = extractParam(session, "lang", "zh-CN");
        Sinks.Many<byte[]> sink = Sinks.many().unicast().onBackpressureBuffer();
        sinkMap.put(session.getId(), sink);

        speechService
                .transcribeStream(sink.asFlux(), lang)
                .subscribe(
                        text -> sendText(session, text),
                        err -> {
                            log.error("ASR 识别错误: sessionId={}", session.getId(), err);
                            closeQuietly(session);
                        },
                        () -> closeQuietly(session));

        log.info("ASR WebSocket 连接建立: sessionId={}, lang={}", session.getId(), lang);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        var sink = sinkMap.get(session.getId());
        if (sink == null) return;
        byte[] chunk = new byte[message.getPayload().remaining()];
        message.getPayload().get(chunk);
        sink.tryEmitNext(chunk);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var sink = sinkMap.remove(session.getId());
        if (sink != null) sink.tryEmitComplete();
        log.info("ASR WebSocket 连接关闭: sessionId={}, status={}", session.getId(), status);
    }

    private void sendText(WebSocketSession session, String text) {
        if (!session.isOpen()) return;
        try {
            var json = "{\"text\":\"" + text.replace("\"", "\\\"") + "\",\"final\":true}";
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("ASR 推送结果失败: sessionId={}", session.getId(), e);
        }
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
