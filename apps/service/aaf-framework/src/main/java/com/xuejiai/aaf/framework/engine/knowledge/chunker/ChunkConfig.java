package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import java.util.List;

/**
 * 分块配置
 */
public record ChunkConfig(
        ChunkStrategy strategy,
        int chunkSize,
        int overlapSize,
        List<String> separators
) {
    public ChunkConfig(ChunkStrategy strategy) {
        this(strategy, 512, 64, List.of("\n\n", "\n", "。", " "));
    }

    public ChunkConfig(ChunkStrategy strategy, int chunkSize, int overlapSize) {
        this(strategy, chunkSize, overlapSize, List.of("\n\n", "\n", "。", " "));
    }
}
