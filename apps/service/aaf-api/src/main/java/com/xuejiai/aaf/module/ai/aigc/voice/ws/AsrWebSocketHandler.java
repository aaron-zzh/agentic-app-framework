package com.xuejiai.aaf.module.ai.aigc.voice.ws;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.xuejiai.aaf.common.exception.InsufficientCreditsException;
import com.xuejiai.aaf.config.JwtHandshakeInterceptor;
import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.speech.AsrResult;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

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
 * <p>计费策略：
 *
 * <ul>
 *   <li>握手期 {@link AiCreditGuard#precheck} 校验余额，余额不足直接拒绝
 *   <li>会话进行中 transcribeStream 推送的 usage 帧仅用于聚合最新累计 duration（{@link #latestDurationSecsMap}），不立即结算
 *       —— DashScope 的 {@code usage.duration} 是累计值，多次扣会重复
 *   <li>{@link #afterConnectionClosed}（连接断开）触发**一次**结算：
 *       <ol>
 *         <li>真实结算优先：若 {@code latestDurationSecsMap > 0}，按 DashScope 累计 duration 计费
 *         <li>字节兜底：未拿到真实 usage 但收到了音频字节，按 PCM 16kHz mono 16bit (≈32000 B/s) 估算
 *         <li>不扣：两者都为 0，理论上 precheck 通过但完全没发音频
 *       </ol>
 *   <li>{@link #claimSettlement} CAS 保证最多触发一档，绝不双扣
 *   <li>关闭时通过 {@link #usageReadyLatchMap} 短超时等待 SDK 尾包，避免"sink.complete 异步 vs 兜底同步"竞态
 * </ul>
 *
 * <p>账单效果：一次会话恒定写入一条 {@code credit_transaction}（与 sessionId 一一对应），便于对账。
 *
 * @author AaronZZH &amp; Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsrWebSocketHandler extends BinaryWebSocketHandler {

    /** ASR 模型 ID，对应 seed 中 fun-asr-realtime 的配置。 */
    private static final String ASR_MODEL_ID = "qwen:fun-asr-realtime";

    /**
     * 计费 capability，与字典 {@code credit_transaction_category} 的 value 对齐（下划线格式）。
     *
     * <p>历史曾用 {@code speech-asr}（中划线），不一致；统一改为 {@code speech_asr}。
     */
    private static final String BILLING_CAPABILITY = "speech_asr";

    /** PCM 16kHz mono 16bit 的字节率：16000 samples/s × 2 bytes/sample × 1 channel = 32000 B/s。 */
    private static final int PCM_BYTES_PER_SECOND = 32_000;

    /** 关闭时等待真实 usage 帧到达的最长时间（毫秒）。超时降级走字节兜底。 */
    private static final long USAGE_WAIT_TIMEOUT_MS = 2_000L;

    private final SpeechService speechService;
    private final AiCreditGuard creditGuard;
    private final ConfigCacheManager configCacheManager;

    /** 每个 session 对应一个音频 sink。 */
    private final Map<String, Sinks.Many<byte[]>> sinkMap = new ConcurrentHashMap<>();

    /** 每个 session 是否已结算（CAS 防止重入双扣）。 */
    private final Map<String, AtomicBoolean> settledMap = new ConcurrentHashMap<>();

    /** 每个 session 累计接收的 PCM 字节数（兜底结算时估算 duration）。 */
    private final Map<String, AtomicLong> bytesMap = new ConcurrentHashMap<>();

    /** 每个 session 的真实 ASR 累计 duration（秒），由 transcribeStream 推送的 usage 帧更新。 */
    private final Map<String, AtomicInteger> latestDurationSecsMap = new ConcurrentHashMap<>();

    /** 每个 session 的 usage 到达信号；{@link #afterConnectionClosed} 用它短超时等真实计费帧。 */
    private final Map<String, CountDownLatch> usageReadyLatchMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        var lang = extractParam(session, "lang", "zh-CN");
        Long userId = (Long) session.getAttributes().get(JwtHandshakeInterceptor.ATTR_USER_ID);

        // 1. 握手期 precheck：余额不足 / userId 缺失 → 直接关闭连接，不进入流式订阅
        if (!precheck(session, userId)) {
            return;
        }

        settledMap.put(session.getId(), new AtomicBoolean(false));
        bytesMap.put(session.getId(), new AtomicLong(0));
        latestDurationSecsMap.put(session.getId(), new AtomicInteger(0));
        usageReadyLatchMap.put(session.getId(), new CountDownLatch(1));

        Sinks.Many<byte[]> sink = Sinks.many().unicast().onBackpressureBuffer();
        sinkMap.put(session.getId(), sink);

        speechService
                .transcribeStream(sink.asFlux(), lang)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        result -> {
                            if (result.hasUsage()) {
                                // 仅聚合最新真实 duration，不立即结算（统一在 close 时一次结算）
                                aggregateUsage(session.getId(), result.usage().duration());
                            } else {
                                sendText(session, result.text());
                            }
                        },
                        err -> {
                            log.error("ASR 识别错误: sessionId={}", session.getId(), err);
                            closeQuietly(session);
                        },
                        () -> closeQuietly(session));

        log.info(
                "ASR WebSocket 连接建立: sessionId={}, userId={}, lang={}",
                session.getId(),
                userId,
                lang);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        var sink = sinkMap.get(session.getId());
        if (sink == null) return;
        byte[] chunk = new byte[message.getPayload().remaining()];
        message.getPayload().get(chunk);
        sink.tryEmitNext(chunk);
        // 累计字节数，供兜底结算使用
        var counter = bytesMap.get(session.getId());
        if (counter != null) {
            counter.addAndGet(chunk.length);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 通知 SDK 流上游已结束，触发尾包下发
        var sink = sinkMap.remove(session.getId());
        if (sink != null) sink.tryEmitComplete();

        // 短超时等待真实 usage 帧到达：
        // - 若 sentenceEnd 已携带 usage 并被订阅回调聚合过，countDown 已触发，await 立即返回
        // - 否则给 boundedElastic 线程一个最多 USAGE_WAIT_TIMEOUT_MS 的窗口
        // - 超时则降级走字节兜底，绝不漏扣
        var latch = usageReadyLatchMap.get(session.getId());
        if (latch != null) {
            try {
                latch.await(USAGE_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            settleAtClose(session);
        } finally {
            settledMap.remove(session.getId());
            bytesMap.remove(session.getId());
            latestDurationSecsMap.remove(session.getId());
            usageReadyLatchMap.remove(session.getId());
        }

        log.info("ASR WebSocket 连接关闭: sessionId={}, status={}", session.getId(), status);
    }

    /** 聚合 transcribeStream 推送的 usage 帧：取累计 duration 最大值，并 countDown 通知 close 路径。 */
    private void aggregateUsage(String sessionId, int durationSecs) {
        if (durationSecs <= 0) return;
        var latest = latestDurationSecsMap.get(sessionId);
        if (latest != null) {
            latest.accumulateAndGet(durationSecs, Math::max);
        }
        var latch = usageReadyLatchMap.get(sessionId);
        if (latch != null) latch.countDown();
    }

    /**
     * 关闭时统一结算：CAS 抢占后按"真实 duration → 字节兜底 → 不扣"三档优先级择一执行。
     *
     * <p>使用 CAS 而非简单标记，是为了防御未来可能的重入路径（如 SDK 异常导致 onError 与 close 同时触发）。当前 close 仅触发一次，CAS 失败分支仅作保险。
     */
    private void settleAtClose(WebSocketSession session) {
        if (!claimSettlement(session.getId())) {
            return;
        }
        Long userId = (Long) session.getAttributes().get(JwtHandshakeInterceptor.ATTR_USER_ID);
        if (userId == null) {
            log.warn("ASR 结算跳过：userId 缺失, sessionId={}", session.getId());
            return;
        }
        var latest = latestDurationSecsMap.get(session.getId());
        int realSecs = latest != null ? latest.get() : 0;
        if (realSecs > 0) {
            settleByRealDuration(session, userId, realSecs);
            return;
        }
        var bytes = bytesMap.get(session.getId());
        long byteCount = bytes != null ? bytes.get() : 0;
        if (byteCount > 0) {
            settleByBytes(session, userId, byteCount);
            return;
        }
        log.info("ASR 无用量数据，跳过结算: sessionId={}, userId={}", session.getId(), userId);
    }

    /** 按 DashScope 返回的真实累计 duration 结算（首选路径）。 */
    private void settleByRealDuration(WebSocketSession session, Long userId, int durationSecs) {
        try {
            var model = configCacheManager.getAiModelByModelId(ASR_MODEL_ID);
            // 结算明细由 DefaultAiCreditGuard 统一输出（"AI 结算明细" + "积分消费" + "AI 积分扣减成功"），
            // 此处不重复打日志。路径来源（真实/兜底）已通过 remark 区分（"语音识别" vs "语音识别（兜底）"）。
            creditGuard.settleByUsage(
                    userId,
                    model,
                    AsrResult.ofUsage(durationSecs * 1000).usage(),
                    BILLING_CAPABILITY,
                    "语音识别");
        } catch (Exception e) {
            log.warn(
                    "ASR 流式结算失败: userId={}, sessionId={}, err={}",
                    userId,
                    session.getId(),
                    e.getMessage());
        }
    }

    /** 字节兜底结算：未拿到真实 usage 时按 PCM 字节估算 duration（降级路径）。 */
    private void settleByBytes(WebSocketSession session, Long userId, long bytes) {
        int durationMs = (int) Math.min(bytes * 1000L / PCM_BYTES_PER_SECOND, Integer.MAX_VALUE);
        // 兜底是异常路径（DashScope 未下发 usage），打一条 warn 提示运营关注；
        // 结算明细仍由 DefaultAiCreditGuard 统一输出。
        log.warn(
                "ASR 走字节兜底结算: userId={}, sessionId={}, bytes={}, estimatedDurationMs={}",
                userId,
                session.getId(),
                bytes,
                durationMs);
        try {
            var model = configCacheManager.getAiModelByModelId(ASR_MODEL_ID);
            creditGuard.settleByUsage(
                    userId,
                    model,
                    AsrResult.ofUsage(durationMs).usage(),
                    BILLING_CAPABILITY,
                    "语音识别（兜底）");
        } catch (Exception e) {
            log.warn(
                    "ASR 兜底结算失败: userId={}, sessionId={}, bytes={}, err={}",
                    userId,
                    session.getId(),
                    bytes,
                    e.getMessage());
        }
    }

    /**
     * 握手期余额预检。
     *
     * @return 是否放行；false 表示已发拒绝消息并关闭连接，调用方应直接 return
     */
    private boolean precheck(WebSocketSession session, Long userId) {
        if (userId == null) {
            log.warn("ASR 握手 userId 缺失，拒绝连接: sessionId={}", session.getId());
            sendErrorAndClose(session, "未认证：握手未携带 userId");
            return false;
        }
        try {
            creditGuard.precheck(userId, BILLING_CAPABILITY, AiCreditGuard.INESTIMABLE_COST);
            return true;
        } catch (InsufficientCreditsException e) {
            log.info(
                    "ASR 握手余额不足，拒绝连接: sessionId={}, userId={}, balance={}",
                    session.getId(),
                    userId,
                    e.getBalance());
            sendErrorAndClose(session, "积分余额不足，请先充值");
            return false;
        } catch (Exception e) {
            log.warn(
                    "ASR 握手 precheck 异常: sessionId={}, userId={}, err={}",
                    session.getId(),
                    userId,
                    e.getMessage());
            sendErrorAndClose(session, "积分校验失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 抢占结算锁。CAS 把 settled 从 false 翻到 true：成功者负责结算，失败者跳过。
     *
     * <p>当前只在 {@link #settleAtClose} 一处调用，CAS 失败分支用于防御未来可能引入的并发结算路径。
     */
    private boolean claimSettlement(String sessionId) {
        var settled = settledMap.get(sessionId);
        return settled != null && settled.compareAndSet(false, true);
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

    /** 发送错误消息后关闭连接（用于 precheck 拒绝场景）。 */
    private void sendErrorAndClose(WebSocketSession session, String message) {
        if (session.isOpen()) {
            try {
                var json =
                        "{\"text\":\""
                                + message.replace("\"", "\\\"")
                                + "\",\"final\":true,\"error\":true}";
                session.sendMessage(new TextMessage(json));
            } catch (Exception ignored) {
                // 客户端可能已断开
            }
        }
        closeQuietly(session);
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
