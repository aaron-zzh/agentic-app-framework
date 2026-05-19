package com.xuejiai.aaf.framework.engine.knowledge.rag;

/**
 * RAG 评估报告
 */
public record RagEvaluationReport(
        int totalCases,
        double avgConfidence,
        long avgLatencyMs,
        double passRate
) {}
