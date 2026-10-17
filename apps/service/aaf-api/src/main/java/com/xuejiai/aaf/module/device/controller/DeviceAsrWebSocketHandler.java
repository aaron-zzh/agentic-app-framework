package com.xuejiai.aaf.module.device.controller;

import static com.xuejiai.aaf.module.device.ErrorCodeConstants.DEVICE_VOICE_GATEWAY_FAILED;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.device.security.DevicePrincipal;
import com.xuejiai.aaf.module.device.service.DeviceVoiceGatewayService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/** 设备凭据专用的 PCM 流式语音识别 WebSocket。 */
@Slf4j
@Component
public class DeviceAsrWebSocketHandler extends BinaryWebSocketHandler {

    private static final int PCM_BYTES_PER_SECOND = 32_000;
    private static final long USAGE_WAIT_TIMEOUT_MS = 2_000L;

    private final DeviceVoiceGatewayService gatewayService;
    private final Map<String, DevicePrincipal> principals = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<byte[]>> sinks = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> receivedBytes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> durationSeconds = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> settled = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> usageReady = new ConcurrentHashMap<>();

    public DeviceAsrWebSocketHandler(DeviceVoiceGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        var principal = devicePrincipal(session);
        if (principal == null) {
            sendErrorAndClose(session, 401, "设备未认证");
            return;
        }
        try {
            gatewayService.precheckAsr(principal);
        } catch (BusinessException exception) {
            sendErrorAndClose(session, exception.getCode(), exception.getMessage());
            return;
        }

        principals.put(session.getId(), principal);
        receivedBytes.put(session.getId(), new AtomicLong());
        durationSeconds.put(session.getId(), new AtomicInteger());
        settled.put(session.getId(), new AtomicBoolean());
        usageReady.put(session.getId(), new CountDownLatch(1));
        var sink = Sinks.many().unicast().<byte[]>onBackpressureBuffer();
        sinks.put(session.getId(), sink);

        gatewayService
                .transcribe(principal, sink.asFlux(), queryParam(session, "lang", "zh-CN"))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        result -> {
                            if (result.hasUsage()) {
                                aggregateUsage(session.getId(), result.usage().duration());
                            } else if (result.text() != null && !result.text().isBlank()) {
                                sendText(session, result.text());
                            }
                        },
                        failure -> {
                            log.error(
                                    "设备 ASR 识别失败: deviceId={}, sessionId={}",
                                    principal.deviceId(),
                                    session.getId(),
                                    failure);
                            sendErrorAndClose(
                                    session,
                                    DEVICE_VOICE_GATEWAY_FAILED.code(),
                                    DEVICE_VOICE_GATEWAY_FAILED.message());
                        },
                        () -> closeQuietly(session));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        var sink = sinks.get(session.getId());
        if (sink == null) {
            return;
        }
        var chunk = new byte[message.getPayload().remaining()];
        message.getPayload().get(chunk);
        sink.tryEmitNext(chunk);
        var bytes = receivedBytes.get(session.getId());
        if (bytes != null) {
            bytes.addAndGet(chunk.length);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var sink = sinks.remove(session.getId());
        if (sink != null) {
            sink.tryEmitComplete();
        }
        var latch = usageReady.get(session.getId());
        if (latch != null) {
            try {
                latch.await(USAGE_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            settle(session.getId());
        } finally {
            principals.remove(session.getId());
            receivedBytes.remove(session.getId());
            durationSeconds.remove(session.getId());
            settled.remove(session.getId());
            usageReady.remove(session.getId());
        }
    }

    private void settle(String sessionId) {
        var settlement = settled.get(sessionId);
        var principal = principals.get(sessionId);
        if (settlement == null || principal == null || !settlement.compareAndSet(false, true)) {
            return;
        }
        var duration = durationSeconds.get(sessionId);
        var durationMs = duration == null ? 0 : duration.get() * 1000;
        if (durationMs <= 0) {
            var bytes = receivedBytes.get(sessionId);
            var byteCount = bytes == null ? 0 : bytes.get();
            durationMs =
                    (int) Math.min(byteCount * 1000L / PCM_BYTES_PER_SECOND, Integer.MAX_VALUE);
        }
        if (durationMs <= 0) {
            return;
        }
        try {
            gatewayService.settleAsr(principal, durationMs);
        } catch (RuntimeException exception) {
            log.warn(
                    "设备 ASR 结算失败: deviceId={}, sessionId={}, err={}",
                    principal.deviceId(),
                    sessionId,
                    exception.getMessage());
        }
    }

    private void aggregateUsage(String sessionId, Integer seconds) {
        if (seconds == null || seconds <= 0) {
            return;
        }
        var duration = durationSeconds.get(sessionId);
        if (duration != null) {
            duration.accumulateAndGet(seconds, Math::max);
        }
        var latch = usageReady.get(sessionId);
        if (latch != null) {
            latch.countDown();
        }
    }

    private static DevicePrincipal devicePrincipal(WebSocketSession session) {
        var principal = session.getPrincipal();
        if (principal instanceof DevicePrincipal devicePrincipal) {
            return devicePrincipal;
        }
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof DevicePrincipal devicePrincipal) {
            return devicePrincipal;
        }
        return null;
    }

    private static void sendText(WebSocketSession session, String text) {
        send(session, Map.of("text", text, "final", true));
    }

    private static void sendErrorAndClose(WebSocketSession session, int code, String message) {
        send(session, Map.of("code", code, "message", message, "error", true, "final", true));
        closeQuietly(session);
    }

    private static void send(WebSocketSession session, Map<String, Object> payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(JsonUtils.toJsonString(payload)));
        } catch (Exception exception) {
            log.debug("设备 ASR 响应发送失败: sessionId={}", session.getId());
        }
    }

    private static void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (Exception ignored) {
            // 客户端可能已断开。
        }
    }

    private static String queryParam(WebSocketSession session, String name, String defaultValue) {
        URI uri = session.getUri();
        if (uri == null) {
            return defaultValue;
        }
        var value = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
