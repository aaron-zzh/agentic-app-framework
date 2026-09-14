package com.xuejiai.aaf.module.knowledge.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.knowledge.pipeline.KnowledgeSourceVersionPort;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi.AppendDocumentVersionCommand;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;

import lombok.RequiredArgsConstructor;

/** 将知识导入解析出的规范文本冻结为业务文档的不可变版本。 */
@Component
@RequiredArgsConstructor
public class DocumentKnowledgeSourceVersionAdapter implements KnowledgeSourceVersionPort {

    private final KnowledgeDocumentRepository knowledgeDocuments;
    private final DocumentVersionApi documentVersions;

    @Override
    public SourceVersion freeze(
            Long knowledgeDocumentId, String title, String content, String contentHash) {
        var knowledgeDocument =
                knowledgeDocuments
                        .findById(knowledgeDocumentId)
                        .orElseThrow(
                                () -> new IllegalStateException("知识来源不存在: " + knowledgeDocumentId));
        var sourceDocumentId = knowledgeDocument.getSourceDocumentId();
        if (sourceDocumentId == null) {
            throw new IllegalStateException("知识来源缺少业务文档身份: " + knowledgeDocumentId);
        }
        var current = documentVersions.findCurrent(List.of(sourceDocumentId)).get(sourceDocumentId);
        if (current != null
                && Objects.equals(current.title(), title)
                && Objects.equals(normalize(current.content()), normalize(content))) {
            return new SourceVersion(current.id(), current.checksum());
        }
        var created =
                documentVersions.append(
                        new AppendDocumentVersionCommand(
                                sourceDocumentId,
                                current == null ? null : current.id(),
                                title,
                                content,
                                "PLAIN_TEXT",
                                "IMPORT",
                                knowledgeDocument.getSourceKey() == null
                                        ? "knowledge-document:" + knowledgeDocumentId
                                        : knowledgeDocument.getSourceKey(),
                                "知识来源解析 " + contentHash,
                                knowledgeDocument.getOwnerId(),
                                knowledgeDocument.getOrgId(),
                                knowledgeDocument.getWorkspaceId()));
        return new SourceVersion(created.id(), created.checksum());
    }

    private String normalize(String value) {
        return Objects.requireNonNullElse(value, "").replace("\r\n", "\n").replace('\r', '\n');
    }
}
