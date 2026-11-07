package com.xuejiai.aaf.module.knowledge.vo;

import java.util.UUID;

/** 知识文档当前发布代际中的分块；内容只读，可启用/停用。 */
public record KnowledgeChunkVO(
        UUID id,
        int chunkIndex,
        String content,
        int tokenCount,
        String sectionPath,
        Integer pageNo,
        boolean enabled) {}
