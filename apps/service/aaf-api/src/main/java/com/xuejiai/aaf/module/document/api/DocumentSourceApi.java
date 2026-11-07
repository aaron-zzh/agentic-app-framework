package com.xuejiai.aaf.module.document.api;

import java.util.Optional;

import org.springframework.web.multipart.MultipartFile;

/** 文档子域的外部来源建档接口。 */
public interface DocumentSourceApi {

    SourceDocument register(SourceDocumentCommand command);

    /** 是否支持 {@link #importFile} 导入的文件类型（md/markdown/txt/pdf）。 */
    boolean supportsFileImport(String filename);

    /** 将上传文件导入当前用户文档库：保留原文件并生成正文 v1；文本类可编辑，PDF 为只读提取文本。 */
    SourceDocument importFile(MultipartFile file, Long ownerId);

    /** 按来源键查找当前组织内指定 owner 的有效文档。 */
    Optional<SourceDocument> findBySourceKey(String sourceKey, Long ownerId);

    record SourceDocumentCommand(
            Long sourceFileId,
            String title,
            String documentType,
            String sourceKey,
            String content,
            Long ownerId) {}

    record SourceDocument(
            Long id,
            Long documentVersionId,
            String versionChecksum,
            Long sourceFileId,
            String sourceKey,
            Long ownerId) {}
}
