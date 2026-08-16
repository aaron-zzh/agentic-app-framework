package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;

/** Assistant 单次调用的非持久执行选项；恢复快照仍只保存 {@link AssistantCommand}。 */
public record AssistantInvocation(
        AssistantCommand command,
        String requestedSkillKey,
        MemoryMode memoryMode,
        List<AgentMessage> supplementalMessages,
        List<AgentMessage.Attachment> userAttachments) {

    public AssistantInvocation {
        Objects.requireNonNull(command, "command 不能为空");
        requestedSkillKey = normalize(requestedSkillKey);
        Objects.requireNonNull(memoryMode, "memoryMode 不能为空");
        supplementalMessages =
                List.copyOf(
                        Objects.requireNonNull(supplementalMessages, "supplementalMessages 不能为空"));
        userAttachments =
                List.copyOf(Objects.requireNonNull(userAttachments, "userAttachments 不能为空"));
        if (supplementalMessages.stream()
                .anyMatch(message -> message.role() != AgentMessage.Role.SYSTEM)) {
            throw new IllegalArgumentException("supplementalMessages 仅允许 SYSTEM 消息");
        }
    }

    public AssistantInvocation(
            AssistantCommand command,
            String requestedSkillKey,
            MemoryMode memoryMode,
            List<AgentMessage> supplementalMessages) {
        this(command, requestedSkillKey, memoryMode, supplementalMessages, List.of());
    }

    public static AssistantInvocation of(AssistantCommand command) {
        return new AssistantInvocation(command, null, MemoryMode.DEFAULT, List.of(), List.of());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 本次调用的记忆读写策略。 */
    public enum MemoryMode {
        DEFAULT,
        DISABLED
    }
}
