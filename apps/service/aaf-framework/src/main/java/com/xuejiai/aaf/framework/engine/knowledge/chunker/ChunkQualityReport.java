package com.xuejiai.aaf.framework.engine.knowledge.chunker;

/** 分块质量评估报告 */
public record ChunkQualityReport(
        double avgLength,
        int minLength,
        int maxLength,
        int totalChunks,
        double avgTokenCount,
        double qualityScore) {}
