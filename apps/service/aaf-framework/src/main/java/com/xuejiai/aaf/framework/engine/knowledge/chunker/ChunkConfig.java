package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import java.util.Objects;

/** 分块配置：章节内的分块策略、块大小与相邻块重叠（字符）。 */
public record ChunkConfig(ChunkStrategy strategy, int chunkSize, int overlapSize) {

    public ChunkConfig {
        Objects.requireNonNull(strategy, "分块策略不能为空");
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("分块大小必须大于 0");
        }
        if (overlapSize < 0 || overlapSize >= chunkSize) {
            throw new IllegalArgumentException("分块重叠必须大于等于 0 且小于分块大小");
        }
    }
}
