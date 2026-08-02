package com.xuejiai.aaf.module.document.api;

/** 文档子域的外部来源建档接口。 */
public interface DocumentSourceApi {

    SourceDocument register(SourceDocumentCommand command);

    record SourceDocumentCommand(
            Long sourceFileId,
            String title,
            String documentType,
            String sourceKey,
            String content,
            Long ownerId) {}

    record SourceDocument(Long id, Long sourceFileId, String sourceKey, Long ownerId) {}
}
