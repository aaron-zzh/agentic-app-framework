package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 根据文档类型自动选择分块策略 */
@Component
public class AutoChunkStrategySelector {

    @Autowired(required = false)
    private KnowledgeBaseConfigProvider configProvider;

    /** 根据文件类型自动选择分块策略，知识库配置优先 */
    public ChunkConfig selectStrategy(String fileType, Long knowledgeBaseId) {
        // 知识库自定义配置优先
        if (knowledgeBaseId != null && configProvider != null) {
            var custom = configProvider.getChunkConfig(knowledgeBaseId);
            if (custom.isPresent()) {
                return custom.get();
            }
        }
        return selectByFileType(fileType);
    }

    private ChunkConfig selectByFileType(String fileType) {
        var type = fileType == null ? "" : fileType.toLowerCase().replaceFirst("^\\.", "");
        return switch (type) {
            case "pdf" -> new ChunkConfig(ChunkStrategy.RECURSIVE_CHARACTER, 1024, 128);
            case "md", "markdown" ->
                    new ChunkConfig(
                            ChunkStrategy.RECURSIVE_CHARACTER,
                            800,
                            100,
                            List.of("\n## ", "\n### ", "\n\n", "\n"));
            case "html", "htm" -> new ChunkConfig(ChunkStrategy.RECURSIVE_CHARACTER, 800, 100);
            case "docx" -> new ChunkConfig(ChunkStrategy.FIXED_SIZE, 512, 64);
            default -> new ChunkConfig(ChunkStrategy.FIXED_SIZE);
        };
    }
}
