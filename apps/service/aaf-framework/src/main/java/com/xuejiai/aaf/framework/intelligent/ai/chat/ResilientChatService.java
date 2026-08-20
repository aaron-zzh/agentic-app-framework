package com.xuejiai.aaf.framework.intelligent.ai.chat;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.enums.pay.CreditTransactionCategoryEnum;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.model.ModelSpec;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 弹性对话服务：降级 + 计量 + 完整模型路由决策链。
 *
 * <p>模型选择优先级：显式指定 → 编排引擎 → AI辅助决策 → 用户偏好 → 系统默认 → yaml兜底
 *
 * <p>计费 capability 透传：路由层使用 {@code CapabilityRoutingContext.CAP_*}（大写），落库使用规范小写值（{@code chat} /
 * {@code vision}）。本服务自动检测消息内容（含图像 Media → {@code vision}），并通过 {@link TokenUsageEvent#capability}
 * 字段把规范化值透传给 计费监听器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientChatService {

    /** 计费 capability 规范小写值——纯文本对话。 */
    private static final String BILLING_CAP_CHAT = "chat";

    /** 计费 capability 规范小写值——含图像理解的多模态对话。 */
    private static final String BILLING_CAP_VISION = "vision";

    private final DynamicChatClientFactory clientFactory;
    private final CapabilityRouter capabilityRouter;
    private final AiModelRepository modelRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AiCreditGuard creditGuard;
    private final OperatorContext operatorContext;

    /**
     * 同步调用，使用完整路由上下文。
     *
     * @param messages 消息列表
     * @param ctx 路由上下文（含 userId、capability、显式 modelId、编排配置、任务特征）
     */
    public ChatResponse call(List<Message> messages, CapabilityRoutingContext ctx) {
        var ownerId = billingOwnerId(ctx.userId());
        creditGuard.precheck(
                ownerId,
                CreditTransactionCategoryEnum.fromCapability(ctx.capability()),
                AiCreditGuard.INESTIMABLE_COST);
        var model = capabilityRouter.resolve(ctx);
        var billingCapability = detectBillingCapability(messages);
        try {
            var response = doCall(messages, model.getModelId());
            publishUsage(response, ownerId, model.getId(), billingCapability);
            return response;
        } catch (Exception e) {
            // m29：只有可重试错误才降级，参数错误/内容策略拒绝等直接抛出
            if (!isRetryable(e)) {
                log.warn("主模型 [{}] 调用失败且不可重试，不降级: {}", model.getModelId(), e.getMessage());
                throw e;
            }
            log.warn("主模型 [{}] 调用失败，尝试降级: {}", model.getModelId(), e.getMessage());
            return callFallback(messages, model, ownerId, billingCapability);
        }
    }

    /** 使用冻结的 ai_model 主键精确调用；上下文摘要禁止路由和 fallback。 */
    public ChatResponse callExact(List<Message> messages, ModelSpec modelSpec, Long userId) {
        Objects.requireNonNull(modelSpec, "modelSpec 不能为空");
        var model =
                modelRepository
                        .findById(databaseId(modelSpec))
                        .filter(candidate -> Boolean.TRUE.equals(candidate.getEnabled()))
                        .orElseThrow(() -> new IllegalArgumentException("摘要模型不存在或未启用"));
        var ownerId = billingOwnerId(userId);
        creditGuard.precheck(
                ownerId,
                CreditTransactionCategoryEnum.fromCapability(CapabilityRoutingContext.CAP_CHAT),
                AiCreditGuard.INESTIMABLE_COST);
        var response = doCall(messages, model.getModelId());
        publishUsage(response, ownerId, model.getId(), detectBillingCapability(messages));
        return response;
    }

    private static long databaseId(ModelSpec modelSpec) {
        try {
            return Long.parseLong(modelSpec.modelId());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(
                    "摘要模型规格必须引用 ai_model 主键: " + modelSpec.modelId(), failure);
        }
    }

    /** 同步调用，带额外 ChatOptions（用于透传非标准参数如 enable_search）。 */
    public ChatResponse call(
            List<Message> messages,
            CapabilityRoutingContext ctx,
            org.springframework.ai.chat.prompt.ChatOptions options) {
        var ownerId = billingOwnerId(ctx.userId());
        creditGuard.precheck(
                ownerId,
                CreditTransactionCategoryEnum.fromCapability(ctx.capability()),
                AiCreditGuard.INESTIMABLE_COST);
        var model = capabilityRouter.resolve(ctx);
        var billingCapability = detectBillingCapability(messages);
        try {
            var response = doCall(messages, model.getModelId(), options);
            publishUsage(response, ownerId, model.getId(), billingCapability);
            return response;
        } catch (Exception e) {
            // m29：同上，不可重试错误不降级
            if (!isRetryable(e)) {
                log.warn("主模型 [{}] 调用失败且不可重试，不降级: {}", model.getModelId(), e.getMessage());
                throw e;
            }
            log.warn("主模型 [{}] 调用失败，尝试降级: {}", model.getModelId(), e.getMessage());
            return callFallback(messages, model, ownerId, billingCapability);
        }
    }

    /**
     * m29：判断异常是否值得降级重试。
     *
     * <p>原实现对**任何** {@code Exception} 都换备用模型重试，后果有两个：
     *
     * <ul>
     *   <li>参数非法、内容策略拒绝这类必然再次失败的错误也重试，白付一次备用模型的钱
     *   <li>真实错误被"降级成功"掩盖，排查时只看到备用模型的结果
     * </ul>
     *
     * <p>判定沿因果链向上找第一个可判定信号：Spring AI 的 Transient/NonTransient 异常、HTTP 状态码（5xx 与 429 可重试，其余 4xx
     * 不可）、网络超时/连接类 IO 异常。无法判定时保守地**不降级**，让错误暴露。
     */
    private boolean isRetryable(Throwable error) {
        for (var t = error; t != null; t = t.getCause()) {
            var name = t.getClass().getName();
            if (name.endsWith("TransientAiException")) {
                return true;
            }
            if (name.endsWith("NonTransientAiException")) {
                return false;
            }
            if (t instanceof org.springframework.web.client.HttpStatusCodeException http) {
                return isRetryableStatus(http.getStatusCode().value());
            }
            if (t
                    instanceof
                    org.springframework.web.reactive.function.client.WebClientResponseException
                            webClient) {
                return isRetryableStatus(webClient.getStatusCode().value());
            }
            if (t instanceof java.net.SocketTimeoutException
                    || t instanceof java.net.ConnectException
                    || t instanceof java.util.concurrent.TimeoutException
                    || t instanceof java.io.IOException) {
                return true;
            }
        }
        return false;
    }

    /** 5xx 服务端错误与 429 限流可重试；其余 4xx 是请求本身的问题，重试只会再失败一次。 */
    private boolean isRetryableStatus(int status) {
        return status >= 500 || status == 429;
    }

    /**
     * 同步调用，简化入口（显式指定 modelId）。
     *
     * @param messages 消息列表
     * @param modelId 显式 modelId（null 时走路由决策）
     * @param userId 用户 ID
     */
    public ChatResponse call(List<Message> messages, String modelId, Long userId) {
        return call(
                messages,
                CapabilityRoutingContext.of(userId, CapabilityRoutingContext.CAP_CHAT, modelId));
    }

    /** 流式调用，使用完整路由上下文。 */
    public Flux<ChatResponse> stream(List<Message> messages, CapabilityRoutingContext ctx) {
        long startedAt = System.currentTimeMillis();
        var ownerId = billingOwnerId(ctx.userId());
        // 1. 积分/配额预检，不足则抛异常
        creditGuard.precheck(
                ownerId,
                CreditTransactionCategoryEnum.fromCapability(ctx.capability()),
                AiCreditGuard.INESTIMABLE_COST);
        // 2. 路由决策链：显式 modelId → 编排引擎 → AI 辅助 → 用户偏好 → 系统默认 → yaml 兜底
        var model = capabilityRouter.resolve(ctx);
        // 3. 自动识别落库 capability（vision/chat），与路由层 ctx.capability() 解耦
        var billingCapability = detectBillingCapability(messages);
        long routedAt = System.currentTimeMillis();
        log.info(
                "[流式计时] 路由完成 model={} billingCapability={} 预检+路由耗时={}ms",
                model.getModelId(),
                billingCapability,
                routedAt - startedAt);
        try {
            // 4. 从 DynamicChatClientFactory 取 ChatClient（Caffeine 缓存）并发起流式调用
            return logTiming(
                    withStreamUsage(
                            doStream(messages, model.getModelId()),
                            ownerId,
                            model.getId(),
                            billingCapability),
                    model.getModelId(),
                    routedAt);
        } catch (Exception e) {
            // m29：流式路径同样只对可重试错误降级
            if (!isRetryable(e)) {
                log.warn("主模型 [{}] 流式调用失败且不可重试，不降级: {}", model.getModelId(), e.getMessage());
                throw e;
            }
            log.warn("主模型 [{}] 流式调用失败，尝试降级: {}", model.getModelId(), e.getMessage());
            // 5. 主模型失败时降级到 fallbackModelId
            var fallback = resolveFallback(model);
            return logTiming(
                    withStreamUsage(
                            doStream(messages, fallback.getModelId()),
                            ownerId,
                            fallback.getId(),
                            billingCapability),
                    fallback.getModelId(),
                    routedAt);
        }
    }

    /**
     * 流式计时日志：用于判断上游是否「真流式」。
     *
     * <p>关键判据是 <b>首 token → 完成</b> 的时长配合 chunk 数：
     *
     * <ul>
     *   <li>该时长较长、chunk 分散到达 → 真流式（token 逐个产生）
     *   <li>TTFT 很大但「首 token→完成」接近 0、chunk 瞬间到齐 → 上游攒完一次性返回，非真流式
     * </ul>
     */
    private Flux<ChatResponse> logTiming(Flux<ChatResponse> stream, String modelId, long routedAt) {
        var firstTokenAt = new AtomicLong(0);
        var chunkCount = new AtomicLong(0);
        return stream.doOnNext(
                        r -> {
                            long now = System.currentTimeMillis();
                            if (firstTokenAt.compareAndSet(0, now)) {
                                log.info(
                                        "[流式计时] 首 token model={} TTFT(路由后)={}ms",
                                        modelId,
                                        now - routedAt);
                            }
                            chunkCount.incrementAndGet();
                        })
                .doOnComplete(
                        () -> {
                            long now = System.currentTimeMillis();
                            long first = firstTokenAt.get();
                            log.info(
                                    "[流式计时] 完成 model={} chunk数={} 首token→完成={}ms 路由后总时长={}ms",
                                    modelId,
                                    chunkCount.get(),
                                    first > 0 ? now - first : -1,
                                    now - routedAt);
                        });
    }

    /** 流式调用，简化入口。 */
    public Flux<ChatResponse> stream(List<Message> messages, String modelId, Long userId) {
        return stream(
                messages,
                CapabilityRoutingContext.of(userId, CapabilityRoutingContext.CAP_CHAT, modelId));
    }

    private ChatResponse callFallback(
            List<Message> messages, AiModel model, Long userId, String billingCapability) {
        var fallback = resolveFallback(model);
        var response = doCall(messages, fallback.getModelId());
        publishUsage(response, userId, fallback.getId(), billingCapability);
        return response;
    }

    private ChatResponse doCall(List<Message> messages, String modelId) {
        return clientFactory.get(modelId).prompt(new Prompt(messages)).call().chatResponse();
    }

    private ChatResponse doCall(
            List<Message> messages,
            String modelId,
            org.springframework.ai.chat.prompt.ChatOptions options) {
        return clientFactory
                .get(modelId)
                .prompt(new Prompt(messages, options))
                .call()
                .chatResponse();
    }

    private Flux<ChatResponse> doStream(List<Message> messages, String modelId) {
        return clientFactory.get(modelId).prompt(new Prompt(messages)).stream().chatResponse();
    }

    private AiModel resolveFallback(AiModel model) {
        if (model.getFallbackModelId() == null) return model;
        return modelRepository.findByModelId(model.getFallbackModelId()).orElse(model);
    }

    private void publishUsage(ChatResponse response, Long userId, Long modelId, String capability) {
        if (response == null
                || response.getMetadata() == null
                || response.getMetadata().getUsage() == null) return;
        var usage = response.getMetadata().getUsage();
        eventPublisher.publishEvent(
                new TokenUsageEvent(
                        userId,
                        modelId,
                        usage.getPromptTokens(),
                        usage.getCompletionTokens(),
                        capability));
    }

    private Flux<ChatResponse> withStreamUsage(
            Flux<ChatResponse> stream, Long userId, Long modelId, String capability) {
        var promptTokens = new AtomicLong();
        var completionTokens = new AtomicLong();
        return stream.doOnNext(
                        response -> {
                            if (response == null
                                    || response.getMetadata() == null
                                    || response.getMetadata().getUsage() == null) {
                                return;
                            }
                            var usage = response.getMetadata().getUsage();
                            promptTokens.updateAndGet(
                                    current -> Math.max(current, usage.getPromptTokens()));
                            completionTokens.updateAndGet(
                                    current -> Math.max(current, usage.getCompletionTokens()));
                        })
                .doOnComplete(
                        () -> {
                            if (promptTokens.get() > 0 || completionTokens.get() > 0) {
                                eventPublisher.publishEvent(
                                        new TokenUsageEvent(
                                                userId,
                                                modelId,
                                                promptTokens.get(),
                                                completionTokens.get(),
                                                capability));
                            }
                        });
    }

    private Long billingOwnerId(Long fallbackUserId) {
        return operatorContext.currentOwnerId().orElse(fallbackUserId);
    }

    /**
     * 自动识别计费 capability：消息中含图像 Media 即 {@link #BILLING_CAP_VISION}，否则 {@link #BILLING_CAP_CHAT}。
     *
     * <p>用于落库 {@code credit_transaction.category}，与路由层 {@code ctx.capability()}（大写常量）解耦。 仅扫描 {@link
     * UserMessage}，因为 system / assistant 消息按惯例不含图像附件。
     */
    private static String detectBillingCapability(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return BILLING_CAP_CHAT;
        for (var msg : messages) {
            if (msg instanceof UserMessage um) {
                var media = um.getMedia();
                if (media != null && !media.isEmpty()) {
                    return BILLING_CAP_VISION;
                }
            }
        }
        return BILLING_CAP_CHAT;
    }
}
