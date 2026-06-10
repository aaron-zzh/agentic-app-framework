package com.xuejiai.aaf.framework.intelligent.ai.chat;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 弹性对话服务：降级 + 计量 + 完整模型路由决策链。
 *
 * <p>模型选择优先级：显式指定 → 编排引擎 → AI辅助决策 → 用户偏好 → 系统默认 → yaml兜底
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientChatService {

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
        creditGuard.precheck(ownerId, ctx.capability());
        var model = capabilityRouter.resolve(ctx);
        try {
            var response = doCall(messages, model.getModelId());
            publishUsage(response, ownerId, model.getId());
            return response;
        } catch (Exception e) {
            log.warn("主模型 [{}] 调用失败，尝试降级: {}", model.getModelId(), e.getMessage());
            return callFallback(messages, model, ownerId);
        }
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
        var ownerId = billingOwnerId(ctx.userId());
        creditGuard.precheck(ownerId, ctx.capability());
        var model = capabilityRouter.resolve(ctx);
        try {
            return withStreamUsage(doStream(messages, model.getModelId()), ownerId, model.getId());
        } catch (Exception e) {
            log.warn("主模型 [{}] 流式调用失败，尝试降级: {}", model.getModelId(), e.getMessage());
            var fallback = resolveFallback(model);
            return withStreamUsage(
                    doStream(messages, fallback.getModelId()), ownerId, fallback.getId());
        }
    }

    /** 流式调用，简化入口。 */
    public Flux<ChatResponse> stream(List<Message> messages, String modelId, Long userId) {
        return stream(
                messages,
                CapabilityRoutingContext.of(userId, CapabilityRoutingContext.CAP_CHAT, modelId));
    }

    private ChatResponse callFallback(List<Message> messages, AiModel model, Long userId) {
        var fallback = resolveFallback(model);
        var response = doCall(messages, fallback.getModelId());
        publishUsage(response, userId, fallback.getId());
        return response;
    }

    private ChatResponse doCall(List<Message> messages, String modelId) {
        return clientFactory.get(modelId).prompt(new Prompt(messages)).call().chatResponse();
    }

    private Flux<ChatResponse> doStream(List<Message> messages, String modelId) {
        return clientFactory.get(modelId).prompt(new Prompt(messages)).stream().chatResponse();
    }

    private AiModel resolveFallback(AiModel model) {
        if (model.getFallbackModelId() == null) return model;
        return modelRepository.findByModelId(model.getFallbackModelId()).orElse(model);
    }

    private void publishUsage(ChatResponse response, Long userId, Long modelId) {
        if (response == null
                || response.getMetadata() == null
                || response.getMetadata().getUsage() == null) return;
        var usage = response.getMetadata().getUsage();
        eventPublisher.publishEvent(
                new TokenUsageEvent(
                        userId, modelId, usage.getPromptTokens(), usage.getCompletionTokens()));
    }

    private Flux<ChatResponse> withStreamUsage(
            Flux<ChatResponse> stream, Long userId, Long modelId) {
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
                                                completionTokens.get()));
                            }
                        });
    }

    private Long billingOwnerId(Long fallbackUserId) {
        return operatorContext.currentOwnerId().orElse(fallbackUserId);
    }
}
