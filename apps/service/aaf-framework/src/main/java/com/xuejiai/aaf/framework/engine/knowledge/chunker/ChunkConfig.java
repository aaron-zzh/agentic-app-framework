package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import java.util.List;
import java.util.Objects;

/** 分块配置 */
public record ChunkConfig(
        ChunkStrategy strategy, int chunkSize, int overlapSize, List<String> separators) {

    public ChunkConfig {
        Objects.requireNonNull(strategy, "分块策略不能为空");
        separators = separators == null ? List.of() : List.copyOf(separators);
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("分块大小必须大于 0");
        }
        if (overlapSize < 0 || overlapSize >= chunkSize) {
            throw new IllegalArgumentException("分块重叠必须大于等于 0 且小于分块大小");
        }
    }

    public ChunkConfig(ChunkStrategy strategy) {
        this(strategy, 512, 64, List.of("\n\n", "\n", "。", " "));
    }

    public ChunkConfig(ChunkStrategy strategy, int chunkSize, int overlapSize) {
        this(strategy, chunkSize, overlapSize, List.of("\n\n", "\n", "。", " "));
    }
}
