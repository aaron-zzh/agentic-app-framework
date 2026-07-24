package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Objects;
import java.util.Set;

/** Assistant 如何从 Cognition 回忆与沉淀的纯配置。 */
public record MemoryStrategy(
        Mode mode, Set<String> recallScopes, Set<String> writeScopes, boolean longTermEnabled) {

    public MemoryStrategy {
        Objects.requireNonNull(mode, "MemoryStrategy mode 不能为空");
        recallScopes = Set.copyOf(Objects.requireNonNull(recallScopes, "recallScopes 不能为空"));
        writeScopes = Set.copyOf(Objects.requireNonNull(writeScopes, "writeScopes 不能为空"));
        recallScopes.forEach(MemoryStrategy::requireScope);
        writeScopes.forEach(MemoryStrategy::requireScope);
        if (!longTermEnabled && !writeScopes.isEmpty()) {
            throw new IllegalArgumentException("未启用长期记忆时 writeScopes 必须为空");
        }
    }

    public static MemoryStrategy hybridDefault() {
        return new MemoryStrategy(
                Mode.HYBRID, Set.of("PERSONAL", "KNOWLEDGE"), Set.of("PERSONAL"), true);
    }

    public static MemoryStrategy knowledgeOnly() {
        return new MemoryStrategy(Mode.KNOWLEDGE_ONLY, Set.of("KNOWLEDGE"), Set.of(), false);
    }

    private static void requireScope(String scope) {
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("记忆作用域不能为空白");
        }
    }

    public enum Mode {
        MEMORY_ONLY,
        KNOWLEDGE_ONLY,
        HYBRID,
        PROCEDURAL_FIRST,
        FULL
    }
}
