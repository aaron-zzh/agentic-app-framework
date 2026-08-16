package com.xuejiai.aaf.module.ai.assistant.vo;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 通用 Assistant 无会话执行请求。 */
public record AssistantExecutionRequest(
        @NotNull Long assistantVersion,
        @NotBlank String input,
        String skillKey,
        @NotNull @Valid KnowledgeOptions knowledge,
        @NotNull @Valid ModelSelection model,
        @NotNull List<@NotNull @Valid Material> materials,
        @NotNull MemoryMode memoryMode) {

    /** 单次任务知识选项。 */
    public record KnowledgeOptions(
            @NotNull Set<UUID> knowledgeBaseIds,
            @NotNull Boolean includePublic,
            @NotNull Integer topK,
            @NotNull Double similarityThreshold) {}

    /** 单次任务模型选择。 */
    public record ModelSelection(@NotNull ModelMode mode, String modelId) {}

    /** 补充材料；TEXT 使用 content，IMAGE 使用 resourceId。 */
    public record Material(
            @NotNull MaterialType type, String name, String content, String resourceId) {}

    public enum ModelMode {
        AUTO,
        EXPLICIT
    }

    public enum MaterialType {
        TEXT,
        IMAGE
    }

    public enum MemoryMode {
        DEFAULT,
        DISABLED
    }
}
