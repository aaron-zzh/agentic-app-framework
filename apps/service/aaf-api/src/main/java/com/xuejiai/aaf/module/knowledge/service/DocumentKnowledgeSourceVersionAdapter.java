package com.xuejiai.aaf.module.knowledge.service;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

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

    private static final Pattern LIBRARY_SOURCE_KEY = Pattern.compile("doc:(\\d+):v(\\d+)");

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
        var pinnedVersionId = pinnedVersionId(knowledgeDocument.getSourceKey(), sourceDocumentId);
        if (pinnedVersionId != null) {
            var pinned =
                    documentVersions
                            .findExact(sourceDocumentId, pinnedVersionId)
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "知识来源固定的文档版本不存在: " + pinnedVersionId));
            return new SourceVersion(pinned.id(), pinned.checksum());
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

    /**
     * 文档库导入的来源键 {@code doc:{文档ID}:v{版本ID}} 已固定精确版本，直接复用该版本，不向用户文档追加解析文本版本。
     *
     * @return 来源键指向本来源文档时返回版本 ID，否则 {@code null}
     */
    private static Long pinnedVersionId(String sourceKey, Long sourceDocumentId) {
        if (sourceKey == null) {
            return null;
        }
        var matcher = LIBRARY_SOURCE_KEY.matcher(sourceKey);
        if (!matcher.matches() || !sourceDocumentId.toString().equals(matcher.group(1))) {
            return null;
        }
        return Long.valueOf(matcher.group(2));
    }

    private String normalize(String value) {
        return Objects.requireNonNullElse(value, "").replace("\r\n", "\n").replace('\r', '\n');
    }
}
