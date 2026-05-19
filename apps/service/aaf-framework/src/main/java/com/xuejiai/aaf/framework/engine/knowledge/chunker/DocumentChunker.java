package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import java.util.List;
import java.util.Map;

/**
 * 文档分块器接口
 */
public interface DocumentChunker {

    /** 当前实现对应的分块策略 */
    ChunkStrategy strategy();

    /** 将文本内容按配置分块 */
    List<DocumentChunk> chunk(String content, ChunkConfig config, Map<String, Object> baseMetadata);
}
