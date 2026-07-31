package com.xuejiai.aaf.framework.engine.knowledge.pipeline;

/** 知识管道文档状态输出端口；具体业务表持久化由上层模块实现。 */
public interface KnowledgeDocumentStatusPort {

    void updateStatus(Long documentId, int status, int chunkCount, String errorMessage);
}
