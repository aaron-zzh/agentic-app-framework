package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/** 分块器工厂，根据策略返回对应实现 */
@Component
public class ChunkerFactory {

    private final Map<ChunkStrategy, DocumentChunker> chunkerMap;

    public ChunkerFactory(List<DocumentChunker> chunkers) {
        this.chunkerMap =
                chunkers.stream()
                        .collect(Collectors.toMap(DocumentChunker::strategy, Function.identity()));
    }

    public DocumentChunker getChunker(ChunkStrategy strategy) {
        var chunker = chunkerMap.get(strategy);
        if (chunker == null) {
            throw new IllegalArgumentException("不支持的分块策略: " + strategy);
        }
        return chunker;
    }
}
