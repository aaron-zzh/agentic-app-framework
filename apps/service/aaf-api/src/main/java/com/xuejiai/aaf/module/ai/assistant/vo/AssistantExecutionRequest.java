package com.xuejiai.aaf.module.ai.assistant.vo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 通用 Assistant 无会话执行请求。 */
public record AssistantExecutionRequest(
        @Valid AssistantTarget assistant,
        @NotNull @Valid Input input,
        @Valid SkillSelection skill,
        @NotNull @Valid KnowledgeOptions knowledge,
        @NotNull @Valid ModelSelection model,
        @NotNull @Valid MemoryOptions memory,
        @NotNull @Valid OutputOptions output) {

    /** 可选 Assistant 目标；省略时解析当前用户的唯一默认 Assistant。 */
    public record AssistantTarget(String id) {}

    /** 用户输入；变量和附件作为不可信任务数据进入服务端上下文。 */
    public record Input(
            @NotBlank String text,
            @NotNull Map<@NotBlank String, Object> variables,
            @NotNull List<@NotNull @Valid Attachment> attachments) {}

    /** 可选 Skill 收窄条件。 */
    public record SkillSelection(String code) {}

    /** 单次任务知识选项。 */
    public record KnowledgeOptions(
            @NotNull Set<UUID> knowledgeBaseIds,
            @NotNull Boolean includePublic,
            @NotNull Integer topK,
            @NotNull Double similarityThreshold) {}

    /** 单次任务模型选择。 */
    public record ModelSelection(@NotNull ModelMode mode, String modelId) {}

    /** 单次任务记忆策略。 */
    public record MemoryOptions(@NotNull MemoryMode mode) {}

    /** 输出提示与格式约束；maxCharLen 仅作为模型文案篇幅提示，JSON 格式仍严格验证。 */
    public record OutputOptions(Integer maxCharLen, AssistantOutputLocale locale, String format) {}

    /** 补充附件；TEXT 使用 content，IMAGE 使用 resourceId。 */
    public record Attachment(
            @NotNull AttachmentType type, String name, String content, String resourceId) {}

    public enum ModelMode {
        AUTO,
        EXPLICIT
    }

    public enum AttachmentType {
        TEXT,
        IMAGE
    }

    public enum MemoryMode {
        DEFAULT,
        DISABLED
    }
}
