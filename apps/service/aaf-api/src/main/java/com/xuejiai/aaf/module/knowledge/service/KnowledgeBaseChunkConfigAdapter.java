package com.xuejiai.aaf.module.knowledge.service;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.knowledge.chunker.ChunkConfig;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.ChunkStrategy;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.KnowledgeBaseConfigProvider;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeBaseRepository;

import lombok.RequiredArgsConstructor;

/** 把业务知识库的分块设置提供给 framework 管道。 */
@Component
@RequiredArgsConstructor
public class KnowledgeBaseChunkConfigAdapter implements KnowledgeBaseConfigProvider {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    @Override
    public Optional<ChunkConfig> getChunkConfig(Long knowledgeBaseId) {
        return knowledgeBaseRepository
                .findById(knowledgeBaseId)
                .filter(knowledgeBase -> hasText(knowledgeBase.getChunkStrategy()))
                .map(
                        knowledgeBase ->
                                new ChunkConfig(
                                        strategy(knowledgeBase.getChunkStrategy()),
                                        effectiveSize(knowledgeBase.getChunkSize()),
                                        effectiveOverlap(
                                                knowledgeBase.getChunkOverlap(),
                                                effectiveSize(knowledgeBase.getChunkSize()))));
    }

    private ChunkStrategy strategy(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "fixed" -> ChunkStrategy.FIXED_SIZE;
            case "recursive" -> ChunkStrategy.RECURSIVE_CHARACTER;
            case "semantic" -> ChunkStrategy.SEMANTIC_BOUNDARY;
            default -> throw new IllegalStateException("未知知识库分块策略: " + value);
        };
    }

    private int effectiveSize(Integer value) {
        return value == null ? 512 : value;
    }

    private int effectiveOverlap(Integer value, int chunkSize) {
        return value == null ? Math.min(64, chunkSize - 1) : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
