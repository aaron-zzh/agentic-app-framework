package com.xuejiai.aaf.framework.intelligent.cognition.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;
import com.xuejiai.aaf.framework.intelligent.assistant.model.EffectiveContextManifest.SourceReference;

/** L1 根据授权、范围与预算冻结的最小可用上下文。 */
public record ControlledContextSnapshot(
        List<AgentMessage> messages,
        List<SourceReference> references,
        String digest,
        Instant frozenAt) {

    public ControlledContextSnapshot {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages 不能为空"));
        references = List.copyOf(Objects.requireNonNull(references, "references 不能为空"));
        if (messages.stream()
                .anyMatch(
                        message ->
                                message.role() == AgentMessage.Role.SYSTEM
                                        || message.role() == AgentMessage.Role.REASONING)) {
            throw new IllegalArgumentException("L1 动态 Context 禁止使用 SYSTEM 或 REASONING 角色");
        }
        digest = Objects.requireNonNullElse(digest, "").trim();
        Objects.requireNonNull(frozenAt, "frozenAt 不能为空");
    }

    public static ControlledContextSnapshot empty(Instant frozenAt) {
        return new ControlledContextSnapshot(List.of(), List.of(), "sources:0", frozenAt);
    }
}
