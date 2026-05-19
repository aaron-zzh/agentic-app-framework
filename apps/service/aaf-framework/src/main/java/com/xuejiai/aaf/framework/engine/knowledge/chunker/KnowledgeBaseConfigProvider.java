package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import java.util.Optional;

/**
 * 知识库分块配置提供者（由业务层实现，框架层可选注入）
 */
public interface KnowledgeBaseConfigProvider {

    /**
     * 根据知识库 ID 获取自定义分块配置
     */
    Optional<ChunkConfig> getChunkConfig(Long knowledgeBaseId);
}
