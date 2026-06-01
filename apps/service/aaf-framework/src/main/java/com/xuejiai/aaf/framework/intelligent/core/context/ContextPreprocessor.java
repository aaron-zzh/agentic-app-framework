package com.xuejiai.aaf.framework.intelligent.core.context;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunEventPublisher;
import com.xuejiai.aaf.framework.intelligent.agent.run.AgentRunEventType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 模型调用前上下文预处理器。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextPreprocessor {

    private final ContextSettingsProvider settingsProvider;
    private final ContextPolicyService contextPolicyService;
    private final ContextSummarizer contextSummarizer;
    private final ApplicationEventPublisher eventPublisher;
    private final AgentRunEventPublisher agentRunEventPublisher;
    private final ContextTokenEstimator tokenEstimator = new ContextTokenEstimator();

    public ContextPreparationResult prepare(ContextPreparationRequest request) {
        var startedAt = System.currentTimeMillis();
        var messages = request.messages();
        var settings = settingsProvider.current();
        if (!settings.enabled() || messages == null) {
            return unchanged(request, startedAt, "disabled");
        }

        var budget = contextPolicyService.budget(request.modelId(), request.policy());
        var tokenBefore = tokenEstimator.estimate(messages);
        if (messages.size() < budget.messageThreshold() && tokenBefore < budget.triggerTokens()) {
            return unchanged(request, startedAt, "below-threshold");
        }
        agentRunEventPublisher.publish(
                AgentRunEventType.CONTEXT_COMPRESSION_STARTED,
                "整理上下文",
                "正在根据模型窗口整理上下文",
                java.util.Map.of(
                        "modelId", request.modelId() != null ? request.modelId() : "",
                        "tokenBefore", tokenBefore,
                        "triggerTokens", budget.triggerTokens(),
                        "messageCount", messages.size()));

        var actions = new LinkedHashSet<ContextCompressionAction>();
        var prepared = truncateLargeMessages(messages, budget, actions);
        prepared = dropOldHistory(prepared, budget, actions);
        var tokenAfterRules = tokenEstimator.estimate(prepared);

        if (tokenAfterRules > budget.triggerTokens()
                && settings.enableSummary()) {
            prepared = summarizeHistory(prepared, request.modelId(), budget, actions);
        }

        var tokenAfter = tokenEstimator.estimate(prepared);
        var result =
                new ContextPreparationResult(
                        List.copyOf(prepared),
                        budget,
                        tokenBefore,
                        tokenAfter,
                        List.copyOf(actions));
        publishLog(request, result, startedAt, "preprocess");
        return result;
    }

    private ContextPreparationResult unchanged(
            ContextPreparationRequest request, long startedAt, String reason) {
        var budget = contextPolicyService.budget(request.modelId(), request.policy());
        var token = tokenEstimator.estimate(request.messages());
        var result =
                new ContextPreparationResult(
                        request.messages(),
                        budget,
                        token,
                        token,
                        List.of(ContextCompressionAction.NONE));
        publishLog(request, result, startedAt, reason);
        return result;
    }

    private List<Message> truncateLargeMessages(
            List<Message> messages,
            ContextBudget budget,
            LinkedHashSet<ContextCompressionAction> actions) {
        var result = new ArrayList<Message>(messages.size());
        for (var message : messages) {
            var text = message.getText();
            if (text != null && text.length() > budget.largeInputCharThreshold()) {
                result.add(copyWithText(message, preview(text, budget.rulePreviewChars())));
                actions.add(ContextCompressionAction.RULE_TRUNCATE_LARGE_MESSAGE);
            } else {
                result.add(message);
            }
        }
        return result;
    }

    private List<Message> dropOldHistory(
            List<Message> messages,
            ContextBudget budget,
            LinkedHashSet<ContextCompressionAction> actions) {
        if (tokenEstimator.estimate(messages) <= budget.triggerTokens()) {
            return messages;
        }
        if (messages.size() <= budget.lastKeep() + 1) {
            return messages;
        }

        var result = new ArrayList<Message>();
        var first = messages.getFirst();
        if (first instanceof SystemMessage) {
            result.add(first);
        }
        var start = Math.max(first instanceof SystemMessage ? 1 : 0, messages.size() - budget.lastKeep());
        result.add(new SystemMessage("以下早期上下文已因预算限制被省略；如需要细节，请结合最近消息继续推理。"));
        for (int i = start; i < messages.size(); i++) {
            result.add(messages.get(i));
        }
        actions.add(ContextCompressionAction.DROP_OLD_HISTORY);
        return result;
    }

    private List<Message> summarizeHistory(
            List<Message> messages,
            String mainModelId,
            ContextBudget budget,
            LinkedHashSet<ContextCompressionAction> actions) {
        var config = settingsProvider.current();
        var summaryModelId =
                config.summaryModelId() != null && !config.summaryModelId().isBlank()
                        ? config.summaryModelId()
                        : mainModelId;
        if (summaryModelId == null || summaryModelId.isBlank() || messages.size() <= budget.lastKeep()) {
            return messages;
        }

        var protectedStart = Math.max(0, messages.size() - budget.lastKeep());
        var toSummarize = messages.subList(0, protectedStart);
        var retained = messages.subList(protectedStart, messages.size());
        var summaryInput = renderMessages(toSummarize);
        var targetBudget = Math.max(512, budget.triggerTokens() / 4);
        var request =
                new SummaryRequest(
                        summaryModelId,
                        config.summarySystemPrompt(),
                        config.summaryUserPrompt(),
                        summaryInput,
                        targetBudget);
        try {
            var summary =
                    CompletableFuture.supplyAsync(() -> contextSummarizer.summarize(request))
                            .get(config.summaryTimeoutMs(), TimeUnit.MILLISECONDS);
            if (summary.content() == null || summary.content().isBlank()) {
                return messages;
            }
            var result = new ArrayList<Message>();
            result.add(new SystemMessage("历史上下文压缩摘要：\n" + summary.content()));
            result.addAll(retained);
            actions.add(ContextCompressionAction.SUMMARIZE_HISTORY);
            return result;
        } catch (Exception e) {
            log.warn("上下文摘要失败，继续使用规则裁剪结果: modelId={}, error={}", summaryModelId, e.getMessage());
            return messages;
        }
    }

    private String preview(String text, int maxChars) {
        var head = text.substring(0, Math.min(text.length(), maxChars));
        return head + "\n\n[内容过长，已按规则裁剪，原始字符数=" + text.length() + "]";
    }

    private String renderMessages(List<Message> messages) {
        var builder = new StringBuilder();
        for (var message : messages) {
            builder.append(roleOf(message)).append(": ");
            builder.append(message.getText()).append("\n\n");
        }
        return builder.toString();
    }

    private String roleOf(Message message) {
        if (message instanceof SystemMessage) {
            return "system";
        }
        if (message instanceof AssistantMessage) {
            return "assistant";
        }
        return "user";
    }

    private Message copyWithText(Message source, String text) {
        if (source instanceof SystemMessage) {
            return new SystemMessage(text);
        }
        if (source instanceof AssistantMessage) {
            return new AssistantMessage(text);
        }
        return new UserMessage(text);
    }

    private void publishLog(
            ContextPreparationRequest request,
            ContextPreparationResult result,
            long startedAt,
            String reason) {
        var durationMs = System.currentTimeMillis() - startedAt;
        var event =
                new ContextCompressionLogEvent(
                        request.userId(),
                        request.modelId(),
                        result.budget().policy(),
                        result.budget().contextWindow(),
                        result.budget().inputBudget(),
                        result.budget().triggerTokens(),
                        result.tokenBefore(),
                        result.tokenAfter(),
                        request.messages() != null ? request.messages().size() : 0,
                        result.messages() != null ? result.messages().size() : 0,
                        result.actions(),
                        settingsProvider.current().summaryModelId(),
                        durationMs,
                        reason);
        log.info(
                "上下文预处理完成: userId={}, modelId={}, policy={}, tokens={}->{}, messages={}->{}, actions={}, reason={}, durationMs={}",
                event.userId(),
                event.modelId(),
                event.policy(),
                event.tokenBefore(),
                event.tokenAfter(),
                event.messageCountBefore(),
                event.messageCountAfter(),
                event.actions(),
                event.reason(),
                event.durationMs());
        eventPublisher.publishEvent(event);
        if (!event.actions().contains(ContextCompressionAction.NONE)) {
            agentRunEventPublisher.publish(
                    AgentRunEventType.CONTEXT_COMPRESSION_COMPLETED,
                    "上下文已整理",
                    "已完成上下文压缩与裁剪",
                    java.util.Map.of(
                            "modelId", event.modelId() != null ? event.modelId() : "",
                            "tokenBefore", event.tokenBefore(),
                            "tokenAfter", event.tokenAfter(),
                            "messageCountBefore", event.messageCountBefore(),
                            "messageCountAfter", event.messageCountAfter(),
                            "actions", event.actions()));
        }
    }
}
