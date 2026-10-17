package com.xuejiai.aaf.module.voice.ws;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import com.xuejiai.aaf.module.voice.api.VoiceClient;
import com.xuejiai.aaf.module.voice.session.VoiceSession;

import lombok.extern.slf4j.Slf4j;

/**
 * 语音会话 WebSocket 处理器基类（协议 v3）。设备端与网页端各写一个子类，只负责认证并产出 {@link VoiceClient}，协议与状态机共用 {@link
 * VoiceSession}。
 */
@Slf4j
public abstract class AbstractVoiceSessionHandler extends AbstractWebSocketHandler {

    /** 单次发送耗时上限与发送缓冲上限，超出视为客户端过慢并断开。 */
    private static final int SEND_TIME_LIMIT_MS = 10_000;

    private static final int SEND_BUFFER_LIMIT_BYTES = 2 * 1024 * 1024;

    private final VoiceRuntime runtime;
    private final Map<String, VoiceSession> sessions = new ConcurrentHashMap<>();

    protected AbstractVoiceSessionHandler(VoiceRuntime runtime) {
        this.runtime = runtime;
    }

    /** 根据握手期认证结果产出客户端端口；未认证返回 null，连接将被拒绝。 */
    protected abstract VoiceClient resolveClient(WebSocketSession session);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        VoiceClient client;
        try {
            client = resolveClient(session);
        } catch (RuntimeException exception) {
            log.warn("语音会话认证失败: sessionId={}", session.getId(), exception);
            client = null;
        }
        if (client == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("unauthorized"));
            return;
        }
        var decorated =
                new ConcurrentWebSocketSessionDecorator(
                        session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        var voiceSession =
                new VoiceSession(
                        session.getId(),
                        client,
                        new SpringChannel(decorated),
                        runtime.newActor(),
                        runtime.deps());
        sessions.put(session.getId(), voiceSession);
        runtime.registry().register(voiceSession);
        voiceSession.onOpen();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        var voiceSession = sessions.get(session.getId());
        if (voiceSession != null) {
            voiceSession.onText(message.getPayload());
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        var voiceSession = sessions.get(session.getId());
        if (voiceSession != null) {
            voiceSession.onBinary(message.getPayload());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("语音会话传输错误: sessionId={}, err={}", session.getId(), exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var voiceSession = sessions.remove(session.getId());
        if (voiceSession != null) {
            runtime.registry().unregister(voiceSession);
            voiceSession.onClosed();
        }
    }

    /** 把 Spring 的 WebSocket 会话适配为会话核心的传输通道。 */
    private record SpringChannel(WebSocketSession session) implements VoiceSession.Channel {

        @Override
        public void sendText(String json) {
            send(new TextMessage(json));
        }

        @Override
        public void sendBinary(byte[] frame) {
            send(new BinaryMessage(frame));
        }

        @Override
        public void close(int code, String reason) {
            try {
                if (session.isOpen()) {
                    session.close(new CloseStatus(code, reason));
                }
            } catch (Exception exception) {
                log.debug("语音会话关闭失败: sessionId={}", session.getId());
            }
        }

        private void send(WebSocketMessage<?> message) {
            if (!session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(message);
            } catch (Exception exception) {
                log.debug(
                        "语音会话发送失败: sessionId={}, err={}", session.getId(), exception.getMessage());
            }
        }
    }
}
