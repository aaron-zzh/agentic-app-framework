package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 分块质量评估器
 */
@Component
public class ChunkQualityEvaluator {

    private static final double MIN_REASONABLE_LENGTH = 200.0;
    private static final double MAX_REASONABLE_LENGTH = 1500.0;

    /**
     * 评估分块质量
     */
    public ChunkQualityReport evaluate(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new ChunkQualityReport(0, 0, 0, 0, 0, 0);
        }

        var lengths = chunks.stream().mapToInt(c -> c.content().length()).toArray();
        int totalChunks = lengths.length;
        double avgLength = (double) java.util.Arrays.stream(lengths).sum() / totalChunks;
        int minLength = java.util.Arrays.stream(lengths).min().orElse(0);
        int maxLength = java.util.Arrays.stream(lengths).max().orElse(0);
        double avgTokenCount = chunks.stream().mapToInt(DocumentChunk::tokenCount).average().orElse(0);

        // 均匀度评分：基于变异系数（CV），CV 越小越均匀
        double variance = java.util.Arrays.stream(lengths)
                .mapToDouble(l -> Math.pow(l - avgLength, 2))
                .sum() / totalChunks;
        double cv = avgLength > 0 ? Math.sqrt(variance) / avgLength : 1.0;
        double uniformityScore = Math.max(0, 1.0 - cv);

        // 合理范围评分：平均长度在 200-1500 之间得满分，偏离越远越低
        double rangeScore;
        if (avgLength >= MIN_REASONABLE_LENGTH && avgLength <= MAX_REASONABLE_LENGTH) {
            rangeScore = 1.0;
        } else if (avgLength < MIN_REASONABLE_LENGTH) {
            rangeScore = Math.max(0, avgLength / MIN_REASONABLE_LENGTH);
        } else {
            rangeScore = Math.max(0, 1.0 - (avgLength - MAX_REASONABLE_LENGTH) / MAX_REASONABLE_LENGTH);
        }

        // 综合评分：均匀度 60% + 合理范围 40%
        double qualityScore = Math.min(1.0, uniformityScore * 0.6 + rangeScore * 0.4);

        return new ChunkQualityReport(avgLength, minLength, maxLength, totalChunks, avgTokenCount, qualityScore);
    }
}
