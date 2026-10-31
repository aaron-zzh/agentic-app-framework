package com.xuejiai.aaf.module.ai.aigc.realtime.ws;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeCloseReason;
import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeServerEvent;
import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeSessionTransport;

import lombok.extern.slf4j.Slf4j;

/** Spring WebSocket 到 v1 传输端口的适配，保持 JSON 与 PCM 消息发送顺序。 */
@Slf4j
public final class RealtimeWebSocketTransport implements RealtimeSessionTransport {

    private final WebSocketSession session;
    private final AtomicBoolean sendFailed = new AtomicBoolean();
    private volatile Runnable sendFailureHandler;

    public RealtimeWebSocketTransport(WebSocketSession session) {
        this.session = session;
    }

    void setSendFailureHandler(Runnable handler) {
        sendFailureHandler = handler;
        if (sendFailed.get()) {
            handler.run();
        }
    }

    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public void sendEvent(RealtimeServerEvent event) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("type", event.type());
        if (event.data() != null) {
            payload.putAll(event.data());
        }
        send(new TextMessage(JsonUtils.toJsonString(Map.copyOf(payload))));
    }

    @Override
    public void sendAudio(byte[] pcm24Le) {
        send(new BinaryMessage(pcm24Le));
    }

    @Override
    public void close(RealtimeCloseReason reason) {
        try {
            if (session.isOpen()) {
                session.close(closeStatus(reason));
            }
        } catch (Exception exception) {
            log.debug(
                    "Omni Realtime transport 关闭失败: sessionId={}, reason={}",
                    session.getId(),
                    exception.getClass().getSimpleName());
        }
    }

    private void send(WebSocketMessage<?> message) {
        if (!session.isOpen()) {
            handleSendFailure(message, "session_closed");
            return;
        }
        try {
            session.sendMessage(message);
        } catch (Exception exception) {
            handleSendFailure(message, exception.getClass().getSimpleName());
        }
    }

    private void handleSendFailure(WebSocketMessage<?> message, String reason) {
        if (!sendFailed.compareAndSet(false, true)) {
            return;
        }
        log.warn(
                "Omni Realtime transport 发送失败: sessionId={}, messageType={}, reason={}",
                session.getId(),
                message.getClass().getSimpleName(),
                reason);
        close(RealtimeCloseReason.ENGINE_ERROR);
        var handler = sendFailureHandler;
        if (handler != null) {
            handler.run();
        }
    }

    private static CloseStatus closeStatus(RealtimeCloseReason reason) {
        return switch (reason) {
            case NORMAL -> CloseStatus.NORMAL;
            case PROTOCOL_ERROR -> CloseStatus.BAD_DATA;
            case UNAUTHORIZED, CONTEXT_REJECTED -> CloseStatus.POLICY_VIOLATION;
            case ENGINE_ERROR -> CloseStatus.SERVER_ERROR;
        };
    }
}
