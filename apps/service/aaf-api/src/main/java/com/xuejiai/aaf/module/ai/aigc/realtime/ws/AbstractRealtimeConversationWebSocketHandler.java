package com.xuejiai.aaf.module.ai.aigc.realtime.ws;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeCaller;
import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeClientControl;
import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeCloseReason;
import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeConversationSession;
import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeConversationSessionFactory;
import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeHello;
import com.xuejiai.aaf.module.ai.aigc.realtime.RealtimeServerEvent;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/** Omni Realtime v1 的公共 WebSocket transport，子类只负责认证调用者。 */
@Slf4j
public abstract class AbstractRealtimeConversationWebSocketHandler extends TextWebSocketHandler {

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 2 * 1024 * 1024;
    private static final int MAX_IMAGE_BASE64_LENGTH = 14 * 1024 * 1024;

    private final RealtimeConversationSessionFactory sessionFactory;
    private final Endpoint endpoint;
    private final Map<String, RealtimeWebSocketTransport> transports = new ConcurrentHashMap<>();
    private final Map<String, RealtimeConversationSession> sessions = new ConcurrentHashMap<>();

    protected AbstractRealtimeConversationWebSocketHandler(
            RealtimeConversationSessionFactory sessionFactory, Endpoint endpoint) {
        this.sessionFactory = sessionFactory;
        this.endpoint = endpoint;
    }

    /** 从本入口已完成的握手认证结果构造可信调用者。 */
    protected abstract RealtimeCaller resolveCaller(WebSocketSession session);

    /** 在连接建立时拒绝本入口不允许的 query/header/cookie。 */
    protected abstract void validateHandshake(WebSocketSession session);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        var decorated =
                new ConcurrentWebSocketSessionDecorator(
                        session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        var transport = new RealtimeWebSocketTransport(decorated);
        try {
            validateHandshake(session);
            transports.put(session.getId(), transport);
        } catch (IllegalArgumentException exception) {
            sendFatalError(transport, "PROTOCOL_ERROR", "握手参数无效");
            transport.close(RealtimeCloseReason.PROTOCOL_ERROR);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        var transport = transports.get(session.getId());
        if (transport == null) {
            return;
        }
        try {
            var node = JsonUtils.readTreeStrict(message.getPayload());
            if (!node.isObject()) {
                throw new IllegalArgumentException("控制帧必须为 JSON 对象");
            }
            var active = sessions.get(session.getId());
            if (active == null) {
                var hello = parseHello(node);
                var caller = resolveCaller(session);
                var created = sessionFactory.open(caller, hello, transport);
                if (sessions.putIfAbsent(session.getId(), created) != null) {
                    created.close();
                    throw new IllegalArgumentException("hello 只能发送一次");
                }
                transport.setSendFailureHandler(() -> closeSession(session.getId()));
                return;
            }
            active.receiveControl(parseControl(node));
        } catch (IllegalArgumentException exception) {
            log.warn("Omni Realtime hello/control 解析失败: {}", exception.getMessage(), exception);
            if (sessions.containsKey(session.getId())) {
                sessions.get(session.getId()).close();
            }
            sendFatalError(transport, "PROTOCOL_ERROR", "协议帧无效");
            transport.close(RealtimeCloseReason.PROTOCOL_ERROR);
        } catch (RuntimeException exception) {
            sendFatalError(transport, "CONTEXT_REJECTED", "实时会话初始化失败");
            transport.close(RealtimeCloseReason.CONTEXT_REJECTED);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        var active = sessions.get(session.getId());
        var transport = transports.get(session.getId());
        if (active == null || transport == null) {
            if (transport != null) {
                sendFatalError(transport, "INPUT_NOT_READY", "必须先发送 hello 并等待 ready");
                transport.close(RealtimeCloseReason.PROTOCOL_ERROR);
            }
            return;
        }
        active.receiveAudio(copy(message.getPayload()));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        closeSession(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        transports.remove(session.getId());
        closeSession(session.getId());
    }

    private RealtimeHello parseHello(JsonNode node) {
        requireExactFields(
                node,
                endpoint == Endpoint.WEB
                        ? Set.of(
                                "type",
                                "protocol",
                                "endpointing",
                                "roleKey",
                                "knowledge",
                                "voice",
                                "temperature")
                        : Set.of("type", "protocol", "endpointing"));
        if (!"hello".equals(requiredString(node, "type"))
                || !"omni-realtime.v1".equals(requiredString(node, "protocol"))) {
            throw new IllegalArgumentException("首帧必须为 omni-realtime.v1 hello");
        }
        var endpointing = requiredString(node, "endpointing");
        var roleKey = optionalString(node, "roleKey");
        var knowledge = optionalBoolean(node, "knowledge");
        var voice = optionalString(node, "voice");
        var temperature = optionalDouble(node, "temperature");
        if (endpoint == Endpoint.WEB && !"server".equals(endpointing)) {
            throw new IllegalArgumentException("浏览器 endpointing 必须为 server");
        }
        if (endpoint == Endpoint.DEVICE
                && (!"client".equals(endpointing)
                        || roleKey != null
                        || knowledge != null
                        || voice != null
                        || temperature != null)) {
            throw new IllegalArgumentException("设备 hello 无效");
        }
        return new RealtimeHello(
                "omni-realtime.v1", endpointing, roleKey, knowledge, voice, temperature);
    }

    private RealtimeClientControl parseControl(JsonNode node) {
        var type = requiredString(node, "type");
        return switch (type) {
            case "input.text" -> {
                requireExactFields(node, Set.of("type", "text"));
                yield new RealtimeClientControl.InputText(requiredString(node, "text"));
            }
            case "input.image" -> {
                requireExactFields(node, Set.of("type", "mimeType", "data"));
                var encoded = requiredString(node, "data");
                if (encoded.length() > MAX_IMAGE_BASE64_LENGTH) {
                    throw new IllegalArgumentException("图片载荷过大");
                }
                try {
                    yield new RealtimeClientControl.InputImage(
                            requiredString(node, "mimeType"), Base64.getDecoder().decode(encoded));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("图片 Base64 无效", exception);
                }
            }
            case "input_audio.commit" -> {
                requireExactFields(node, Set.of("type"));
                yield new RealtimeClientControl.InputAudioCommit();
            }
            case "response.create" -> {
                requireExactFields(node, Set.of("type"));
                yield new RealtimeClientControl.ResponseCreate();
            }
            case "response.cancel" -> {
                requireExactFields(node, Set.of("type"));
                yield new RealtimeClientControl.ResponseCancel();
            }
            case "session.close" -> {
                requireExactFields(node, Set.of("type"));
                yield new RealtimeClientControl.SessionClose();
            }
            default -> throw new IllegalArgumentException("未知控制事件");
        };
    }

    private static String requiredString(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(field + " 必须为非空字符串");
        }
        return value.asString();
    }

    private static String optionalString(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(field + " 必须为非空字符串");
        }
        return value.asString();
    }

    private static Boolean optionalBoolean(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isBoolean()) {
            throw new IllegalArgumentException(field + " 必须为布尔值");
        }
        return value.asBoolean();
    }

    private static Double optionalDouble(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw new IllegalArgumentException(field + " 必须为有限数字");
        }
        return value.asDouble();
    }

    private static void requireExactFields(JsonNode node, Set<String> allowed) {
        var actual = new LinkedHashSet<String>();
        node.propertyNames().forEach(actual::add);
        if (!allowed.containsAll(actual)) {
            throw new IllegalArgumentException("控制帧包含未知字段");
        }
    }

    private static byte[] copy(ByteBuffer payload) {
        var copy = new byte[payload.remaining()];
        payload.slice().get(copy);
        return copy;
    }

    private static void sendFatalError(
            RealtimeWebSocketTransport transport, String code, String message) {
        transport.sendEvent(
                new RealtimeServerEvent(
                        "error", Map.of("code", code, "message", message, "fatal", true)));
    }

    private void closeSession(String sessionId) {
        transports.remove(sessionId);
        var active = sessions.remove(sessionId);
        if (active != null) {
            active.close();
        }
    }

    protected static Set<String> queryNames(WebSocketSession session) {
        URI uri = session.getUri();
        return uri == null
                ? Set.of()
                : Set.copyOf(UriComponentsBuilder.fromUri(uri).build().getQueryParams().keySet());
    }

    protected enum Endpoint {
        WEB,
        DEVICE
    }
}
