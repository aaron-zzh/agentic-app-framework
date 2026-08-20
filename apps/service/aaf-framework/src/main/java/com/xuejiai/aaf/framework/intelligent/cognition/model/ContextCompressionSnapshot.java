package com.xuejiai.aaf.framework.intelligent.cognition.model;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;

/** 单次 Harness 调用冻结的上下文压缩策略、证据和最终消息。 */
public record ContextCompressionSnapshot(
        Policy policy,
        List<String> triggerReasons,
        int originalEstimatedTokens,
        int finalEstimatedTokens,
        String inputSha256,
        String outputSha256,
        String summaryPromptName,
        int summaryPromptVersion,
        String summaryPromptSha256,
        String summaryModelId,
        boolean aiSummaryUsed,
        List<AgentMessage> finalMessages) {

    public ContextCompressionSnapshot {
        Objects.requireNonNull(policy, "policy 不能为空");
        triggerReasons = List.copyOf(Objects.requireNonNull(triggerReasons, "triggerReasons 不能为空"));
        if (originalEstimatedTokens < 0 || finalEstimatedTokens < 0) {
            throw new IllegalArgumentException("Token 估算不能小于 0");
        }
        inputSha256 = requireSha256(inputSha256, "inputSha256");
        outputSha256 = requireSha256(outputSha256, "outputSha256");
        summaryPromptName = requireText(summaryPromptName, "summaryPromptName");
        if (summaryPromptVersion < 1) {
            throw new IllegalArgumentException("summaryPromptVersion 必须大于 0");
        }
        summaryPromptSha256 = requireSha256(summaryPromptSha256, "summaryPromptSha256");
        summaryModelId = requireText(summaryModelId, "summaryModelId");
        finalMessages = List.copyOf(Objects.requireNonNull(finalMessages, "finalMessages 不能为空"));
        if (finalMessages.isEmpty()) {
            throw new IllegalArgumentException("finalMessages 不能为空");
        }
    }

    /** L3 选定并冻结的程序化与 AI 摘要策略。 */
    public record Policy(
            String version,
            boolean enabled,
            String name,
            int contextWindow,
            int reservedOutputTokens,
            int fixedPromptBudget,
            double triggerRatio,
            int lastKeep,
            int messageThreshold,
            int largeInputCharThreshold,
            int rulePreviewChars,
            boolean summaryEnabled,
            int summaryMaxChars,
            long summaryTimeoutMs) {

        public Policy {
            version = requireText(version, "version");
            name = requireText(name, "name");
            if (contextWindow <= 0
                    || reservedOutputTokens < 0
                    || fixedPromptBudget < 0
                    || lastKeep < 1
                    || messageThreshold < 1
                    || largeInputCharThreshold < 1
                    || rulePreviewChars < 1
                    || summaryMaxChars < 1
                    || summaryTimeoutMs < 1) {
                throw new IllegalArgumentException("上下文压缩策略数值非法");
            }
            if (triggerRatio <= 0 || triggerRatio > 1) {
                throw new IllegalArgumentException("triggerRatio 必须位于 (0, 1]");
            }
        }
    }

    private static String requireSha256(String value, String field) {
        value = requireText(value, field);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " 必须是 64 位小写十六进制");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }
}
