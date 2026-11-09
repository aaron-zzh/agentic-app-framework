package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 选择章节内的分块策略：知识库自定义配置优先，未配置（auto）时按段落与句末边界切分。
 *
 * <p>文档结构由导入器与 {@link SectionOutlineBuilder} 按文件类型解析，分块策略只决定章节内部如何切分，因此不再按文件类型区分。
 */
@Component
public class AutoChunkStrategySelector {

    /** 默认块大小（字符）。 */
    static final int DEFAULT_CHUNK_SIZE = 800;

    /** 默认相邻块重叠（字符），仅发生在同一章节内。 */
    static final int DEFAULT_OVERLAP_SIZE = 50;

    @Autowired(required = false)
    private KnowledgeBaseConfigProvider configProvider;

    public ChunkConfig selectStrategy(Long knowledgeBaseId) {
        if (knowledgeBaseId != null && configProvider != null) {
            var custom = configProvider.getChunkConfig(knowledgeBaseId);
            if (custom.isPresent()) {
                return custom.get();
            }
        }
        return new ChunkConfig(
                ChunkStrategy.SEMANTIC_BOUNDARY, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE);
    }
}
