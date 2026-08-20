package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;
import com.xuejiai.aaf.framework.intelligent.assistant.model.ExecutionIntent;
import com.xuejiai.aaf.framework.intelligent.assistant.model.InvocationPolicy;

/** Assistant 单次调用选项；执行意图会冻结到 ExecutionProfileSnapshot 供恢复复用。 */
public record AssistantInvocation(
        AssistantCommand command,
        String requestedSkillKey,
        MemoryMode memoryMode,
        InvocationPolicy invocationPolicy,
        List<AgentMessage.Attachment> userAttachments,
        ExecutionIntent executionIntent) {

    public AssistantInvocation {
        Objects.requireNonNull(command, "command 不能为空");
        requestedSkillKey = normalize(requestedSkillKey);
        Objects.requireNonNull(memoryMode, "memoryMode 不能为空");
        Objects.requireNonNull(invocationPolicy, "invocationPolicy 不能为空");
        userAttachments =
                List.copyOf(Objects.requireNonNull(userAttachments, "userAttachments 不能为空"));
        Objects.requireNonNull(executionIntent, "executionIntent 不能为空");
    }

    public static AssistantInvocation of(AssistantCommand command) {
        var profile = command.invocationProfile();
        return new AssistantInvocation(
                command,
                profile.requestedSkillKey(),
                profile.memoryMode(),
                profile.invocationPolicy(),
                profile.userAttachments(),
                profile.executionIntent());
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
