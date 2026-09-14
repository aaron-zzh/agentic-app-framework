package com.xuejiai.aaf.framework.engine.knowledge.pipeline;

/** 知识入库冻结规范化来源正文的上层端口。 */
public interface KnowledgeSourceVersionPort {

    SourceVersion freeze(
            Long knowledgeDocumentId, String title, String content, String contentHash);

    record SourceVersion(Long documentVersionId, String checksum) {}
}
