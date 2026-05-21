package com.xuejiai.aaf.framework.engine.knowledge.rag;

/** RAG 评估用例 */
public record EvalCase(String question, String expectedAnswer, Long knowledgeBaseId) {}
