package com.xuejiai.aaf.framework.engine.knowledge.rag;

import java.util.Set;
import java.util.UUID;

/** RAG 评估用例。 */
public record EvalCase(String question, String expectedAnswer, Set<UUID> knowledgeBaseIds) {
    public EvalCase {
        knowledgeBaseIds = knowledgeBaseIds == null ? Set.of() : Set.copyOf(knowledgeBaseIds);
    }
}
