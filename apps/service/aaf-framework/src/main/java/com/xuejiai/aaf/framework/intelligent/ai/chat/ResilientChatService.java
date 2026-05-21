package com.xuejiai.aaf.framework.intelligent.ai.chat;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.core.model.AiModelRepository;

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
    private final ModelRouter modelRouter;
    private final AiModelRepository modelRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 同步调用，使用完整路由上下文。
     *
     * @param messages 消息列表
     * @param ctx      路由上下文（含 userId、capability、显式 modelId、编排配置、任务特征）
     */
    public ChatResponse call(List<Message> messages, ModelRoutingContext ctx) {
        var modelId = modelRouter.resolve(ctx);
        try {
            var response = doCall(messages, modelId);
            publishUsage(response, ctx.userId(), modelId);
            return response;
        } catch (Exception e) {
            log.warn("主模型 [{}] 调用失败，尝试降级: {}", modelId, e.getMessage());
            return callFallback(messages, modelId, ctx.userId());
        }
    }

    /**
     * 同步调用，简化入口（显式指定 modelId）。
     *
     * @param messages 消息列表
     * @param modelId  显式 modelId（null 时走路由决策）
     * @param userId   用户 ID
     */
    public ChatResponse call(List<Message> messages, String modelId, Long userId) {
        return call(messages, ModelRoutingContext.of(userId, "CHAT", modelId));
    }

    /**
     * 流式调用，使用完整路由上下文。
     */
    public Flux<ChatResponse> stream(List<Message> messages, ModelRoutingContext ctx) {
        var modelId = modelRouter.resolve(ctx);
        try {
            return doStream(messages, modelId);
        } catch (Exception e) {
            log.warn("主模型 [{}] 流式调用失败，尝试降级: {}", modelId, e.getMessage());
            return doStream(messages, resolveFallback(modelId));
        }
    }

    /**
     * 流式调用，简化入口。
     */
    public Flux<ChatResponse> stream(List<Message> messages, String modelId, Long userId) {
        return stream(messages, ModelRoutingContext.of(userId, "CHAT", modelId));
    }

    private ChatResponse callFallback(List<Message> messages, String modelId, Long userId) {
        var fallbackId = resolveFallback(modelId);
        var response = doCall(messages, fallbackId);
        publishUsage(response, userId, fallbackId);
        return response;
    }

    private ChatResponse doCall(List<Message> messages, String modelId) {
        return clientFactory.get(modelId).prompt(new Prompt(messages)).call().chatResponse();
    }

    private Flux<ChatResponse> doStream(List<Message> messages, String modelId) {
        return clientFactory.get(modelId).prompt(new Prompt(messages)).stream().chatResponse();
    }

    private String resolveFallback(String modelId) {
        return modelRepository.findByModelId(modelId)
                .filter(m -> m.getFallbackModelId() != null)
                .map(m -> m.getFallbackModelId())
                .orElse(modelId);
    }

    private void publishUsage(ChatResponse response, Long userId, String modelId) {
        if (response == null
                || response.getMetadata() == null
                || response.getMetadata().getUsage() == null) return;
        var usage = response.getMetadata().getUsage();
        eventPublisher.publishEvent(
                new TokenUsageEvent(
                        userId, modelId, usage.getPromptTokens(), usage.getCompletionTokens()));
    }
}
