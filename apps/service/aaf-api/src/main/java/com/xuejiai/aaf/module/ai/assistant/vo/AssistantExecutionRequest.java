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
        @NotNull @Valid ExecutionOptions execution,
        @NotNull @Valid Input input,
        @Valid RoleSelection role,
        @Valid SkillSelection skill,
        @NotNull @Valid KnowledgeOptions knowledge,
        @NotNull @Valid ModelSelection model,
        @NotNull @Valid MemoryOptions memory,
        @NotNull @Valid OutputOptions output) {

    /** 客户端执行偏好；固定 Route 的 Role/Skill 可由客户端声明，服务端必须校验 Assistant 归属。 */
    public record ExecutionOptions(
            @NotNull InteractionMode interactionMode,
            @NotNull RouteConstraint routeConstraint,
            @NotNull ClarificationPolicy clarificationPolicy,
            @NotNull ActionAuthorizationPolicy actionAuthorizationPolicy,
            @NotNull ArtifactPersistence artifactPersistence) {}

    /** 固定 Route 的 Role 选择；仅作为待校验输入。 */
    public record RoleSelection(String key) {}

    /** 可选 Assistant 目标；省略时解析当前用户的唯一默认 Assistant。 */
    public record AssistantTarget(String id) {}

    /** 用户输入；变量和附件作为不可信任务数据进入服务端上下文。 */
    public record Input(
            @NotBlank String text,
            @NotNull Map<@NotBlank String, Object> variables,
            @NotNull List<@NotNull @Valid Attachment> attachments) {}

    /** 可选 Skill 收窄条件。 */
    public record SkillSelection(String code) {}

    /** 单次任务知识选项；DEFAULT 仅解析 Assistant 已授权绑定，绝不扩大到全局公共库。 */
    public record KnowledgeOptions(
            @NotNull KnowledgeMode mode,
            @NotNull Set<UUID> knowledgeBaseIds,
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

    public enum InteractionMode {
        TASK,
        CONVERSATIONAL
    }

    public enum RouteConstraint {
        FIXED,
        AUTO
    }

    public enum ClarificationPolicy {
        MINIMAL,
        FAIL_ON_BLOCKER,
        INTERACTIVE
    }

    public enum ActionAuthorizationPolicy {
        REQUEST_ON_DEMAND,
        PREAUTHORIZED_ONLY,
        DENY_AUTHORIZED_ACTIONS
    }

    public enum ArtifactPersistence {
        AUTO_SAVE_DRAFT,
        RETURN_ONLY
    }

    public enum KnowledgeMode {
        DEFAULT,
        EXPLICIT,
        DISABLED
    }

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
