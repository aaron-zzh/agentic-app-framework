package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.AssistantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;

/** 本次任务实际使用的规则、资料、知识和记忆引用，不复制源正文。 */
public record EffectiveContextManifest(
        TaskId taskId,
        AssistantId assistantId,
        long assistantRevision,
        String roleKey,
        List<SourceReference> sources,
        Instant createdAt) {

    public EffectiveContextManifest {
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(assistantId, "assistantId 不能为空");
        if (assistantRevision < 0) throw new IllegalArgumentException("assistantRevision 不能小于 0");
        if (roleKey == null || roleKey.isBlank())
            throw new IllegalArgumentException("roleKey 不能为空白");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources 不能为空"));
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
    }

    /** 对源的稳定、脱敏引用。summary 只能是摘要，不得放原始知识、记忆或凭证。 */
    public record SourceReference(
            SourceType type,
            String sourceKey,
            String version,
            String scope,
            String reason,
            String summary,
            boolean userManageable) {

        public SourceReference {
            Objects.requireNonNull(type, "source type 不能为空");
            sourceKey = requireText(sourceKey, "sourceKey");
            version = requireText(version, "version");
            scope = requireText(scope, "scope");
            reason = requireText(reason, "reason");
            summary = summary == null ? "" : summary.trim();
            if (summary.length() > 256) throw new IllegalArgumentException("上下文摘要长度不能超过 256");
        }

        private static String requireText(String value, String name) {
            Objects.requireNonNull(value, name + " 不能为空");
            if (value.isBlank()) throw new IllegalArgumentException(name + " 不能为空白");
            return value;
        }
    }

    public enum SourceType {
        RULE,
        SKILL,
        TASK_MATERIAL,
        KNOWLEDGE,
        MEMORY
    }
}
